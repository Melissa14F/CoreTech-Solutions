package co.coretech.portal.proyectos;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/**
 * Crear proyecto desde una cita atendida y gestionar proyectos contra PostgreSQL 15 (HU-02, HU-17, RN-04 a RN-06, RN-27,
 * RN-29, D-33): cada excepción de las dos HU, un caso por cada condición de "en riesgo" y que un proyecto pausado no ocupe
 * cupo. Cada prueba crea sus propios usuarios, servicio, equipos y citas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class ProyectosTest {

    static final String CLAVE = "Segura2026!";
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transacciones;

    // ------------------------------------------------------------------------------------------ Crear (HU-02)

    @Test
    void creaElProyectoCopiaLaPlantillaYAvisaAlClienteYAlProductOwner() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = servicioConPlantilla(5);
        Equipo equipo = equipo();
        UUID cliente = idDe(crearUsuario("CLIENTE"));
        UUID cita = cita(cliente, servicio, equipo.id(), "ATENDIDA");

        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cita, equipo.id(), hoy(), hoy().plusDays(30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("P-\\d{4,}")))
                .andExpect(jsonPath("$.estadoPlan").value("BORRADOR"))
                .andExpect(jsonPath("$.fases").value(5));

        String codigo = jdbc.queryForObject("SELECT codigo FROM proyectos WHERE cita_id = ?", String.class, cita);
        assertThat(jdbc.queryForObject("SELECT estado::text || '/' || estado_plan::text || '/' || avance_porcentaje FROM proyectos WHERE cita_id = ?",
                String.class, cita)).isEqualTo("ACTIVO/BORRADOR/0.00");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id JOIN proyectos p ON p.id = f.proyecto_id
                 WHERE p.cita_id = ? AND s.estado = 'PENDIENTE' AND f.estado = 'PENDIENTE'
                """, Integer.class, cita)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND categoria = 'proyectos'", Integer.class,
                codigo)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = 'PROYECTO_CREADO'", Integer.class,
                cliente)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = 'PROYECTO_ASIGNADO'", Integer.class,
                equipo.usuario())).isEqualTo(1);
    }

    @Test
    void laCitaYaConvertidaNoSeUsaDeNuevo() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = servicioConPlantilla(2);
        Equipo equipo = equipo();
        UUID cita = cita(idDe(crearUsuario("CLIENTE")), servicio, equipo.id(), "ATENDIDA");
        String cuerpo = cuerpoCrear(cita, equipo.id(), hoy(), hoy().plusDays(20));
        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpo)).andExpect(status().isCreated());

        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value(org.hamcrest.Matchers.startsWith("Esta cita ya se convirtió en el proyecto P-")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE cita_id = ?", Integer.class, cita)).isEqualTo(1);

        mvc.perform(get("/api/v1/proyectos/citas-atendidas").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.citaId=='" + cita + "')].proyecto").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("P-"))));
    }

    @Test
    void soloUnaCitaAtendidaSePuedeConvertir() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = servicioConPlantilla(2);
        Equipo equipo = equipo();
        UUID cita = cita(idDe(crearUsuario("CLIENTE")), servicio, equipo.id(), "CONFIRMADA");

        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cita, equipo.id(), hoy(), hoy().plusDays(20))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Solo una cita atendida puede convertirse en proyecto"));
    }

    @Test
    void sinPlantillaValidaNoSeCreaElProyecto() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = servicioConPlantilla(0);
        Equipo equipo = equipo();
        UUID cita = cita(idDe(crearUsuario("CLIENTE")), servicio, equipo.id(), "ATENDIDA");

        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cita, equipo.id(), hoy(), hoy().plusDays(20))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Completa la plantilla de fases de este servicio antes de crear el proyecto"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE cita_id = ?", Integer.class, cita)).isZero();
    }

    @Test
    void elEquipoSinCupoNoRecibeOtroProyecto() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = servicioConPlantilla(2);
        Equipo equipo = equipo();
        for (int i = 0; i < 3; i++) {
            UUID c = cita(idDe(crearUsuario("CLIENTE")), servicio, equipo.id(), "ATENDIDA");
            mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(c, equipo.id(), hoy(), hoy().plusDays(20))))
                    .andExpect(status().isCreated());
        }
        UUID cuarta = cita(idDe(crearUsuario("CLIENTE")), servicio, equipo.id(), "ATENDIDA");

        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cuarta, equipo.id(), hoy(), hoy().plusDays(20))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El equipo ya tiene 3 proyectos activos, elige otro"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE cita_id = ?", Integer.class, cuarta)).isZero();
        mvc.perform(get("/api/v1/proyectos/equipos").cookie(admin))
                .andExpect(jsonPath("$[?(@.equipoId=='" + equipo.id() + "')].cupoLibre").value(0));
    }

    @Test
    void validaCadaCampoDelFormulario() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = servicioConPlantilla(2);
        Equipo equipo = equipo();
        UUID cita = cita(idDe(crearUsuario("CLIENTE")), servicio, equipo.id(), "ATENDIDA");

        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), "{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[?(@.campo=='citaId')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='alcance')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='fechaInicio')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='fechaEstimadaEntrega')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='equipoId')]").exists());
        // inicio en el pasado y entrega a menos de 14 días del inicio
        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cita, equipo.id(), hoy().minusDays(1), hoy().plusDays(5))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[?(@.campo=='fechaInicio')].mensaje").value("El inicio no puede ser una fecha pasada."))
                .andExpect(jsonPath("$.errores[?(@.campo=='fechaEstimadaEntrega')].mensaje")
                        .value("La entrega debe ser al menos 2 semanas después del inicio"));
        // el límite exacto: 14 días sí; 13 no
        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cita, equipo.id(), hoy(), hoy().plusDays(13))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cita, equipo.id(), hoy(), hoy().plusDays(14))))
                .andExpect(status().isCreated());
    }

    @Test
    void laCitaOElEquipoInexistentesYLosPermisos() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = servicioConPlantilla(2);
        Equipo equipo = equipo();
        UUID cita = cita(idDe(crearUsuario("CLIENTE")), servicio, equipo.id(), "ATENDIDA");

        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(UUID.randomUUID(), equipo.id(), hoy(), hoy().plusDays(20))))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpoCrear(cita, UUID.randomUUID(), hoy(), hoy().plusDays(20))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("equipoId"));
        String cuerpo = cuerpoCrear(cita, equipo.id(), hoy(), hoy().plusDays(20));
        mvc.perform(json(post("/api/v1/proyectos"), cuerpo)).andExpect(status().isUnauthorized());
        for (String rol : new String[] { "CLIENTE", "PRODUCT_OWNER" }) {
            Cookie otro = sesionDe(crearUsuario(rol));
            mvc.perform(json(post("/api/v1/proyectos").cookie(otro), cuerpo)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/proyectos").cookie(otro)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/proyectos/citas-atendidas").cookie(otro)).andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------------------------------ Lista (HU-17)

    @Test
    void laListaFiltraPorEstadoEquipoYTexto() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        Equipo a = equipo();
        Equipo b = equipo();
        String activo = proyecto(admin, a, "Tienda Filtro Alfa");
        String pausado = proyecto(admin, a, "Tienda Filtro Beta");
        String otroEquipo = proyecto(admin, b, "Tienda Filtro Gamma");
        mvc.perform(json(patch("/api/v1/proyectos/" + pausado + "/estado").cookie(admin), "{\"estado\":\"PAUSADO\",\"motivo\":\"El cliente pidió esperar\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/proyectos?q=filtro&equipoId=" + a.id()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyectos", hasSize(2)))
                .andExpect(jsonPath("$.porFiltro.todas").value(2))
                .andExpect(jsonPath("$.porFiltro.activo").value(1))
                .andExpect(jsonPath("$.porFiltro.pausado").value(1));
        mvc.perform(get("/api/v1/proyectos?q=FILTRO&estado=pausado&equipoId=" + a.id()).cookie(admin))
                .andExpect(jsonPath("$.proyectos", hasSize(1)))
                .andExpect(jsonPath("$.proyectos[0].codigo").value(pausado))
                .andExpect(jsonPath("$.proyectos[0].estado").value("PAUSADO"))
                .andExpect(jsonPath("$.proyectos[0].equipo.productOwner").value("Product Owner de Prueba"));
        mvc.perform(get("/api/v1/proyectos?q=" + otroEquipo).cookie(admin))
                .andExpect(jsonPath("$.proyectos", hasSize(1)));
        mvc.perform(get("/api/v1/proyectos?estado=activo&q=" + activo).cookie(admin))
                .andExpect(jsonPath("$.proyectos[0].faseActual.orden").value(1));
        mvc.perform(get("/api/v1/proyectos?estado=inventado").cookie(admin)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void enRiesgoPorEntregaVencida() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Riesgo por entrega");
        jdbc.update("UPDATE proyectos SET fecha_inicio = ?, fecha_estimada_entrega = ? WHERE codigo = ?", hoy().minusDays(40), hoy().minusDays(2), codigo);

        riesgoDe(admin, codigo, "ENTREGA_VENCIDA");
    }

    @Test
    void enRiesgoPorEntregaCercanaConPocoAvance() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Riesgo por poco avance");
        // Entrega dentro de unos días calendario (≤ 5 hábiles) y 0 % de avance
        jdbc.update("UPDATE proyectos SET fecha_inicio = ?, fecha_estimada_entrega = ? WHERE codigo = ?", hoy().minusDays(30), hoy().plusDays(3), codigo);
        riesgoDe(admin, codigo, "POCO_AVANCE");

        // Con 80 % o más ya no está en riesgo por eso (se simula el avance con la entrega a más de 5 días hábiles)
        jdbc.update("UPDATE proyectos SET fecha_estimada_entrega = ? WHERE codigo = ?", hoy().plusDays(30), codigo);
        mvc.perform(get("/api/v1/proyectos?q=" + codigo).cookie(admin))
                .andExpect(jsonPath("$.proyectos[0].enRiesgo").value(false))
                .andExpect(jsonPath("$.proyectos[0].riesgo").doesNotExist());
    }

    @Test
    void enRiesgoPorSieteDiasHabilesSinPasos() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Riesgo sin avances");
        enviarYAprobar(codigo, hoy().minusDays(40));
        jdbc.update("UPDATE proyectos SET fecha_estimada_entrega = ? WHERE codigo = ?", hoy().plusDays(90), codigo);

        riesgoDe(admin, codigo, "SIN_AVANCES");
        // Un plan aprobado hoy, sin pasos todavía, no está en riesgo
        jdbc.update("UPDATE proyectos SET plan_aprobado_at = now() WHERE codigo = ?", codigo);
        mvc.perform(get("/api/v1/proyectos?q=" + codigo).cookie(admin)).andExpect(jsonPath("$.proyectos[0].enRiesgo").value(false));
    }

    @Test
    void enRiesgoPorPlanEnviadoSinRespuesta() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Riesgo plan sin respuesta");
        jdbc.update("""
                UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 5000000, condiciones_cotizacion = 'Incluye diseño y desarrollo; no incluye hosting.',
                       plan_enviado_at = now() - interval '8 days', plan_plazo_respuesta = ?, plan_vence_el = ? WHERE codigo = ?
                """, hoy().minusDays(2), hoy().plusDays(5), codigo);

        riesgoDe(admin, codigo, "PLAN_SIN_RESPUESTA");
        mvc.perform(get("/api/v1/proyectos?estado=plan&q=" + codigo).cookie(admin)).andExpect(jsonPath("$.proyectos", hasSize(1)));
        // Dentro del plazo no es riesgo
        jdbc.update("UPDATE proyectos SET plan_plazo_respuesta = ? WHERE codigo = ?", hoy().plusDays(1), codigo);
        mvc.perform(get("/api/v1/proyectos?q=" + codigo).cookie(admin)).andExpect(jsonPath("$.proyectos[0].enRiesgo").value(false));
    }

    @Test
    void unProyectoPausadoNoEstaEnRiesgoNiOcupaCupo() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        Equipo equipo = equipo();
        String codigo = proyecto(admin, equipo, "Pausado sin cupo");
        jdbc.update("UPDATE proyectos SET fecha_inicio = ?, fecha_estimada_entrega = ? WHERE codigo = ?", hoy().minusDays(40), hoy().minusDays(2), codigo);
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin), "{\"estado\":\"PAUSADO\",\"motivo\":\"Pausa de prueba larga\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enRiesgo").value(false))
                .andExpect(jsonPath("$.motivoEstado").value("Pausa de prueba larga"));

        mvc.perform(get("/api/v1/proyectos/equipos").cookie(admin))
                .andExpect(jsonPath("$[?(@.equipoId=='" + equipo.id() + "')].proyectosActivos").value(0))
                .andExpect(jsonPath("$[?(@.equipoId=='" + equipo.id() + "')].cupoLibre").value(3));
    }

    // ------------------------------------------------------------------------------------------ Editar (HU-17, D-33)

    @Test
    void conElPlanEnBorradorLaFechaCambiaDeUnaVezYQuedaAuditada() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Edición en borrador");
        LocalDate nueva = hoy().plusDays(60);

        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin), """
                        {"nombre":"Nombre nuevo del proyecto","fechaEstimadaEntrega":"%s","motivo":"El cliente amplió el alcance"}
                        """.formatted(nueva)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyecto.nombre").value("Nombre nuevo del proyecto"))
                .andExpect(jsonPath("$.proyecto.fechaEstimadaEntrega").value(nueva.toString()))
                .andExpect(jsonPath("$.cambioFecha").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = 'Editó un proyecto'", Integer.class, codigo))
                .isEqualTo(1);
    }

    @Test
    void conElPlanEnviadoLaEntregaNoSeCambia() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Edición con plan enviado");
        jdbc.update("""
                UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 5000000, condiciones_cotizacion = 'Incluye diseño y desarrollo; no incluye hosting.',
                       plan_enviado_at = now(), plan_plazo_respuesta = ?, plan_vence_el = ? WHERE codigo = ?
                """, hoy().plusDays(3), hoy().plusDays(10), codigo);

        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin),
                        "{\"fechaEstimadaEntrega\":\"%s\",\"motivo\":\"Cambio de prueba\"}".formatted(hoy().plusDays(90))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("El plan está enviado al cliente; espera su respuesta para cambiar la entrega"));
    }

    @Test
    void conElPlanAprobadoLaEntregaCreaUnCambioYaAutorizado() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID cliente = idDe(crearUsuario("CLIENTE"));
        String codigo = proyecto(admin, equipo(), "Edición con plan aprobado", cliente);
        enviarYAprobar(codigo, hoy());
        LocalDate actual = jdbc.queryForObject("SELECT fecha_estimada_entrega FROM proyectos WHERE codigo = ?", LocalDate.class, codigo);
        LocalDate nueva = actual.plusDays(20);

        // motivo corto: 422
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin),
                        "{\"fechaEstimadaEntrega\":\"%s\",\"motivo\":\"Motivo de 10+\"}".formatted(nueva)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin),
                        "{\"fechaEstimadaEntrega\":\"%s\",\"motivo\":\"El cliente tardó en entregar los datos del sistema\"}".formatted(nueva)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyecto.fechaEstimadaEntrega").value(actual.toString()))
                .andExpect(jsonPath("$.cambioFecha.estado").value("AUTORIZADO"))
                .andExpect(jsonPath("$.cambioFecha.nuevaFecha").value(nueva.toString()));
        assertThat(jdbc.queryForObject("SELECT estado::text FROM cambios_plan WHERE proyecto_id = (SELECT id FROM proyectos WHERE codigo = ?)",
                String.class, codigo)).isEqualTo("AUTORIZADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = 'CAMBIO_FECHA_AUTORIZADO'", Integer.class,
                cliente)).isEqualTo(1);

        // un segundo cambio mientras hay uno abierto: 409
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin),
                        "{\"fechaEstimadaEntrega\":\"%s\",\"motivo\":\"Otro cambio de fecha pedido por el cliente\"}".formatted(nueva.plusDays(5))))
                .andExpect(status().isConflict());
    }

    @Test
    void elMotivoYLosDatosSeValidan() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Validación de edición");

        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin), "{\"nombre\":\"abc\",\"alcance\":\"corto\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='alcance')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='motivo')]").exists());
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin),
                        "{\"fechaEstimadaEntrega\":\"%s\",\"motivo\":\"Entrega pasada\"}".formatted(hoy().minusDays(1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaEstimadaEntrega"));
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin), "{\"motivo\":\"No cambia nada\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(json(patch("/api/v1/proyectos/P-9999").cookie(admin), "{\"nombre\":\"Un nombre valido\",\"motivo\":\"Motivo valido\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(patch("/api/v1/proyectos/inventado").cookie(admin), "{}")).andExpect(status().isNotFound());
    }

    @Test
    void reasignarRespetaElCupoYAvisaAlosDosEquipos() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        Equipo origen = equipo();
        Equipo lleno = equipo();
        Equipo libre = equipo();
        String codigo = proyecto(admin, origen, "Proyecto a reasignar");
        for (int i = 0; i < 3; i++) {
            proyecto(admin, lleno, "Ocupa cupo " + i + " del equipo");
        }

        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin), "{\"equipoId\":\"%s\",\"motivo\":\"Rebalanceo de carga\"}".formatted(lleno.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El equipo ya tiene 3 proyectos activos, elige otro"));
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin), "{\"equipoId\":\"%s\",\"motivo\":\"Rebalanceo de carga\"}".formatted(libre.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyecto.equipo.equipoId").value(libre.id().toString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = 'PROYECTO_REASIGNADO'", Integer.class,
                origen.usuario())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = 'Reasignó el equipo de un proyecto'",
                Integer.class, codigo)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------ Estado (HU-17, RN-29)

    @Test
    void pausarYReanudarVuelvenAContarElCupo() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        Equipo equipo = equipo();
        String pausado = proyecto(admin, equipo, "Se pausa un tiempo");
        mvc.perform(json(patch("/api/v1/proyectos/" + pausado + "/estado").cookie(admin), "{\"estado\":\"PAUSADO\",\"motivo\":\"Temporada alta del cliente\"}"))
                .andExpect(status().isOk());
        // Mientras está pausado, el equipo llena su cupo con otros tres
        for (int i = 0; i < 3; i++) {
            proyecto(admin, equipo, "Otro proyecto número " + i);
        }

        mvc.perform(json(patch("/api/v1/proyectos/" + pausado + "/estado").cookie(admin), "{\"estado\":\"ACTIVO\",\"motivo\":\"El cliente retomó el trabajo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El equipo ya tiene 3 proyectos activos, elige otro"));
        // Reanudar con otro equipo con cupo
        Equipo otro = equipo();
        mvc.perform(json(patch("/api/v1/proyectos/" + pausado + "/estado").cookie(admin),
                        "{\"estado\":\"ACTIVO\",\"motivo\":\"El cliente retomó el trabajo\",\"equipoId\":\"%s\"}".formatted(otro.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.equipo.equipoId").value(otro.id().toString()))
                .andExpect(jsonPath("$.motivoEstado").doesNotExist());
    }

    @Test
    void reanudarUnPlanVencidoORechazadoExigeEquipoYDevuelveElPlanABorrador() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        Equipo equipo = equipo();
        for (String estadoPlan : new String[] { "VENCIDO", "RECHAZADO" }) {
            String codigo = proyecto(admin, equipo, "Plan " + estadoPlan.toLowerCase() + " de prueba");
            jdbc.update("""
                    UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 5000000, condiciones_cotizacion = 'Incluye diseño y desarrollo; no incluye hosting.',
                           plan_enviado_at = now(), plan_plazo_respuesta = ?, plan_vence_el = ? WHERE codigo = ?
                    """, hoy().plusDays(3), hoy().plusDays(10), codigo);
            jdbc.update("UPDATE proyectos SET estado_plan = ?::estado_plan WHERE codigo = ?", estadoPlan, codigo);
            assertThat(jdbc.queryForObject("SELECT estado::text FROM proyectos WHERE codigo = ?", String.class, codigo)).isEqualTo("PAUSADO");

            mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin), "{\"estado\":\"ACTIVO\",\"motivo\":\"El cliente quiere retomarlo\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("equipoId"));
            mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin),
                            "{\"estado\":\"ACTIVO\",\"motivo\":\"El cliente quiere retomarlo\",\"equipoId\":\"%s\"}".formatted(equipo.id())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ACTIVO"))
                    .andExpect(jsonPath("$.estadoPlan").value("BORRADOR"));
        }
    }

    @Test
    void cancelarNoBorraYUnProyectoCerradoEsSoloLectura() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Se cancela este proyecto");

        mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin), "{\"estado\":\"CANCELADO\",\"motivo\":\"corto\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin), "{\"estado\":\"CANCELADO\",\"motivo\":\"El cliente desistió por presupuesto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE codigo = ?", Integer.class, codigo)).isEqualTo(1);

        mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin), "{\"estado\":\"ACTIVO\",\"motivo\":\"Intento de reanudar\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo).cookie(admin), "{\"nombre\":\"Otro nombre distinto\",\"motivo\":\"Intento de editar\"}"))
                .andExpect(status().isConflict());

        // Un proyecto finalizado: solo lectura (409)
        String finalizado = proyecto(admin, equipo(), "Proyecto ya finalizado");
        jdbc.update("""
                UPDATE proyecto_fases SET estado = 'APROBADA', aprobada_at = now() WHERE proyecto_id = (SELECT id FROM proyectos WHERE codigo = ?)
                """, finalizado);
        jdbc.update("""
                UPDATE proyectos SET estado = 'FINALIZADO', garantia_hasta = ?, finalizado_at = now() WHERE codigo = ?
                """, hoy().minusDays(1), finalizado);
        mvc.perform(json(patch("/api/v1/proyectos/" + finalizado + "/estado").cookie(admin), "{\"estado\":\"PAUSADO\",\"motivo\":\"Intento de pausar\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(patch("/api/v1/proyectos/" + finalizado).cookie(admin), "{\"nombre\":\"Otro nombre distinto\",\"motivo\":\"Intento de editar\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void elEstadoPedidoDebeSerValidoYDistintoDelActual() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String codigo = proyecto(admin, equipo(), "Estados del proyecto");

        mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin), "{\"estado\":\"FINALIZADO\",\"motivo\":\"No se puede\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(admin), "{\"estado\":\"ACTIVO\",\"motivo\":\"Ya está activo\"}"))
                .andExpect(status().isConflict());
        Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
        mvc.perform(json(patch("/api/v1/proyectos/" + codigo + "/estado").cookie(cliente), "{\"estado\":\"PAUSADO\",\"motivo\":\"No tiene permiso\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------ Ayudas

    private void riesgoDe(Cookie admin, String codigo, String motivo) throws Exception {
        mvc.perform(get("/api/v1/proyectos?q=" + codigo).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyectos[0].enRiesgo").value(true))
                .andExpect(jsonPath("$.proyectos[0].riesgo").value(motivo));
        mvc.perform(get("/api/v1/proyectos?estado=riesgo&q=" + codigo).cookie(admin)).andExpect(jsonPath("$.proyectos", hasSize(1)));
        mvc.perform(get("/api/v1/proyectos?estado=riesgo").cookie(admin))
                .andExpect(jsonPath("$.proyectos[?(@.codigo=='" + codigo + "')]").exists());
    }

    /** Envía el plan y lo aprueba como lo hará P17; {@code aprobadoEl} es el día de la aprobación. */
    private void enviarYAprobar(String codigo, LocalDate aprobadoEl) {
        jdbc.update("""
                UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 5000000, condiciones_cotizacion = 'Incluye diseño y desarrollo; no incluye hosting.',
                       plan_enviado_at = now(), plan_plazo_respuesta = ?, plan_vence_el = ? WHERE codigo = ?
                """, hoy().plusDays(3), hoy().plusDays(10), codigo);
        jdbc.update("UPDATE proyectos SET estado_plan = 'APROBADO', plan_aprobado_at = ? WHERE codigo = ?",
                Timestamp.from(aprobadoEl.atTime(10, 0).atZone(BOGOTA).toInstant()), codigo);
    }

    private String proyecto(Cookie admin, Equipo equipo, String nombre) throws Exception {
        return proyecto(admin, equipo, nombre, idDe(crearUsuario("CLIENTE")));
    }

    private String proyecto(Cookie admin, Equipo equipo, String nombre, UUID cliente) throws Exception {
        UUID cita = cita(cliente, servicioConPlantilla(3), equipo.id(), "ATENDIDA");
        String cuerpo = """
                {"citaId":"%s","nombre":"%s","alcance":"Alcance acordado en la reunión con el cliente de prueba.",
                 "fechaInicio":"%s","fechaEstimadaEntrega":"%s","equipoId":"%s"}
                """.formatted(cita, nombre, hoy(), hoy().plusDays(40), equipo.id());
        mvc.perform(json(post("/api/v1/proyectos").cookie(admin), cuerpo)).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT codigo FROM proyectos WHERE cita_id = ?", String.class, cita);
    }

    private static String cuerpoCrear(UUID cita, UUID equipo, LocalDate inicio, LocalDate entrega) {
        return """
                {"citaId":"%s","nombre":"Proyecto de prueba","alcance":"Alcance acordado en la reunión con el cliente de prueba.",
                 "fechaInicio":"%s","fechaEstimadaEntrega":"%s","equipoId":"%s"}
                """.formatted(cita, inicio, entrega, equipo);
    }

    private static LocalDate hoy() {
        return LocalDate.now(BOGOTA);
    }

    /** Servicio con una plantilla de {@code fases} fases (la primera la aprueba el cliente), cada una con 2 pasos. */
    private UUID servicioConPlantilla(int fases) {
        UUID id = UUID.randomUUID();
        String sufijo = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO servicios (id, nombre, slug, descripcion_corta, imagen_url, imagen_alt, orden, visible)
                VALUES (?, ?, ?, 'Servicio de prueba para proyectos', 'img/servicios/app-movil.webp', 'Imagen de prueba',
                        (SELECT coalesce(max(orden), 0) + 1 FROM servicios), true)
                """, id, "Servicio " + sufijo, "servicio-" + sufijo);
        // La revisión de RN-28 se hace al confirmar: toda la plantilla va en una sola transacción
        new org.springframework.transaction.support.TransactionTemplate(transacciones).executeWithoutResult(t -> {
        for (int f = 1; f <= fases; f++) {
            UUID fase = UUID.randomUUID();
            jdbc.update("INSERT INTO plantilla_fases (id, servicio_id, orden, nombre, que_es, aprueba_cliente) VALUES (?, ?, ?, ?, 'Qué es', ?)",
                    fase, id, f, "Fase " + f, f == 1);
            for (int p = 1; p <= 2; p++) {
                jdbc.update("INSERT INTO plantilla_pasos (plantilla_fase_id, orden, titulo, explicacion) VALUES (?, ?, ?, 'Explicación')",
                        fase, p, "Paso " + p + " de la fase " + f);
            }
        }
        });
        return id;
    }

    private UUID cita(UUID cliente, UUID servicio, UUID equipo, String estado) {
        UUID id = UUID.randomUUID();
        Instant fecha = Instant.now().minus(3 + (int) (Math.random() * 50), ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        jdbc.update("""
                INSERT INTO citas (id, codigo, cliente_id, servicio_id, equipo_id, fecha_hora, modalidad, descripcion_necesidad, estado,
                                   enlace_reunion, confirmada_at, atendida_at)
                VALUES (?, fn_siguiente_codigo_cita(), ?, ?, ?, ?, 'VIRTUAL', 'Necesito un sistema para mi empresa y su operación.',
                        ?::estado_cita, 'https://meet.google.com/abc-defg-hij', ?, ?)
                """, id, cliente, servicio, equipo, Timestamp.from(fecha), estado, Timestamp.from(fecha.minus(1, ChronoUnit.DAYS)),
                estado.equals("ATENDIDA") ? Timestamp.from(fecha.plus(1, ChronoUnit.HOURS)) : null);
        return id;
    }

    record Equipo(UUID id, UUID usuario) {
    }

    private Equipo equipo() {
        String email = crearUsuario("PRODUCT_OWNER");
        UUID usuario = idDe(email);
        UUID desarrollador = jdbc.queryForObject("""
                INSERT INTO desarrolladores (nombre, correo, celular, especialidad, usuario_id)
                VALUES ('Product Owner de Prueba', ?, '3001234567', 'FULL_STACK', ?) RETURNING id
                """, UUID.class, email, usuario);
        UUID equipo = jdbc.queryForObject("INSERT INTO equipos (max_proyectos, activo) VALUES (3, true) RETURNING id", UUID.class);
        jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, 'PRODUCT_OWNER')", equipo, desarrollador);
        return new Equipo(equipo, usuario);
    }

    private UUID idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);
    }

    private String crearUsuario(String rol) {
        String email = "prueba-" + UUID.randomUUID().toString().substring(0, 12)
                + (rol.equals("CLIENTE") ? "@correo.com" : "@coretechsolutions.co");
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, true, now() FROM roles WHERE codigo = ?
                """, email, codificador.encode(CLAVE), rol);
        return email;
    }

    private Cookie sesionDe(String email) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s","recordarme":false}
                        """.formatted(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }
}
