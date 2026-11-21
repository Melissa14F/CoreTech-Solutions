package co.coretech.portal.comunicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/** HU-15, HU-16 y D-42 · Solicitar, programar, rechazar y cerrar reuniones de seguimiento (RN-14, RN-15). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class ReunionesTest extends BaseComunicacionPrueba {

    private static final String MOTIVO = "Revisar el avance";
    private static final String ENLACE = "{\"accion\":\"PROGRAMAR\",\"opcion\":1,\"enlace\":\"https://meet.google.com/abc-defg-hij\"}";
    private static final String RESUMEN = "Revisamos el flujo de pago y el cliente aprobó los cambios de color y de tamaño del botón.";
    private static final String PIDIO = "Que el botón de pagar sea más grande y que el color se vea mejor.";

    private static String ruta(Aprobado a) {
        return "/api/v1/proyectos/" + a.proyecto().codigo() + "/solicitudes-reunion";
    }

    private static String id(String respuesta) {
        return respuesta.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    private static String rechazo(String motivo) {
        return "{\"accion\":\"RECHAZAR\",\"motivo\":\"" + motivo + "\"}";
    }

    /** Dos horas válidas distintas: las 10:00 y las 15:00 de un día hábil. */
    private String[] horas() {
        LocalDate dia = diaHabil(3);
        return new String[] { hora(dia, 10), hora(dia, 15) };
    }

    private String solicitar(Aprobado a, String modalidad, String opcion1, String opcion2) throws Exception {
        return id(mvc.perform(json(post(ruta(a)).cookie(a.cliente()), solicitudJson(MOTIVO, RAZON, modalidad, opcion1, opcion2)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void envejecer(String solicitudId) {
        jdbc.update("UPDATE solicitudes_reunion SET fecha_programada = now() - interval '1 hour' WHERE id = ?::uuid", solicitudId);
    }

    @Nested
    class Solicitar {

        @Test
        void elClienteProponeDosHorasYQuedaPendienteEnLaFaseEnCurso() throws Exception {
            Aprobado a = aprobado();
            String[] h = horas();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), solicitudJson(MOTIVO, RAZON, "VIRTUAL", h[0], h[1])))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("S-\\d{4,}")))
                    .andExpect(jsonPath("$.fase").value("Fase 1")).andExpect(jsonPath("$.opciones", hasSize(2)))
                    .andExpect(jsonPath("$.modalidad").value("VIRTUAL"));
            assertThat(avisos(a.proyecto().equipo().usuario(), "REUNION_SOLICITADA")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'Solicitó una reunión' AND usuario_id = ?",
                    Integer.class, a.proyecto().clienteId())).isEqualTo(1);

            // Una sola solicitud pendiente por proyecto (RN-15)
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), solicitudJson(MOTIVO, RAZON, "VIRTUAL", h[0], null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ya tienes una solicitud de reunión en espera para este proyecto"));
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(status().isOk()).andExpect(jsonPath("$.puedeSolicitar").value(false))
                    .andExpect(jsonPath("$.solicitudes", hasSize(1)));
        }

        @Test
        void conLaSegundaOpcionVaciaBastaUnaHora() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), solicitudJson(MOTIVO, RAZON, "PRESENCIAL", horas()[0], null)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.opciones", hasSize(1)));
        }

        @Test
        void sinElPlanAprobadoElBotonNoAparece() throws Exception {
            String admin = crearUsuario("ADMINISTRADOR");
            Proyecto p = proyecto(admin);
            Cookie cliente = sesionDe(p.clienteEmail());
            mvc.perform(get("/api/v1/proyectos/" + p.codigo() + "/solicitudes-reunion").cookie(cliente)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.puedeSolicitar").value(false));
            mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/solicitudes-reunion").cookie(cliente),
                    solicitudJson(MOTIVO, RAZON, "VIRTUAL", horas()[0], null))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value("Podrás pedir reuniones de seguimiento cuando apruebes el plan de trabajo"));
        }

        @Test
        void conElProyectoPausadoNoSePideReunion() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(patch("/api/v1/proyectos/" + a.proyecto().codigo() + "/estado").cookie(sesionDe(a.admin())),
                    "{\"estado\":\"PAUSADO\",\"motivo\":\"El cliente pidió una pausa mientras consigue el presupuesto.\"}"))
                    .andExpect(status().isOk());
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), solicitudJson(MOTIVO, RAZON, "VIRTUAL", horas()[0], null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("El proyecto no admite solicitudes de reunión en su estado actual"));
        }

        @Test
        void cadaCampoSeValida() throws Exception {
            Aprobado a = aprobado();
            String bien = horas()[0];
            LocalDate sabado = diaHabil(3);
            while (sabado.getDayOfWeek() != DayOfWeek.SATURDAY) {
                sabado = sabado.plusDays(1);
            }
            String[][] casos = {
                    { "motivo", solicitudJson("Un motivo inventado", RAZON, "VIRTUAL", bien, null) },
                    { "razon", solicitudJson(MOTIVO, "Muy corta", "VIRTUAL", bien, null) },
                    { "razon", solicitudJson(MOTIVO, "a".repeat(501), "VIRTUAL", bien, null) },
                    { "modalidad", solicitudJson(MOTIVO, RAZON, "TELEPATICA", bien, null) },
                    { "opcion1", solicitudJson(MOTIVO, RAZON, "VIRTUAL", null, null) },
                    { "opcion1", solicitudJson(MOTIVO, RAZON, "VIRTUAL", hora(hoy().minusDays(1), 10), null) },
                    { "opcion1", solicitudJson(MOTIVO, RAZON, "VIRTUAL", hora(sabado, 10), null) },
                    { "opcion1", solicitudJson(MOTIVO, RAZON, "VIRTUAL", hora(diaHabil(3), 12), null) },
                    { "opcion1", solicitudJson(MOTIVO, RAZON, "VIRTUAL", hora(diaHabil(3), 7), null) },
                    { "opcion1", solicitudJson(MOTIVO, RAZON, "VIRTUAL", hora(diaHabil(3), 18), null) },
                    { "opcion2", solicitudJson(MOTIVO, RAZON, "VIRTUAL", bien, bien) },
                    { "opcion2", solicitudJson(MOTIVO, RAZON, "VIRTUAL", bien, hora(sabado, 10)) } };
            for (String[] caso : casos) {
                mvc.perform(json(post(ruta(a)).cookie(a.cliente()), caso[1])).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[0].campo").value(caso[0]));
            }
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), solicitudJson(MOTIVO, RAZON, "VIRTUAL", bien, bien)))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Las dos opciones deben ser distintas"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitudes_reunion WHERE proyecto_id = (SELECT id FROM proyectos WHERE codigo = ?)",
                    Integer.class, a.proyecto().codigo())).isZero();
        }

        @Test
        void soloElClienteDuenoPideLaReunion() throws Exception {
            Aprobado a = aprobado();
            Cookie otroCliente = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(json(post(ruta(a)).cookie(otroCliente), solicitudJson(MOTIVO, RAZON, "VIRTUAL", horas()[0], null)))
                    .andExpect(status().isNotFound());
            mvc.perform(get(ruta(a)).cookie(otroCliente)).andExpect(status().isNotFound());
            assertThat(auditoriaDe(a.proyecto().codigo(), "Intentó pedir una reunión en un proyecto ajeno")).isEqualTo(1);
            // El equipo no pide reuniones: es solo del cliente
            mvc.perform(json(post(ruta(a)).cookie(a.po()), solicitudJson(MOTIVO, RAZON, "VIRTUAL", horas()[0], null)))
                    .andExpect(status().isForbidden());
            mvc.perform(get(ruta(a))).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Responder {

        @Test
        void elProductOwnerProgramaUnaDeLasHorasConElEnlace() throws Exception {
            Aprobado a = aprobado();
            String[] h = horas();
            String id = solicitar(a, "VIRTUAL", h[0], h[1]);
            String url = "/api/v1/solicitudes-reunion/" + id;

            mvc.perform(json(patch(url).cookie(a.po()), "{\"accion\":\"PROGRAMAR\",\"opcion\":2,\"enlace\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("enlace"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Pega el enlace: la reunión es virtual."));
            mvc.perform(json(patch(url).cookie(a.po()), "{\"accion\":\"PROGRAMAR\",\"opcion\":2,\"enlace\":\"ftp://servidor\"}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("enlace"));
            mvc.perform(json(patch(url).cookie(a.po()), "{\"accion\":\"PROGRAMAR\",\"opcion\":3,\"enlace\":\"https://meet.google.com/abc\"}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("horario"));

            mvc.perform(json(patch(url).cookie(a.po()), "{\"accion\":\"PROGRAMAR\",\"opcion\":2,\"enlace\":\"https://meet.google.com/abc-defg\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                    .andExpect(jsonPath("$.enlace").value("https://meet.google.com/abc-defg"));
            assertThat(jdbc.queryForObject("SELECT fecha_programada = opcion_2 FROM solicitudes_reunion WHERE id = ?::uuid", Boolean.class, id))
                    .isTrue();
            assertThat(avisos(a.proyecto().clienteId(), "REUNION_PROGRAMADA")).isEqualTo(1);

            mvc.perform(json(patch(url).cookie(a.po()), ENLACE)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Esta solicitud ya fue respondida"));
            mvc.perform(json(patch(url).cookie(a.po()), rechazo("Ya no podemos atender esta reunión.")))
                    .andExpect(status().isConflict());
        }

        @Test
        void unaReunionPresencialNoNecesitaEnlace() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "PRESENCIAL", horas()[0], null);
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + id).cookie(a.po()), "{\"accion\":\"PROGRAMAR\",\"opcion\":1}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PROGRAMADA")).andExpect(jsonPath("$.enlace").doesNotExist());
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + id).cookie(a.po()), "{\"accion\":\"PROGRAMAR\",\"opcion\":2}"))
                    .andExpect(status().isConflict());
        }

        @Test
        void unHorarioQueChocaConOtraReunionDelEquipoNoSePrograma() throws Exception {
            Aprobado a = aprobado();
            String h = horas()[0];
            String primera = solicitar(a, "VIRTUAL", h, null);
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + primera).cookie(a.po()), ENLACE)).andExpect(status().isOk());
            String segunda = solicitar(a, "VIRTUAL", h, null);
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + segunda).cookie(a.po()), ENLACE)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ese horario ya no está disponible, propón otro o rechaza"));
            // Rechazarla sigue siendo posible
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + segunda).cookie(a.po()),
                    rechazo("Ese horario ya está ocupado; propón otro por favor."))).andExpect(status().isOk());
        }

        @Test
        void unaHoraQueYaPasoNoSePrograma() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "VIRTUAL", horas()[0], null);
            jdbc.update("UPDATE solicitudes_reunion SET opcion_1 = now() - interval '1 hour' WHERE id = ?::uuid", id);
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + id).cookie(a.po()), ENLACE)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ese horario ya no está disponible, propón otro o rechaza"));
        }

        @Test
        void rechazarPideUnMotivoYElClientePuedeSolicitarOtra() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "VIRTUAL", horas()[0], null);
            String url = "/api/v1/solicitudes-reunion/" + id;
            mvc.perform(json(patch(url).cookie(a.po()), rechazo("Muy corto"))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
            mvc.perform(json(patch(url).cookie(a.po()), rechazo("a".repeat(301)))).andExpect(status().isUnprocessableEntity());

            mvc.perform(json(patch(url).cookie(a.po()), rechazo("La duda se resolvió por correo; te enviamos el detalle.")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"))
                    .andExpect(jsonPath("$.motivoRechazo").value("La duda se resolvió por correo; te enviamos el detalle."));
            assertThat(avisos(a.proyecto().clienteId(), "REUNION_RECHAZADA")).isEqualTo(1);

            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(jsonPath("$.puedeSolicitar").value(true));
            solicitar(a, "VIRTUAL", horas()[0], null);
        }

        @Test
        void soloElProductOwnerDelEquipoOElAdministradorResponden() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "VIRTUAL", horas()[0], null);
            String url = "/api/v1/solicitudes-reunion/" + id;

            mvc.perform(json(patch(url).cookie(sesionDe(equipo().email())), ENLACE)).andExpect(status().isForbidden());
            assertThat(auditoriaDe(id.isEmpty() ? "" : jdbc.queryForObject("SELECT codigo FROM solicitudes_reunion WHERE id = ?::uuid",
                    String.class, id), "Intentó responder una solicitud de reunión de otro equipo")).isEqualTo(1);
            mvc.perform(json(patch(url).cookie(a.cliente()), ENLACE)).andExpect(status().isForbidden());
            mvc.perform(json(patch(url).cookie(a.po()), "{\"accion\":\"OTRA\"}")).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("accion"));
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + UUID.randomUUID()).cookie(a.po()), ENLACE))
                    .andExpect(status().isNotFound());

            // El administrador también programa y el Product Owner se entera
            mvc.perform(json(patch(url).cookie(sesionDe(a.admin())), ENLACE)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PROGRAMADA"));
            assertThat(avisos(a.proyecto().equipo().usuario(), "REUNION_PROGRAMADA")).isEqualTo(1);
            assertThat(avisos(a.proyecto().clienteId(), "REUNION_PROGRAMADA")).isEqualTo(1);
        }

        @Test
        void soloElAdministradorCancelaUnaReunionProgramada() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "VIRTUAL", horas()[0], null);
            String url = "/api/v1/solicitudes-reunion/" + id;
            String cancelar = "{\"accion\":\"CANCELAR\",\"motivo\":\"El equipo no puede asistir ese día; reprogramamos.\"}";
            Cookie admin = sesionDe(a.admin());

            mvc.perform(json(patch(url).cookie(admin), cancelar)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Solo se cancelan las reuniones programadas"));
            mvc.perform(json(patch(url).cookie(a.po()), ENLACE)).andExpect(status().isOk());
            mvc.perform(json(patch(url).cookie(a.po()), cancelar)).andExpect(status().isForbidden());
            mvc.perform(json(patch(url).cookie(admin), "{\"accion\":\"CANCELAR\",\"motivo\":\"Corto\"}")).andExpect(status().isUnprocessableEntity());
            mvc.perform(json(patch(url).cookie(admin), cancelar)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
            assertThat(avisos(a.proyecto().clienteId(), "REUNION_CANCELADA")).isEqualTo(1);
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(jsonPath("$.puedeSolicitar").value(true));
        }
    }

    @Nested
    class Listas {

        @Test
        void elAdministradorVeTodasYElProductOwnerSoloLasDeSuEquipo() throws Exception {
            Aprobado a = aprobado();
            solicitar(a, "VIRTUAL", horas()[0], null);

            mvc.perform(get("/api/v1/solicitudes-reunion").cookie(a.po())).andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].proyectoCodigo").value(a.proyecto().codigo()))
                    .andExpect(jsonPath("$[0].puedeCerrar").value(false));
            mvc.perform(get("/api/v1/solicitudes-reunion").cookie(sesionDe(a.admin()))).andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.proyectoCodigo=='" + a.proyecto().codigo() + "')]", hasSize(1)));
            mvc.perform(get("/api/v1/solicitudes-reunion").cookie(sesionDe(equipo().email()))).andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/v1/solicitudes-reunion").cookie(a.cliente())).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Resumen {

        private static final String CUERPO = "{\"resumen\":\"" + RESUMEN + "\",\"quePidio\":\"" + PIDIO + "\",\"siguientePaso\":\"DENTRO_DEL_PLAN\"}";

        @Test
        void despuesDeLaReunionElProductOwnerLlenaElResumenYQuedaAtendida() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "VIRTUAL", horas()[0], null);
            String url = "/api/v1/solicitudes-reunion/" + id;

            // Sin programar no se puede cerrar
            mvc.perform(json(post(url + "/resumen").cookie(a.po()), CUERPO)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Esta reunión ya no se puede cerrar"));
            mvc.perform(json(patch(url).cookie(a.po()), ENLACE)).andExpect(status().isOk());
            // Antes de la hora programada, tampoco
            mvc.perform(json(post(url + "/resumen").cookie(a.po()), CUERPO)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La reunión aún no ha ocurrido"));

            envejecer(id);
            mvc.perform(get("/api/v1/solicitudes-reunion").cookie(a.po())).andExpect(jsonPath("$[0].puedeCerrar").value(true));
            mvc.perform(json(post(url + "/resumen").cookie(a.po()), CUERPO)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("ATENDIDA")).andExpect(jsonPath("$.resumen.siguientePaso").value("DENTRO_DEL_PLAN"))
                    .andExpect(jsonPath("$.puedeCerrar").value(false));
            assertThat(avisos(a.proyecto().clienteId(), "REUNION_ATENDIDA")).isEqualTo(1);

            // El cliente y el administrador lo ven
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(jsonPath("$.solicitudes[0].resumen.resumen").value(RESUMEN));
            mvc.perform(get("/api/v1/solicitudes-reunion").cookie(sesionDe(a.admin())))
                    .andExpect(jsonPath("$[?(@.codigo=='" + codigo(id) + "')].resumen.quePidio").value(PIDIO));

            // Una sola vez, y no se edita ni se borra (RNF-02)
            mvc.perform(json(post(url + "/resumen").cookie(a.po()), CUERPO)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Esta reunión ya no se puede cerrar"));
            assertThatThrownBy(() -> jdbc.update("UPDATE resumenes_reunion SET resumen = 'Cambio directo en la base de datos de prueba'"))
                    .hasMessageContaining("inmutable");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM resumenes_reunion")).hasMessageContaining("inmutable");
        }

        @Test
        void cadaCampoDelResumenSeValida() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "VIRTUAL", horas()[0], null);
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + id).cookie(a.po()), ENLACE)).andExpect(status().isOk());
            envejecer(id);
            String url = "/api/v1/solicitudes-reunion/" + id + "/resumen";

            String[][] casos = {
                    { "resumen", "{\"resumen\":\"Corto\",\"quePidio\":\"" + PIDIO + "\",\"siguientePaso\":\"NINGUNO\"}" },
                    { "resumen", "{\"resumen\":\"" + "a".repeat(1001) + "\",\"quePidio\":\"" + PIDIO + "\",\"siguientePaso\":\"NINGUNO\"}" },
                    { "quePidio", "{\"resumen\":\"" + RESUMEN + "\",\"quePidio\":\"Poco\",\"siguientePaso\":\"NINGUNO\"}" },
                    { "quePidio", "{\"resumen\":\"" + RESUMEN + "\",\"quePidio\":\"" + "a".repeat(601) + "\",\"siguientePaso\":\"NINGUNO\"}" },
                    { "siguientePaso", "{\"resumen\":\"" + RESUMEN + "\",\"quePidio\":\"" + PIDIO + "\",\"siguientePaso\":\"OTRO\"}" } };
            for (String[] caso : casos) {
                mvc.perform(json(post(url).cookie(a.po()), caso[1])).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[0].campo").value(caso[0]));
            }
            assertThat(jdbc.queryForObject("SELECT estado::text FROM solicitudes_reunion WHERE id = ?::uuid", String.class, id))
                    .isEqualTo("PROGRAMADA");
        }

        @Test
        void soloElProductOwnerDelEquipoLlenaElResumen() throws Exception {
            Aprobado a = aprobado();
            String id = solicitar(a, "VIRTUAL", horas()[0], null);
            mvc.perform(json(patch("/api/v1/solicitudes-reunion/" + id).cookie(a.po()), ENLACE)).andExpect(status().isOk());
            envejecer(id);
            String url = "/api/v1/solicitudes-reunion/" + id + "/resumen";

            mvc.perform(json(post(url).cookie(sesionDe(a.admin())), CUERPO)).andExpect(status().isForbidden());
            mvc.perform(json(post(url).cookie(sesionDe(equipo().email())), CUERPO)).andExpect(status().isForbidden());
            mvc.perform(json(post(url).cookie(a.cliente()), CUERPO)).andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM resumenes_reunion WHERE solicitud_id = ?::uuid", Integer.class, id)).isZero();
        }
    }

    private String codigo(String solicitudId) {
        return jdbc.queryForObject("SELECT codigo FROM solicitudes_reunion WHERE id = ?::uuid", String.class, solicitudId);
    }
}
