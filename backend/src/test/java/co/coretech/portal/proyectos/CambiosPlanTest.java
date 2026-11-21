package co.coretech.portal.proyectos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/**
 * HU-30, RN-30, D-32 y D-33 · Cambios de un plan aprobado: agregar o quitar una fase pendiente, cambiar la fecha, quitar un paso
 * pendiente y cotizar un cambio de alcance. Lo propone el Product Owner, lo autoriza el administrador y lo acepta el cliente;
 * mientras tanto el plan sigue igual.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class CambiosPlanTest extends BaseProyectosPrueba {

    private static final String MOTIVO = "El cliente necesita este cambio para poder cumplir con su lanzamiento.";
    private static final String NOTA = "Quedó listo y ya puedes verlo funcionando en tu cuenta de pruebas.";

    /** Un proyecto con el plan aprobado de tres fases de dos pasos: la 1 la aprueba el cliente y está en curso; la 2 y la 3, las cierra el equipo. */
    record Aprobado(String adminCorreo, Proyecto proyecto, Cookie po, Cookie cliente, Cookie admin) {
    }

    Aprobado aprobado() throws Exception {
        String adminCorreo = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(adminCorreo);
        Cookie po = sesionDe(p.equipo().email());
        Cookie cliente = sesionDe(p.clienteEmail());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/enviar").cookie(po), planJson())).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isOk());
        return new Aprobado(adminCorreo, p, po, cliente, sesionDe(adminCorreo));
    }

    static String planJson() {
        StringBuilder s = new StringBuilder("{\"fases\":[");
        for (int f = 1; f <= 3; f++) {
            s.append(f > 1 ? "," : "").append("{\"nombre\":\"Fase ").append(f).append("\",\"queEs\":\"Qué es\",\"apruebaCliente\":")
                    .append(f == 1).append(",\"pasos\":[{\"titulo\":\"Paso 1 de la fase ").append(f)
                    .append("\",\"explicacion\":\"Explicación\"},{\"titulo\":\"Paso 2 de la fase ").append(f).append("\"}]}");
        }
        return s.append("],\"valorCotizado\":7800000,\"fechaEstimadaEntrega\":\"").append(entregaActual())
                .append("\",\"condicionesCotizacion\":\"Incluye 2 rondas de ajustes en cada fase y 30 días de garantía.\"}").toString();
    }

    static LocalDate entregaActual() {
        return hoy().plusDays(60);
    }

    static LocalDate nuevaEntrega() {
        return hoy().plusDays(75);
    }

    private static String ruta(Aprobado a) {
        return "/api/v1/proyectos/" + a.proyecto().codigo() + "/cambios-plan";
    }

    private static String id(String respuesta) {
        return respuesta.replaceAll(".*?\"cambioId\":\"([^\"]+)\".*", "$1");
    }

    private static String cuerpoFase(String nombre, int posicion, boolean aprueba) {
        return "{\"tipo\":\"AGREGAR_FASE\",\"nombreFase\":\"" + nombre + "\",\"posicion\":" + posicion + ",\"apruebaCliente\":" + aprueba
                + ",\"pasos\":[{\"titulo\":\"Revisión final\",\"explicacion\":\"Se revisa todo\"},{\"titulo\":\"Entrega al cliente\"}],"
                + "\"motivo\":\"" + MOTIVO + "\",\"nuevaEntrega\":\"" + nuevaEntrega() + "\",\"valorAdicional\":1500000}";
    }

    private static String cuerpoSimple(String tipo, String campo, UUID valor) {
        return "{\"tipo\":\"" + tipo + "\",\"" + campo + "\":\"" + valor + "\",\"motivo\":\"" + MOTIVO + "\""
                + (tipo.equals("QUITAR_PASO") ? "" : ",\"nuevaEntrega\":\"" + nuevaEntrega() + "\",\"valorAdicional\":0") + "}";
    }

    private String proponer(Aprobado a, String cuerpo) throws Exception {
        return id(mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpo)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString());
    }

    private void autorizar(Aprobado a, String cambio, String estadoEsperado) throws Exception {
        mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/autorizar").cookie(a.admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(estadoEsperado));
    }

    private void aceptar(Aprobado a, String cambio) throws Exception {
        mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/aceptar").cookie(a.cliente())).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADO"));
    }

    private UUID fase(Aprobado a, int orden) {
        return jdbc.queryForObject("SELECT f.id FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id WHERE p.codigo = ? AND f.orden = ? AND f.estado <> 'QUITADA'",
                UUID.class, a.proyecto().codigo(), orden);
    }

    private UUID paso(Aprobado a, int faseOrden, int pasoOrden) {
        return jdbc.queryForObject("SELECT s.id FROM pasos s WHERE s.proyecto_fase_id = ? AND s.orden = ?", UUID.class, fase(a, faseOrden), pasoOrden);
    }

    private List<String> nombresDeFases(Aprobado a) {
        return jdbc.queryForList("SELECT f.nombre FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id WHERE p.codigo = ? AND f.estado <> 'QUITADA' ORDER BY f.orden",
                String.class, a.proyecto().codigo());
    }

    private LocalDate entrega(Aprobado a) {
        return jdbc.queryForObject("SELECT fecha_estimada_entrega FROM proyectos WHERE codigo = ?", LocalDate.class, a.proyecto().codigo());
    }

    private String estadoCambio(String cambio) {
        return jdbc.queryForObject("SELECT estado::text FROM cambios_plan WHERE id = ?::uuid", String.class, cambio);
    }

    private int auditoriaDe(Aprobado a, String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = ?", Integer.class, a.proyecto().codigo(), accion);
    }

    // =========================================================================================== Agregar una fase

    @Nested
    class AgregarFase {

        @Test
        void elCicloCompletoAgregaLaFaseYActualizaLaEntregaYElValorTotal() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoFase("Capacitación", 4, true))).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PROPUESTO")).andExpect(jsonPath("$.fasesAntes").value(3))
                    .andExpect(jsonPath("$.fasesDespues").value(4)).andExpect(jsonPath("$.avanceDespues").value(0));
            String cambio = jdbc.queryForObject("SELECT id::text FROM cambios_plan ORDER BY created_at DESC LIMIT 1", String.class);
            assertThat(avisos(idDe(a.adminCorreo()), "CAMBIO_PLAN_PROPUESTO")).isEqualTo(1);

            // Mientras no se acepte, el plan sigue igual y el cliente ni lo ve
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(status().isOk()).andExpect(jsonPath("$.cambios", hasSize(0)));
            mvc.perform(get("/api/v1/cambios-plan").cookie(a.admin())).andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id=='" + cambio + "')].antes.fases.length()").value(3))
                    .andExpect(jsonPath("$[?(@.id=='" + cambio + "')].despues.fases.length()").value(4))
                    .andExpect(jsonPath("$[?(@.id=='" + cambio + "')].despues.fases[3].nombre").value("Capacitación"))
                    .andExpect(jsonPath("$[?(@.id=='" + cambio + "')].despues.valorTotal").value(9300000));
            assertThat(nombresDeFases(a)).containsExactly("Fase 1", "Fase 2", "Fase 3");

            autorizar(a, cambio, "AUTORIZADO");
            assertThat(avisos(a.proyecto().clienteId(), "CAMBIO_PLAN_AUTORIZADO")).isEqualTo(1);
            assertThat(nombresDeFases(a)).hasSize(3);
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(jsonPath("$.cambios", hasSize(1)))
                    .andExpect(jsonPath("$.cambios[0].puedeAceptar").value(true)).andExpect(jsonPath("$.cambios[0].despues.fases.length()").value(4));

            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/aceptar").cookie(a.cliente())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.fases").value(4)).andExpect(jsonPath("$.avancePorcentaje").value(0))
                    .andExpect(jsonPath("$.valorTotal").value(9300000));
            assertThat(nombresDeFases(a)).containsExactly("Fase 1", "Fase 2", "Fase 3", "Capacitación");
            assertThat(entrega(a)).isEqualTo(nuevaEntrega());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pasos WHERE proyecto_fase_id = ?", Integer.class, fase(a, 4))).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT cambio_plan_id::text FROM proyecto_fases WHERE id = ?", String.class, fase(a, 4))).isEqualTo(cambio);
            assertThat(avisos(a.proyecto().equipo().usuario(), "CAMBIO_PLAN_ACEPTADO")).isEqualTo(1);
            mvc.perform(get(ruta(a)).cookie(a.po())).andExpect(jsonPath("$.valorAdicionalAceptado").value(1500000))
                    .andExpect(jsonPath("$.valorTotal").value(9300000)).andExpect(jsonPath("$.valorCotizado").value(7800000));
            assertThat(auditoriaDe(a, "Propuso un cambio de plan")).isEqualTo(1);
            assertThat(auditoriaDe(a, "Aceptó un cambio de plan")).isEqualTo(1);
        }

        @Test
        void laFaseNuevaSeInsertaEnSuPosicionYLasQueVienenDespuesBajanUnLugar() throws Exception {
            Aprobado a = aprobado();
            String cambio = proponer(a, cuerpoFase("Pruebas con usuarios", 2, false));
            autorizar(a, cambio, "AUTORIZADO");
            aceptar(a, cambio);
            assertThat(nombresDeFases(a)).containsExactly("Fase 1", "Pruebas con usuarios", "Fase 2", "Fase 3");
            // La que ya está en curso no se mueve
            assertThat(jdbc.queryForObject("SELECT estado::text FROM proyecto_fases WHERE id = ?", String.class, fase(a, 1))).isEqualTo("EN_CURSO");
        }

        @Test
        void laPosicionVaDespuesDeLaFaseActualYElNombreNoSeRepite() throws Exception {
            Aprobado a = aprobado();
            String[][] casos = {
                    { "posicion", cuerpoFase("Una fase nueva", 1, true) },
                    { "posicion", cuerpoFase("Una fase nueva", 5, true) },
                    { "nombreFase", cuerpoFase("fase 2", 3, true) },
                    { "nombreFase", cuerpoFase("Ab", 3, true) },
                    { "pasos", cuerpoFase("Una fase nueva", 3, true).replaceAll("\"pasos\":\\[.*?\\],\"motivo\"", "\"pasos\":[],\"motivo\"") } };
            for (String[] caso : casos) {
                mvc.perform(json(post(ruta(a)).cookie(a.po()), caso[1])).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[?(@.campo=='" + caso[0] + "')]").exists());
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cambios_plan WHERE proyecto_id = ?::uuid", Integer.class, idProyecto(a))).isZero();
        }

        @Test
        void conOchoFasesNoSeAgregaOtra() throws Exception {
            Aprobado a = aprobado();
            for (int n = 4; n <= 8; n++) {
                String cambio = proponer(a, cuerpoFase("Fase extra " + n, n, false));
                autorizar(a, cambio, "AUTORIZADO");
                aceptar(a, cambio);
            }
            assertThat(nombresDeFases(a)).hasSize(8);
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoFase("Una más", 9, false))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[?(@.campo=='fases')].mensaje").value("El plan puede tener máximo 8 fases"));
        }
    }

    private String idProyecto(Aprobado a) {
        return jdbc.queryForObject("SELECT id::text FROM proyectos WHERE codigo = ?", String.class, a.proyecto().codigo());
    }

    // =========================================================================================== Quitar una fase

    @Nested
    class QuitarFase {

        @Test
        void unaFasePendienteSeQuitaConElRecorridoCompletoYNoSeBorra() throws Exception {
            Aprobado a = aprobado();
            UUID tercera = fase(a, 3);
            String cambio = proponer(a, cuerpoSimple("QUITAR_FASE", "faseId", tercera));
            autorizar(a, cambio, "AUTORIZADO");
            assertThat(nombresDeFases(a)).hasSize(3);
            aceptar(a, cambio);

            assertThat(nombresDeFases(a)).containsExactly("Fase 1", "Fase 2");
            assertThat(jdbc.queryForObject("SELECT estado::text FROM proyecto_fases WHERE id = ?", String.class, tercera)).isEqualTo("QUITADA");
            assertThat(entrega(a)).isEqualTo(nuevaEntrega());
        }

        @Test
        void sinEstarPendienteNoSeQuita_yElPlanNoQuedaConMenosDeDosFases() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 1)))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Solo se quita una fase pendiente; las fases aprobadas no cambian"));
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_FASE", "faseId", UUID.randomUUID()))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("faseId"));

            String cambio = proponer(a, cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 3)));
            autorizar(a, cambio, "AUTORIZADO");
            aceptar(a, cambio);
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 2)))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[?(@.campo=='fases')].mensaje").value("El plan necesita al menos 2 fases"));
        }

        @Test
        void siLaFaseEmpezoAntesDeLaRespuestaDelClienteNoSeAplicaNada() throws Exception {
            Aprobado a = aprobado();
            String cambio = proponer(a, cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 3)));
            autorizar(a, cambio, "AUTORIZADO");
            jdbc.update("UPDATE proyecto_fases SET estado = 'EN_CURSO'::estado_fase, iniciada_at = now() WHERE id = ?", fase(a, 3));

            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/aceptar").cookie(a.cliente())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value("El plan cambió desde que se propuso este cambio: pídele al equipo que lo proponga de nuevo"));
            assertThat(estadoCambio(cambio)).isEqualTo("AUTORIZADO");
            assertThat(nombresDeFases(a)).hasSize(3);
        }
    }

    // =========================================================================================== Cambiar la fecha

    @Nested
    class CambiarFecha {

        @Test
        void soloCambiaLaEntregaCuandoElClienteLoAcepta() throws Exception {
            Aprobado a = aprobado();
            String cambio = proponer(a, "{\"tipo\":\"CAMBIAR_FECHA\",\"motivo\":\"" + MOTIVO + "\",\"nuevaEntrega\":\"" + nuevaEntrega() + "\",\"valorAdicional\":0}");
            autorizar(a, cambio, "AUTORIZADO");
            assertThat(entrega(a)).isEqualTo(entregaActual());
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/aceptar").cookie(a.cliente())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.fechaEstimadaEntrega").value(nuevaEntrega().toString())).andExpect(jsonPath("$.fases").value(3));
            assertThat(entrega(a)).isEqualTo(nuevaEntrega());
            assertThat(nombresDeFases(a)).hasSize(3);
        }

        @Test
        void laMismaFechaOUnaInvalidaSeRechazan() throws Exception {
            Aprobado a = aprobado();
            String[] fechas = { entregaActual().toString(), "no es una fecha", hoy().plusDays(3).toString(), "2030-01-01" };
            for (String fecha : fechas) {
                mvc.perform(json(post(ruta(a)).cookie(a.po()), "{\"tipo\":\"CAMBIAR_FECHA\",\"motivo\":\"" + MOTIVO + "\",\"nuevaEntrega\":\"" + fecha
                        + "\",\"valorAdicional\":0}")).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[0].campo").value("nuevaEntrega"));
            }
            mvc.perform(json(post(ruta(a)).cookie(a.po()), "{\"tipo\":\"CAMBIAR_FECHA\",\"motivo\":\"" + MOTIVO + "\",\"nuevaEntrega\":\"" + entregaActual()
                    + "\",\"valorAdicional\":0}")).andExpect(jsonPath("$.errores[0].mensaje").value("Elige una fecha distinta a la entrega actual"));
        }

        @Test
        void elCambioDeFechaDelAdministradorNaceAutorizadoYElClienteLoAcepta() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(patch("/api/v1/proyectos/" + a.proyecto().codigo()).cookie(a.admin()),
                    "{\"fechaEstimadaEntrega\":\"" + nuevaEntrega() + "\",\"motivo\":\"El cliente amplió el alcance del proyecto\"}")).andExpect(status().isOk());
            assertThat(entrega(a)).isEqualTo(entregaActual());
            String cambio = jdbc.queryForObject("SELECT id::text FROM cambios_plan WHERE estado = 'AUTORIZADO' ORDER BY created_at DESC LIMIT 1", String.class);
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(jsonPath("$.cambios[0].tipo").value("CAMBIAR_FECHA"))
                    .andExpect(jsonPath("$.cambios[0].puedeAceptar").value(true));
            aceptar(a, cambio);
            assertThat(entrega(a)).isEqualTo(nuevaEntrega());
        }
    }

    // =========================================================================================== Quitar un paso

    @Nested
    class QuitarPaso {

        @Test
        void enUnaFaseQueCierraElEquipoSeAplicaAlAutorizarYSoloSeAvisaAlCliente() throws Exception {
            Aprobado a = aprobado();
            UUID paso = paso(a, 2, 1);
            String cambio = proponer(a, cuerpoSimple("QUITAR_PASO", "pasoId", paso));
            autorizar(a, cambio, "ACEPTADO");

            assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, paso)).isEqualTo("QUITADO");
            assertThat(avisos(a.proyecto().clienteId(), "CAMBIO_PLAN_APLICADO")).isEqualTo(1);
            assertThat(avisos(a.proyecto().clienteId(), "CAMBIO_PLAN_AUTORIZADO")).isZero();
            assertThat(entrega(a)).isEqualTo(entregaActual());
            // Las fases, el avance y la entrega no cambian
            assertThat(nombresDeFases(a)).hasSize(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pasos WHERE id = ?", Integer.class, paso)).isEqualTo(1);
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(jsonPath("$.cambios[0].estado").value("ACEPTADO"))
                    .andExpect(jsonPath("$.cambios[0].puedeAceptar").value(false));
        }

        @Test
        void enUnaFaseQueApruebaElClienteTambienLoAceptaElCliente() throws Exception {
            Aprobado a = aprobado();
            UUID paso = paso(a, 1, 2);
            String cambio = proponer(a, cuerpoSimple("QUITAR_PASO", "pasoId", paso));
            autorizar(a, cambio, "AUTORIZADO");
            assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, paso)).isEqualTo("PENDIENTE");
            aceptar(a, cambio);
            assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, paso)).isEqualTo("QUITADO");
        }

        @Test
        void unPasoQueYaEmpezoOHechoNoSeQuita_yLaFaseConservaAlMenosUno() throws Exception {
            Aprobado a = aprobado();
            UUID hecho = paso(a, 1, 1);
            mvc.perform(json(patch("/api/v1/pasos/" + hecho).cookie(a.po()), "{\"estado\":\"HECHO\",\"nota\":\"" + NOTA + "\"}")).andExpect(status().isOk());
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_PASO", "pasoId", hecho))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Solo se quita un paso que no ha empezado"));

            String cambio = proponer(a, cuerpoSimple("QUITAR_PASO", "pasoId", paso(a, 3, 1)));
            autorizar(a, cambio, "ACEPTADO");
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_PASO", "pasoId", paso(a, 3, 2)))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("pasoId"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La fase debe conservar al menos un paso"));
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_PASO", "pasoId", UUID.randomUUID()))).andExpect(status().isUnprocessableEntity());
        }

        @Test
        void sinUnCambioAceptadoLaBaseTampocoDejaQuitarElPaso() throws Exception {
            Aprobado a = aprobado();
            UUID paso = paso(a, 2, 1);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE pasos SET estado = 'QUITADO'::estado_paso WHERE id = ?", paso))
                    .hasMessageContaining("requiere un cambio autorizado");
        }
    }

    // =========================================================================================== Cotizar un cambio de alcance

    @Nested
    class AgregarAlcance {

        private UUID solicitud(Aprobado a) {
            return jdbc.queryForObject("""
                    INSERT INTO solicitudes_cambio (proyecto_fase_id, cliente_id, tipo, descripcion, estado)
                    VALUES (?, ?, 'ALCANCE', 'Quiero que los clientes puedan pagar también contra entrega en Cúcuta.', 'POR_COTIZAR') RETURNING id
                    """, UUID.class, fase(a, 1), a.proyecto().clienteId());
        }

        private String cuerpo(UUID solicitud) {
            return "{\"tipo\":\"AGREGAR_ALCANCE\",\"solicitudCambioId\":\"" + solicitud + "\",\"tituloPaso\":\"Pago contra entrega\","
                    + "\"explicacionPaso\":\"Se agrega el pago contra entrega para clientes de Cúcuta.\",\"motivo\":\"" + MOTIVO + "\","
                    + "\"nuevaEntrega\":\"" + nuevaEntrega() + "\",\"valorAdicional\":900000}";
        }

        private String estadoSolicitud(UUID solicitud) {
            return jdbc.queryForObject("SELECT estado::text FROM solicitudes_cambio WHERE id = ?", String.class, solicitud);
        }

        @Test
        void elCicloCompletoCreaElPasoDeAlcanceSumaElCostoYAlTerminarloQuedaResuelto() throws Exception {
            Aprobado a = aprobado();
            UUID solicitud = solicitud(a);
            mvc.perform(get(ruta(a)).cookie(a.po())).andExpect(jsonPath("$.alcancesPorCotizar", hasSize(1)))
                    .andExpect(jsonPath("$.alcancesPorCotizar[0].id").value(solicitud.toString()));

            String cambio = proponer(a, cuerpo(solicitud));
            assertThat(estadoSolicitud(solicitud)).isEqualTo("POR_COTIZAR");
            autorizar(a, cambio, "AUTORIZADO");
            assertThat(estadoSolicitud(solicitud)).isEqualTo("COTIZADO");
            assertThat(jdbc.queryForObject("SELECT costo FROM solicitudes_cambio WHERE id = ?", BigDecimal.class, solicitud)).isEqualByComparingTo("900000");
            assertThat(jdbc.queryForObject("SELECT dias_adicionales FROM solicitudes_cambio WHERE id = ?", Integer.class, solicitud)).isEqualTo(15);

            aceptar(a, cambio);
            assertThat(estadoSolicitud(solicitud)).isEqualTo("ACEPTADO");
            UUID paso = jdbc.queryForObject("SELECT paso_id FROM solicitudes_cambio WHERE id = ?", UUID.class, solicitud);
            assertThat(jdbc.queryForObject("SELECT tipo::text || '|' || origen || '|' || estado::text || '|' || proyecto_fase_id::text FROM pasos WHERE id = ?",
                    String.class, paso)).isEqualTo("ALCANCE|Lo pidió el cliente|PENDIENTE|" + fase(a, 1));
            assertThat(entrega(a)).isEqualTo(nuevaEntrega());
            mvc.perform(get(ruta(a)).cookie(a.po())).andExpect(jsonPath("$.valorTotal").value(8700000)).andExpect(jsonPath("$.alcancesPorCotizar", hasSize(0)));

            mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), "{\"estado\":\"HECHO\",\"nota\":\"" + NOTA + "\"}")).andExpect(status().isOk());
            assertThat(estadoSolicitud(solicitud)).isEqualTo("RESUELTO");
        }

        @Test
        void siElClienteNoAceptaLaSolicitudQuedaRechazada_yElPlanSigueIgual() throws Exception {
            Aprobado a = aprobado();
            UUID solicitud = solicitud(a);
            String cambio = proponer(a, cuerpo(solicitud));
            autorizar(a, cambio, "AUTORIZADO");
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/cambios-plan/" + cambio + "/rechazar")
                    .cookie(a.cliente()).contentType("application/json").content("{\"motivo\":\"Es más caro de lo que esperaba.\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADO_CLIENTE"));
            assertThat(estadoSolicitud(solicitud)).isEqualTo("RECHAZADO");
            assertThat(entrega(a)).isEqualTo(entregaActual());
            assertThat(avisos(a.proyecto().equipo().usuario(), "CAMBIO_PLAN_RECHAZADO")).isEqualTo(1);
        }

        @Test
        void siElAdministradorLaRechazaLaSolicitudSigueParaCotizarseDeNuevo() throws Exception {
            Aprobado a = aprobado();
            UUID solicitud = solicitud(a);
            String cambio = proponer(a, cuerpo(solicitud));
            mvc.perform(json(patch("/api/v1/cambios-plan/" + cambio + "/rechazar").cookie(a.admin()), "{\"motivo\":\"El costo está muy alto, ajústalo.\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADO_ADMIN"));
            assertThat(estadoSolicitud(solicitud)).isEqualTo("POR_COTIZAR");
            assertThat(avisos(a.proyecto().equipo().usuario(), "CAMBIO_PLAN_RECHAZADO")).isEqualTo(1);
            // Se puede cotizar otra vez
            proponer(a, cuerpo(solicitud));
        }

        @Test
        void sinSolicitudPorCotizarOConDatosIncompletosNoSePropone() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpo(UUID.randomUUID()))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("solicitudCambioId"));
            UUID solicitud = solicitud(a);
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpo(solicitud).replace("Pago contra entrega", "Ab"))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("tituloPaso"));
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpo(solicitud).replace("Se agrega el pago contra entrega para clientes de Cúcuta.", "Corta")))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("explicacionPaso"));
        }
    }

    // =========================================================================================== Permisos y estados

    @Nested
    class PermisosYExcepciones {

        @Test
        void soloUnCambioAbiertoPorProyecto() throws Exception {
            Aprobado a = aprobado();
            proponer(a, cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 3)));
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 2)))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este proyecto ya tiene un cambio de plan esperando respuesta"));
        }

        @Test
        void soloElProductOwnerDelEquipoProponeYSoloElAdministradorAutoriza() throws Exception {
            Aprobado a = aprobado();
            String cuerpo = cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 3));
            mvc.perform(json(post(ruta(a)).cookie(a.admin()), cuerpo)).andExpect(status().isForbidden());
            mvc.perform(json(post(ruta(a)).cookie(sesionDe(equipo().email())), cuerpo)).andExpect(status().isForbidden());
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo)).andExpect(status().isForbidden());
            mvc.perform(json(post(ruta(a)), cuerpo)).andExpect(status().isUnauthorized());
            assertThat(auditoriaDe(a, "Intentó proponer un cambio en el plan de otro equipo")).isEqualTo(2);

            String cambio = proponer(a, cuerpo);
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/autorizar").cookie(a.po())).andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/autorizar").cookie(a.cliente())).andExpect(status().isForbidden());
            // El cliente no acepta lo que aún no se autorizó
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/aceptar").cookie(a.cliente())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este cambio no está esperando tu respuesta"));
            mvc.perform(json(patch("/api/v1/cambios-plan/" + cambio + "/rechazar").cookie(a.cliente()), "{\"motivo\":\"No estoy de acuerdo con esto.\"}"))
                    .andExpect(status().isConflict());
            assertThat(estadoCambio(cambio)).isEqualTo("PROPUESTO");
            autorizar(a, cambio, "AUTORIZADO");
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/autorizar").cookie(a.admin())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este cambio de plan ya fue respondido"));
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/aceptar").cookie(a.po())).andExpect(status().isForbidden());
        }

        @Test
        void unClienteAjenoNoVeNiResponde_ElProyectoNoExisteParaEl() throws Exception {
            Aprobado a = aprobado();
            String cambio = proponer(a, cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 3)));
            autorizar(a, cambio, "AUTORIZADO");
            Cookie otro = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(get(ruta(a)).cookie(otro)).andExpect(status().isNotFound());
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/aceptar").cookie(otro)).andExpect(status().isNotFound());
            mvc.perform(json(patch("/api/v1/cambios-plan/" + cambio + "/rechazar").cookie(otro), "{\"motivo\":\"No estoy de acuerdo con esto.\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(get(ruta(a)).cookie(sesionDe(equipo().email()))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/cambios-plan").cookie(a.cliente())).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/cambios-plan").cookie(a.po())).andExpect(status().isForbidden());
            assertThat(estadoCambio(cambio)).isEqualTo("AUTORIZADO");
        }

        @Test
        void elAdministradorRechazaConUnMotivoYElPlanSigueIgual() throws Exception {
            Aprobado a = aprobado();
            String cambio = proponer(a, cuerpoFase("Capacitación", 4, true));
            String url = "/api/v1/cambios-plan/" + cambio + "/rechazar";
            mvc.perform(json(patch(url).cookie(a.admin()), "{\"motivo\":\"Muy corto\"}")).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
            mvc.perform(json(patch(url).cookie(a.admin()), "{\"motivo\":\"\"}")).andExpect(status().isUnprocessableEntity());
            mvc.perform(json(patch(url).cookie(a.admin()), "{\"motivo\":\"No hay presupuesto para esta fase todavía.\"}")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("RECHAZADO_ADMIN"));
            assertThat(nombresDeFases(a)).hasSize(3);
            assertThat(entrega(a)).isEqualTo(entregaActual());
            // Un cambio cerrado ya no cambia y deja pasar uno nuevo
            mvc.perform(json(patch(url).cookie(a.admin()), "{\"motivo\":\"No hay presupuesto para esta fase todavía.\"}")).andExpect(status().isConflict());
            mvc.perform(patch("/api/v1/cambios-plan/" + cambio + "/autorizar").cookie(a.admin())).andExpect(status().isConflict());
            proponer(a, cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 3)));
        }

        @Test
        void conElProyectoPausadoONoAprobadoNoSePropone() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(patch("/api/v1/proyectos/" + a.proyecto().codigo() + "/estado").cookie(a.admin()),
                    "{\"estado\":\"PAUSADO\",\"motivo\":\"El cliente pidió una pausa mientras consigue el presupuesto.\"}")).andExpect(status().isOk());
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpoSimple("QUITAR_FASE", "faseId", fase(a, 3)))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Solo se cambia el plan de un proyecto activo con el plan aprobado"));

            String admin = crearUsuario("ADMINISTRADOR");
            Proyecto borrador = proyecto(admin);
            mvc.perform(json(post("/api/v1/proyectos/" + borrador.codigo() + "/cambios-plan").cookie(sesionDe(borrador.equipo().email())),
                    "{\"tipo\":\"CAMBIAR_FECHA\",\"motivo\":\"" + MOTIVO + "\",\"nuevaEntrega\":\"" + nuevaEntrega() + "\",\"valorAdicional\":0}")).andExpect(status().isConflict());
        }

        @Test
        void cadaCampoComunSeValida() throws Exception {
            Aprobado a = aprobado();
            UUID tercera = fase(a, 3);
            String base = cuerpoSimple("QUITAR_FASE", "faseId", tercera);
            String[][] casos = {
                    { "tipo", base.replace("QUITAR_FASE", "INVENTAR") },
                    { "motivo", base.replace(MOTIVO, "Corto") },
                    { "motivo", base.replace(MOTIVO, "a".repeat(501)) },
                    { "valorAdicional", base.replace("\"valorAdicional\":0", "\"valorAdicional\":200000001") },
                    { "valorAdicional", base.replace("\"valorAdicional\":0", "\"valorAdicional\":-5") },
                    { "valorAdicional", base.replace("\"valorAdicional\":0", "\"valorAdicional\":1500.5") },
                    { "nuevaEntrega", base.replace(",\"nuevaEntrega\":\"" + nuevaEntrega() + "\"", "") } };
            for (String[] caso : casos) {
                mvc.perform(json(post(ruta(a)).cookie(a.po()), caso[1])).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[?(@.campo=='" + caso[0] + "')]").exists());
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cambios_plan WHERE proyecto_id = ?::uuid", Integer.class, idProyecto(a))).isZero();
        }
    }
}
