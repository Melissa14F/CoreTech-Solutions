package co.coretech.portal.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/**
 * El ciclo de una fase contra PostgreSQL 15 (HU-11, HU-12, HU-13; RN-08, RN-13, RN-16, RN-17, RN-31): entregar y cerrar, aprobar,
 * las 2 rondas de ajuste y el intento de una tercera, lo nuevo que se cotiza, las correcciones (también sobre una fase aprobada,
 * donde el porcentaje baja y luego vuelve) y el proyecto completo hasta la garantía y su cierre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class FasesTest extends BaseSeguimientoPrueba {

    private static String cambio(String tipo, String descripcion) {
        return "{\"tipo\":" + (tipo == null ? "null" : "\"" + tipo + "\"") + ",\"descripcion\":\"" + descripcion + "\"}";
    }

    private static String correccion(String origen, String titulo, String explicacion) {
        return "{\"origen\":\"" + origen + "\",\"titulo\":\"" + titulo + "\",\"explicacion\":\"" + explicacion + "\"}";
    }

    private static final String EXPLICACION = "El botón de pagar no mostraba el costo del envío antes de confirmar.";

    // ------------------------------------------------------------------------------------------ HU-11 y HU-13: el ciclo de una fase

    @Test
    void unaFaseConDosRondasDeAjusteYElIntentoDeUnaTerceraSeAprueba() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);
        UUID primero = pasos(codigo).get(0);

        // Con un paso pendiente no se entrega: el mensaje dice cuántos faltan
        mvc.perform(json(patch("/api/v1/pasos/" + primero).cookie(a.po()), hecho(NOTA))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(a.po()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Falta 1 paso por terminar"));
        hacerPasos(a, 1);

        // Entregar: queda en revisión con el plazo de 3 días hábiles y se avisa al cliente
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(a.po()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.plazoAprobacion").exists());
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");
        assertThat(avisos(a.proyecto().clienteId(), "FASE_ENTREGADA")).isEqualTo(1);
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.cliente()))
                .andExpect(jsonPath("$.fases[0].estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.fases[0].plazoAprobacion").exists());

        // Ajuste 1 de 2: la fase vuelve a en curso con un paso de tipo ajuste y su solicitud abierta
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()), cambio("AJUSTE", PEDIDO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.rondasUsadas").value(1))
                .andExpect(jsonPath("$.estadoFase").value("EN_CURSO"));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_CURSO");
        assertThat(jdbc.queryForObject("SELECT rondas_ajuste_usadas FROM proyecto_fases WHERE id = ?", Integer.class, fase1)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT titulo || '|' || origen || '|' || tipo::text FROM pasos WHERE proyecto_fase_id = ? AND tipo = 'AJUSTE'
                """, String.class, fase1)).isEqualTo("Ajuste 1 de 2|Lo pidió el cliente|AJUSTE");
        assertThat(avisos(a.proyecto().equipo().usuario(), "FASE_AJUSTE")).isEqualTo(1);

        // El ajuste cuenta como un paso por hacer; al terminarlo su solicitud queda resuelta y se vuelve a entregar
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(a.po()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores[0].mensaje").value("Falta 1 paso por terminar"));
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.cliente()))
                .andExpect(jsonPath("$.fases[0].rondasAjusteUsadas").value(1))
                .andExpect(jsonPath("$.fases[0].cambios[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.fases[0].pasos[2].tipo").value("AJUSTE"));
        hacerPasos(a, 1);
        assertThat(jdbc.queryForObject("SELECT estado::text FROM solicitudes_cambio WHERE proyecto_fase_id = ?", String.class, fase1))
                .isEqualTo("RESUELTO");
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(a.po())).andExpect(status().isOk());

        // Ajuste 2 de 2
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()), cambio("AJUSTE", PEDIDO + " Otra vez.")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rondasUsadas").value(2));
        hacerPasos(a, 1);
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(a.po())).andExpect(status().isOk());

        // Una tercera ronda: ya no hay; lo nuevo se cotiza
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()), cambio("AJUSTE", PEDIDO + " Y otra más.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].campo").value("tipo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ya usaste las 2 rondas; un nuevo cambio se cotiza"));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");

        // Aprobar: suma el porcentaje de la fase (100 / 3) y empieza la siguiente
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar").cookie(a.cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.avancePorcentaje").value(33.33))
                .andExpect(jsonPath("$.estadoProyecto").value("ACTIVO"));
        assertThat(estadoFase(codigo, 2)).isEqualTo("EN_CURSO");
        assertThat(jdbc.queryForObject("SELECT aprobacion_automatica FROM proyecto_fases WHERE id = ?", Boolean.class, fase1)).isFalse();
        assertThat(avisos(a.proyecto().equipo().usuario(), "FASE_APROBADA")).isEqualTo(1);
        assertThat(auditoriaDe(codigo, "Aprobó una fase")).isEqualTo(1);
    }

    @Test
    void algoNuevoQuedaPorCotizarYLaFaseSigueEsperandoSuRespuesta() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);
        entregarFase(a, 1);

        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()),
                        cambio("ALCANCE", "Quiero que la tienda también envíe una factura electrónica por cada venta.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ALCANCE"))
                .andExpect(jsonPath("$.estado").value("POR_COTIZAR"))
                .andExpect(jsonPath("$.estadoFase").value("EN_REVISION"))
                .andExpect(jsonPath("$.pasoId").doesNotExist());
        // No usa una ronda ni crea un paso: se cotiza con un cambio de plan (P21), y la fase se puede aprobar
        assertThat(jdbc.queryForObject("SELECT rondas_ajuste_usadas FROM proyecto_fases WHERE id = ?", Integer.class, fase1)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pasos WHERE proyecto_fase_id = ?", Integer.class, fase1)).isEqualTo(2);
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.cliente()))
                .andExpect(jsonPath("$.fases[0].cambios", hasSize(1)))
                .andExpect(jsonPath("$.fases[0].cambios[0].estado").value("POR_COTIZAR"));
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar").cookie(a.cliente())).andExpect(status().isOk());
    }

    @Test
    void unaFaseQueCierraElEquipoSumaSuPorcentajeYEmpiezaLaSiguiente() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        aprobarFase(a, 1);

        // La fase 2 la cierra el equipo: queda aprobada de una vez, sin pasar por el cliente
        hacerPasos(a, 2);
        mvc.perform(post("/api/v1/fases/" + faseId(codigo, 2) + "/entregar").cookie(a.po()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.avancePorcentaje").value(66.67))
                .andExpect(jsonPath("$.plazoAprobacion").doesNotExist());
        assertThat(estadoFase(codigo, 3)).isEqualTo("EN_CURSO");
        assertThat(jdbc.queryForObject("SELECT aprobada_por = ? FROM proyecto_fases WHERE id = ?", Boolean.class,
                a.proyecto().equipo().usuario(), faseId(codigo, 2))).isTrue();
        assertThat(avisos(a.proyecto().clienteId(), "FASE_CERRADA")).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------ HU-11: excepciones

    @Test
    void elClienteSoloRespondeUnaFaseEnRevisionYConDatosValidos() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);

        // Todavía en curso: no está esperando su aprobación
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar").cookie(a.cliente()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Esta fase no está esperando tu aprobación"));
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()), cambio("AJUSTE", PEDIDO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Esta fase no está esperando tu aprobación"));

        entregarFase(a, 1);
        // Datos inválidos: un error por campo
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()), cambio(null, "Corta")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].campo").value("tipo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige si es un ajuste o algo nuevo."))
                .andExpect(jsonPath("$.errores[1].campo").value("descripcion"));
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()), cambio("OTRO", PEDIDO)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("tipo"));
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()), cambio("AJUSTE", "x".repeat(801))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe máximo 800 caracteres."));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");
    }

    @Test
    void unClienteAjenoNoExisteYElProductOwnerNoRespondePorElCliente() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);
        entregarFase(a, 1);

        Cookie ajeno = sesionDe(crearUsuario("CLIENTE"));
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar").cookie(ajeno)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(ajeno), cambio("AJUSTE", PEDIDO))).andExpect(status().isNotFound());
        assertThat(auditoriaDe(codigo, "Intentó aprobar una fase de un proyecto ajeno")).isEqualTo(1);
        // El Product Owner y el administrador no son el cliente del proyecto: para ellos no existe
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar").cookie(a.po())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar").cookie(sesionDe(a.admin()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/fases/" + UUID.randomUUID() + "/aprobar").cookie(a.cliente())).andExpect(status().isNotFound());
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");
    }

    // ------------------------------------------------------------------------------------------ HU-13: excepciones al entregar

    @Test
    void soloElProductOwnerDelEquipoEntregaYUnaFaseConCorreccionesAbiertasNoSeEntrega() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);

        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(sesionDe(equipo().email()))).andExpect(status().isForbidden());
        assertThat(auditoriaDe(codigo, "Intentó entregar una fase de un proyecto de otro equipo")).isEqualTo(1);
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(a.cliente())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(sesionDe(a.admin()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar")).andExpect(status().isUnauthorized());

        // Una fase que no está en curso (la 2 todavía es pendiente) no se entrega
        mvc.perform(post("/api/v1/fases/" + faseId(codigo, 2) + "/entregar").cookie(a.po()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errores[0].mensaje").value("Esta fase no está en curso"));

        // Con todos los pasos hechos pero una corrección abierta en la misma fase
        hacerPasos(a, 1);
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/pasos").cookie(a.po()),
                correccion("La encontramos en pruebas", "Mostrar el costo del envío", EXPLICACION))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/fases/" + fase1 + "/entregar").cookie(a.po()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Termina las correcciones antes de entregar"));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_CURSO");
    }

    // ------------------------------------------------------------------------------------------ HU-12: correcciones

    @Test
    void unaCorreccionSobreUnaFaseAprobadaLaDejaDeSumarHastaTerminarlaYNoBloqueaLaFaseActual() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);
        aprobarFase(a, 1);
        assertThat(avance(codigo)).isEqualByComparingTo(new BigDecimal("33.33"));

        // Registrar: la fase 1 pasa a «en corrección» y el proyecto baja
        String respuesta = mvc.perform(json(post("/api/v1/fases/" + fase1 + "/pasos").cookie(a.po()),
                        correccion("Lo pidió el cliente", "Mostrar el costo del envío", EXPLICACION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoFase").value("EN_CORRECCION"))
                .andExpect(jsonPath("$.avancePorcentaje").value(0.0))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).contains("pasoId");
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_CORRECCION");
        assertThat(avance(codigo)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(avisos(a.proyecto().clienteId(), "CORRECCION_ABIERTA")).isEqualTo(1);
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.cliente()))
                .andExpect(jsonPath("$.fases[0].estado").value("EN_CORRECCION"))
                .andExpect(jsonPath("$.fases[0].pasos[2].tipo").value("CORRECCION"))
                .andExpect(jsonPath("$.fases[0].pasos[2].origen").value("Lo pidió el cliente"));

        // Las correcciones de una fase aprobada no bloquean la entrega de la fase actual (RN-16)
        hacerPasos(a, 2);
        mvc.perform(post("/api/v1/fases/" + faseId(codigo, 2) + "/entregar").cookie(a.po())).andExpect(status().isOk());
        assertThat(avance(codigo)).isEqualByComparingTo(new BigDecimal("33.33"));

        // Terminar la corrección: la fase vuelve a sumar (2 de 3 fases aprobadas)
        UUID paso = jdbc.queryForObject("SELECT id FROM pasos WHERE proyecto_fase_id = ? AND tipo = 'CORRECCION'", UUID.class, fase1);
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho("Ya se ve el costo del envío antes de pagar, con el total.")))
                .andExpect(status().isOk());
        assertThat(estadoFase(codigo, 1)).isEqualTo("APROBADA");
        assertThat(avance(codigo)).isEqualByComparingTo(new BigDecimal("66.67"));
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho("Ya se ve el costo del envío antes de pagar, con el total.")))
                .andExpect(status().isConflict());
    }

    @Test
    void lasCorreccionesValidanSuFormularioYSoloVanEnFasesEnCursoOAprobadas() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);

        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/pasos").cookie(a.po()), correccion("Porque sí", "Ab", "Corta")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores", hasSize(3)))
                .andExpect(jsonPath("$.errores[0].campo").value("origen"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige de dónde sale la corrección."))
                .andExpect(jsonPath("$.errores[1].campo").value("titulo"))
                .andExpect(jsonPath("$.errores[2].campo").value("explicacion"));
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/pasos").cookie(a.po()),
                correccion("Lo pidió el cliente", "Nombre", "x".repeat(401)))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe máximo 400 caracteres."));

        // Fase pendiente: todavía no empieza
        mvc.perform(json(post("/api/v1/fases/" + faseId(codigo, 2) + "/pasos").cookie(a.po()),
                correccion("Lo pidió el cliente", "Mostrar el costo del envío", EXPLICACION)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Solo se corrigen fases en curso o aprobadas"));

        // Fase en revisión: espera al cliente
        entregarFase(a, 1);
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/pasos").cookie(a.po()),
                correccion("Lo pidió el cliente", "Mostrar el costo del envío", EXPLICACION))).andExpect(status().isConflict());

        // Quien no es el Product Owner del equipo
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/pasos").cookie(sesionDe(equipo().email())),
                correccion("Lo pidió el cliente", "Mostrar el costo del envío", EXPLICACION))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/pasos").cookie(a.cliente()),
                correccion("Lo pidió el cliente", "Mostrar el costo del envío", EXPLICACION))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------ RN-13 y RN-31: hasta finalizar

    @Test
    void elProyectoCompletoLlegaAl100PorCientoQuedaEnGarantiaYSoloAdmiteCorrecciones() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        aprobarFase(a, 1);
        entregarFase(a, 2);
        hacerPasos(a, 3);

        mvc.perform(post("/api/v1/fases/" + faseId(codigo, 3) + "/entregar").cookie(a.po()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.avancePorcentaje").value(100.0))
                .andExpect(jsonPath("$.estadoProyecto").value("EN_GARANTIA"))
                .andExpect(jsonPath("$.garantiaHasta").value(hoy().plusDays(30).toString()));
        assertThat(estadoProyecto(codigo)).isEqualTo("EN_GARANTIA");
        assertThat(jdbc.queryForObject("SELECT garantia_hasta FROM proyectos WHERE codigo = ?", LocalDate.class, codigo))
                .isEqualTo(hoy().plusDays(30));
        assertThat(avisos(a.proyecto().clienteId(), "PROYECTO_EN_GARANTIA")).isEqualTo(1);
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.cliente()))
                .andExpect(jsonPath("$.estado").value("EN_GARANTIA"))
                .andExpect(jsonPath("$.garantiaHasta").value(hoy().plusDays(30).toString()))
                .andExpect(jsonPath("$.puedeRegistrar").value(false));
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.po())).andExpect(jsonPath("$.puedeRegistrar").value(true));

        // En garantía: una corrección se registra, pero la fase sigue aprobada y el avance sigue en 100 %
        UUID fase2 = faseId(codigo, 2);
        mvc.perform(json(post("/api/v1/fases/" + fase2 + "/pasos").cookie(a.po()),
                correccion("La encontramos en pruebas", "Corregir el total del carrito", EXPLICACION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoFase").value("APROBADA"))
                .andExpect(jsonPath("$.avancePorcentaje").value(100.0));
        assertThat(estadoFase(codigo, 2)).isEqualTo("APROBADA");
        assertThat(avance(codigo)).isEqualByComparingTo(new BigDecimal("100"));

        // Terminarla en garantía solo la deja terminada: el proyecto sigue en garantía hasta que se cumplan los 30 días
        UUID paso = jdbc.queryForObject("SELECT id FROM pasos WHERE proyecto_fase_id = ? AND tipo = 'CORRECCION'", UUID.class, fase2);
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho("El total del carrito ahora suma el envío y los descuentos.")))
                .andExpect(status().isOk());
        assertThat(estadoProyecto(codigo)).isEqualTo("EN_GARANTIA");
        assertThat(estadoFase(codigo, 2)).isEqualTo("APROBADA");
    }

    @Test
    void conLaGarantiaCumplidaNoSeRegistranCorreccionesNuevasYLaUltimaAbiertaFinalizaElProyecto() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        aprobarFase(a, 1);
        entregarFase(a, 2);
        entregarFase(a, 3);
        assertThat(estadoProyecto(codigo)).isEqualTo("EN_GARANTIA");
        UUID fase3 = faseId(codigo, 3);

        // Una corrección abierta dentro de la garantía
        mvc.perform(json(post("/api/v1/fases/" + fase3 + "/pasos").cookie(a.po()),
                correccion("Lo pidió el cliente", "Corregir el total del carrito", EXPLICACION))).andExpect(status().isCreated());
        // Se cumplen los 30 días con la corrección abierta: sigue en garantía y no admite correcciones nuevas
        jdbc.update("UPDATE proyectos SET garantia_hasta = ? WHERE codigo = ?", hoy().minusDays(1), codigo);
        mvc.perform(json(post("/api/v1/fases/" + fase3 + "/pasos").cookie(a.po()),
                correccion("Lo pidió el cliente", "Otra corrección distinta", EXPLICACION)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La garantía ya terminó; solo se terminan las correcciones abiertas"));
        assertThat(estadoProyecto(codigo)).isEqualTo("EN_GARANTIA");

        // Al terminar la última corrección queda finalizado
        UUID paso = jdbc.queryForObject("SELECT id FROM pasos WHERE proyecto_fase_id = ? AND tipo = 'CORRECCION'", UUID.class, fase3);
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho("El total del carrito ahora suma el envío y los descuentos.")))
                .andExpect(status().isOk());
        assertThat(estadoProyecto(codigo)).isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject("SELECT finalizado_at IS NOT NULL FROM proyectos WHERE codigo = ?", Boolean.class, codigo)).isTrue();
        assertThat(avisos(a.proyecto().clienteId(), "PROYECTO_FINALIZADO")).isEqualTo(1);

        // Finalizado no admite registros
        mvc.perform(json(post("/api/v1/fases/" + fase3 + "/pasos").cookie(a.po()),
                correccion("Lo pidió el cliente", "Otra corrección distinta", EXPLICACION)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El proyecto no admite registros en su estado actual"));
    }

    @Test
    void enGarantiaSoloSeRegistranCorrecciones() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        aprobarFase(a, 1);
        entregarFase(a, 2);
        entregarFase(a, 3);
        // Un paso del plan pendiente (por ejemplo, agregado después) no se puede marcar en garantía
        UUID fase3 = faseId(codigo, 3);
        UUID paso = jdbc.queryForObject("""
                INSERT INTO pasos (proyecto_fase_id, orden, titulo) VALUES (?, 9, 'Paso agregado tarde') RETURNING id
                """, UUID.class, fase3);
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho(NOTA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("En garantía solo se registran correcciones"));
    }
}
