package co.coretech.portal.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
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
 * Administración de citas y trabajo del equipo contra PostgreSQL 15 (HU-09, HU-10, RN-02, RN-05, RN-11, RN-12, RN-23, RN-24):
 * una prueba por cada fila de "Excepciones" de las dos fichas, el ciclo completo y que un Product Owner no ve ni toca las
 * citas de otro equipo. Las citas se insertan directo con horas propias (lejos de las que agenda {@link CitasTest}) para que
 * ninguna prueba dependa de otra.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class GestionCitasTest {

    static final String CLAVE = "Segura2026!";
    static final String RESUMEN = "Hablamos del alcance de la tienda y de los tiempos de entrega que necesita.";
    static final String DESCRIPCION = "Dueña de un negocio de accesorios que hoy vende solo por redes sociales.";
    static final String MENSAJE_NO_MODIFICABLE = "Esta cita ya no se puede modificar";

    /** Cada cita nueva usa una hora distinta (el índice ux_cita_franja no deja repetirla). */
    static final AtomicInteger HORAS = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    // ---------------------------------------------------------------- HU-09 · Administrador

    @Nested
    class Administrador {

        @Test
        void listaConFiltrosDeEstadoSinEquipoEquipoYFechaYCuentaCadaFiltro() throws Exception {
            Equipo equipo = equipo();
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            Instant dia = hora(40);
            UUID sinEquipo = cita(cliente, null, "PENDIENTE", "VIRTUAL", dia);
            UUID confirmada = cita(cliente, equipo.id(), "CONFIRMADA", "PRESENCIAL", dia.plus(1, ChronoUnit.HOURS));
            cita(cliente, null, "CANCELADA", "VIRTUAL", dia.plus(2, ChronoUnit.HOURS));
            String rango = "desde=" + diaColombia(dia) + "&hasta=" + diaColombia(dia);

            mvc.perform(get("/api/v1/citas/gestion?" + rango).cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.citas", hasSize(3)))
                    .andExpect(jsonPath("$.total").value(3))
                    .andExpect(jsonPath("$.sinEquipo").value(1))
                    .andExpect(jsonPath("$.porEstado.PENDIENTE").value(1))
                    .andExpect(jsonPath("$.porEstado.CONFIRMADA").value(1))
                    .andExpect(jsonPath("$.porEstado.CANCELADA").value(1))
                    .andExpect(jsonPath("$.porEstado.ATENDIDA").value(0));
            mvc.perform(get("/api/v1/citas/gestion?sinEquipo=true&" + rango).cookie(admin))
                    .andExpect(jsonPath("$.citas", hasSize(1)))
                    .andExpect(jsonPath("$.citas[0].citaId").value(sinEquipo.toString()))
                    .andExpect(jsonPath("$.citas[0].equipo").doesNotExist())
                    .andExpect(jsonPath("$.citas[0].fechaHoraColombia").value(org.hamcrest.Matchers.endsWith("-05:00")));
            mvc.perform(get("/api/v1/citas/gestion?estado=CONFIRMADA&" + rango).cookie(admin))
                    .andExpect(jsonPath("$.citas", hasSize(1)))
                    .andExpect(jsonPath("$.citas[0].citaId").value(confirmada.toString()))
                    .andExpect(jsonPath("$.citas[0].equipo.equipoId").value(equipo.id().toString()))
                    .andExpect(jsonPath("$.porEstado.PENDIENTE").value(1));
            mvc.perform(get("/api/v1/citas/gestion?equipoId=" + equipo.id() + "&" + rango).cookie(admin))
                    .andExpect(jsonPath("$.citas", hasSize(1)));
            mvc.perform(get("/api/v1/citas/gestion?desde=" + diaColombia(dia.plus(2, ChronoUnit.DAYS))).cookie(admin))
                    .andExpect(jsonPath("$.citas[?(@.citaId=='" + sinEquipo + "')]").isEmpty());
        }

        @Test
        void losFiltrosInvalidosDan422() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            mvc.perform(get("/api/v1/citas/gestion?estado=INVENTADA").cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("estado"));
            mvc.perform(get("/api/v1/citas/gestion?desde=2026-12-10&hasta=2026-12-01").cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("hasta"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La fecha final debe ser igual o posterior a la inicial."));
            mvc.perform(get("/api/v1/citas/gestion?desde=ayer").cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("desde"));
        }

        @Test
        void paginaLaListaBuscaPorClienteYDiceQuienCancelo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String email = crearUsuario("CLIENTE");
            UUID cliente = idDe(email);
            Instant dia = hora(70);
            for (int i = 0; i < 3; i++) {
                cita(cliente, null, "PENDIENTE", "VIRTUAL", dia.plus(i, ChronoUnit.HOURS));
            }
            UUID cancelada = cita(cliente, null, "CANCELADA", "VIRTUAL", dia.plus(3, ChronoUnit.HOURS));
            jdbc.update("UPDATE citas SET cancelada_por = cliente_id WHERE id = ?", cancelada);
            String rango = "desde=" + diaColombia(dia) + "&hasta=" + diaColombia(dia);

            mvc.perform(get("/api/v1/citas/gestion?tamano=3&pagina=0&" + rango).cookie(admin))
                    .andExpect(jsonPath("$.citas", hasSize(3)))
                    .andExpect(jsonPath("$.citas[0].canceladaPor").value("CLIENTE"))
                    .andExpect(jsonPath("$.totalFiltradas").value(4))
                    .andExpect(jsonPath("$.totalPaginas").value(2))
                    .andExpect(jsonPath("$.total").value(4));
            mvc.perform(get("/api/v1/citas/gestion?tamano=3&pagina=1&" + rango).cookie(admin))
                    .andExpect(jsonPath("$.citas", hasSize(1)))
                    .andExpect(jsonPath("$.pagina").value(1));
            // Una página que no existe se lleva a la última
            mvc.perform(get("/api/v1/citas/gestion?tamano=3&pagina=9&" + rango).cookie(admin)).andExpect(jsonPath("$.pagina").value(1));
            mvc.perform(get("/api/v1/citas/gestion?q=" + email.toUpperCase() + "&" + rango).cookie(admin))
                    .andExpect(jsonPath("$.totalFiltradas").value(4));
            mvc.perform(get("/api/v1/citas/gestion?q=nadie-se-llama-asi&" + rango).cookie(admin))
                    .andExpect(jsonPath("$.citas", hasSize(0)))
                    .andExpect(jsonPath("$.totalPaginas").value(1))
                    .andExpect(jsonPath("$.total").value(4));
        }

        @Test
        void losEquiposParaAsignarSalenConSuCargaYSinLosDesactivados() throws Exception {
            Equipo activo = equipo();
            Equipo apagado = equipo();
            jdbc.update("UPDATE equipos SET activo = false WHERE id = ?", apagado.id());
            cita(idDe(crearUsuario("CLIENTE")), activo.id(), "PENDIENTE", "VIRTUAL", hora(41));
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(get("/api/v1/citas/equipos-disponibles").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.equipoId=='" + activo.id() + "')].citasProximas").value(1))
                    .andExpect(jsonPath("$[?(@.equipoId=='" + activo.id() + "')].maxProyectos").value(3))
                    .andExpect(jsonPath("$[?(@.equipoId=='" + apagado.id() + "')]").isEmpty());
        }

        @Test
        void asignaElEquipoLoAuditaYAvisaAlProductOwner() throws Exception {
            Equipo equipo = equipo();
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), null, "PENDIENTE", "VIRTUAL", hora(42));

            mvc.perform(json(patch("/api/v1/citas/" + cita + "/asignar").cookie(admin),
                            "{\"equipoId\":\"" + equipo.id() + "\",\"nota\":\"Laura ya es cliente.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.equipo.equipoId").value(equipo.id().toString()))
                    .andExpect(jsonPath("$.notaAsignacion").value("Laura ya es cliente."))
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"));

            assertThat(jdbc.queryForObject("SELECT equipo_id FROM citas WHERE id = ?", UUID.class, cita)).isEqualTo(equipo.id());
            assertThat(avisos(equipo.usuarioId(), "CITA_ASIGNADA")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE categoria = 'citas' AND accion = 'Asignó el equipo de una cita' "
                    + "AND entidad_id = (SELECT codigo FROM citas WHERE id = ?)", Integer.class, cita)).isEqualTo(1);
        }

        @Test
        void reasignarUnaCitaConfirmadaLaVuelveAPendienteSinElEnlaceViejoYAvisaATodos() throws Exception {
            Equipo antes = equipo();
            Equipo despues = equipo();
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            UUID cita = cita(cliente, antes.id(), "CONFIRMADA", "VIRTUAL", hora(43));

            mvc.perform(json(patch("/api/v1/citas/" + cita + "/asignar").cookie(admin), "{\"equipoId\":\"" + despues.id() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.enlaceReunion").doesNotExist())
                    .andExpect(jsonPath("$.equipo.emailProductOwner").value(despues.email()));

            assertThat(avisos(despues.usuarioId(), "CITA_ASIGNADA")).isEqualTo(1);
            assertThat(avisos(antes.usuarioId(), "CITA_REASIGNADA")).isEqualTo(1);
            assertThat(avisos(cliente, "CITA_REASIGNADA")).isEqualTo(1);
        }

        @Test
        void asignarUnaCitaAtendidaOCanceladaDa409() throws Exception {
            Equipo equipo = equipo();
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            for (String estado : new String[] { "ATENDIDA", "CANCELADA" }) {
                UUID cita = cita(cliente, null, estado, "VIRTUAL", hora(44 + HORAS.get() % 3));
                mvc.perform(json(patch("/api/v1/citas/" + cita + "/asignar").cookie(admin), "{\"equipoId\":\"" + equipo.id() + "\"}"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.errores[0].mensaje").value(MENSAJE_NO_MODIFICABLE));
            }
        }

        @Test
        void unEquipoDesactivadoOSinElegirYUnaNotaLargaDan422() throws Exception {
            Equipo apagado = equipo();
            jdbc.update("UPDATE equipos SET activo = false WHERE id = ?", apagado.id());
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), null, "PENDIENTE", "VIRTUAL", hora(47));

            mvc.perform(json(patch("/api/v1/citas/" + cita + "/asignar").cookie(admin), "{\"equipoId\":\"" + apagado.id() + "\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("equipoId"));
            mvc.perform(json(patch("/api/v1/citas/" + cita + "/asignar").cookie(admin), "{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Elige quién atenderá la cita."));
            mvc.perform(json(patch("/api/v1/citas/" + cita + "/asignar").cookie(admin),
                            "{\"equipoId\":\"" + equipo().id() + "\",\"nota\":\"" + "x".repeat(301) + "\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("nota"));
            mvc.perform(json(patch("/api/v1/citas/" + UUID.randomUUID() + "/asignar").cookie(admin), "{\"equipoId\":\"" + equipo().id() + "\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void cancelaConMotivoLiberaLaFranjaYAvisaAlClienteYAlProductOwner() throws Exception {
            Equipo equipo = equipo();
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            Instant franja = hora(48);
            UUID cita = cita(cliente, equipo.id(), "CONFIRMADA", "PRESENCIAL", franja);

            mvc.perform(json(patch("/api/v1/citas/" + cita + "/cancelar-admin").cookie(admin),
                            "{\"motivo\":\"El cliente pidió cambiar la fecha por teléfono.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.canceladaPor").value("ADMINISTRACION"))
                    .andExpect(jsonPath("$.motivoCancelacion").value("El cliente pidió cambiar la fecha por teléfono."));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE fecha_hora = ? AND estado IN ('PENDIENTE','CONFIRMADA')",
                    Integer.class, Timestamp.from(franja))).isZero();
            assertThat(avisos(cliente, "CITA_CANCELADA")).isEqualTo(1);
            assertThat(avisos(equipo.usuarioId(), "CITA_CANCELADA")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'Canceló una cita' "
                    + "AND entidad_id = (SELECT codigo FROM citas WHERE id = ?)", Integer.class, cita)).isEqualTo(1);
        }

        @Test
        void sinMotivoOConMotivoCortoDa422YUnaAtendidaOCanceladaDa409() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            UUID pendiente = cita(cliente, null, "PENDIENTE", "VIRTUAL", hora(49));

            mvc.perform(json(patch("/api/v1/citas/" + pendiente + "/cancelar-admin").cookie(admin), "{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
            mvc.perform(json(patch("/api/v1/citas/" + pendiente + "/cancelar-admin").cookie(admin), "{\"motivo\":\"muy corto\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("El motivo debe tener entre 15 y 300 caracteres"));
            assertThat(jdbc.queryForObject("SELECT estado::text FROM citas WHERE id = ?", String.class, pendiente)).isEqualTo("PENDIENTE");

            for (String estado : new String[] { "ATENDIDA", "CANCELADA" }) {
                UUID cita = cita(cliente, null, estado, "VIRTUAL", hora(50 + HORAS.get() % 4));
                mvc.perform(json(patch("/api/v1/citas/" + cita + "/cancelar-admin").cookie(admin),
                                "{\"motivo\":\"Un motivo de más de quince letras.\"}"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.errores[0].mensaje").value(MENSAJE_NO_MODIFICABLE));
            }
        }

        @Test
        void soloLaAdministracionTieneEstosEndpoints() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            Cookie po = sesionDe(crearUsuario("PRODUCT_OWNER"));
            UUID cualquiera = UUID.randomUUID();

            mvc.perform(get("/api/v1/citas/gestion")).andExpect(status().isUnauthorized());
            for (Cookie sesion : new Cookie[] { cliente, po }) {
                mvc.perform(get("/api/v1/citas/gestion").cookie(sesion)).andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/citas/equipos-disponibles").cookie(sesion)).andExpect(status().isForbidden());
                mvc.perform(json(patch("/api/v1/citas/" + cualquiera + "/asignar").cookie(sesion), "{}")).andExpect(status().isForbidden());
                mvc.perform(json(patch("/api/v1/citas/" + cualquiera + "/cancelar-admin").cookie(sesion), "{}")).andExpect(status().isForbidden());
            }
        }

        @Test
        void verElCuestionarioDeUnaCitaAtendida() throws Exception {
            Equipo equipo = equipo();
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), equipo.id(), "ATENDIDA", "VIRTUAL", hora(-60));
            jdbc.update("""
                    INSERT INTO cuestionarios_cita (cita_id, resumen, descripcion_cliente, presupuesto, idioma, urgencia, registrado_por)
                    VALUES (?, ?, ?, 'DE_5_A_15M', 'ESPANOL', 'MEDIA', ?)
                    """, cita, RESUMEN, DESCRIPCION, equipo.usuarioId());
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(get("/api/v1/citas/" + cita + "/cuestionario").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.presupuesto").value("DE_5_A_15M"))
                    .andExpect(jsonPath("$.urgencia").value("MEDIA"))
                    .andExpect(jsonPath("$.resumen").value(RESUMEN));
            mvc.perform(get("/api/v1/citas/" + cita(idDe(crearUsuario("CLIENTE")), null, "PENDIENTE", "VIRTUAL", hora(51)) + "/cuestionario").cookie(admin))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- HU-10 · Desarrollador (Product Owner)

    @Nested
    class ProductOwner {

        @Test
        void veSoloLasCitasDeSuEquipoConAmbasHoras() throws Exception {
            Equipo mio = equipo();
            Equipo otro = equipo();
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            UUID suya = cita(cliente, mio.id(), "PENDIENTE", "VIRTUAL", hora(52));
            UUID ajena = cita(cliente, otro.id(), "PENDIENTE", "VIRTUAL", hora(53));
            cita(cliente, null, "PENDIENTE", "VIRTUAL", hora(54));
            jdbc.update("UPDATE citas SET pais = 'España', zona_horaria = 'Europe/Madrid' WHERE id = ?", suya);

            mvc.perform(get("/api/v1/citas/equipo").cookie(mio.sesion()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].citaId").value(suya.toString()))
                    .andExpect(jsonPath("$[0].pais").value("España"))
                    .andExpect(jsonPath("$[0].fechaHoraLocal").value(org.hamcrest.Matchers.matchesPattern(".*[+]0[12]:00$")))
                    .andExpect(jsonPath("$[0].fechaHoraColombia").value(org.hamcrest.Matchers.endsWith("-05:00")));
            mvc.perform(get("/api/v1/citas/equipo").cookie(otro.sesion()))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].citaId").value(ajena.toString()));
        }

        @Test
        void unProductOwnerNoTocaLasCitasDeOtroEquipoNiLasQueNoTienenEquipo() throws Exception {
            Equipo mio = equipo();
            Equipo otro = equipo();
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            UUID ajena = cita(cliente, otro.id(), "CONFIRMADA", "VIRTUAL", hora(-61));
            UUID sinEquipo = cita(cliente, null, "PENDIENTE", "VIRTUAL", hora(55));

            for (UUID cita : new UUID[] { ajena, sinEquipo }) {
                mvc.perform(json(patch("/api/v1/citas/" + cita + "/confirmar").cookie(mio.sesion()), "{\"enlaceReunion\":\"https://meet.google.com/abc-defg\"}"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.errores[0].mensaje").value("Esta cita no es de tu equipo"));
                mvc.perform(json(post("/api/v1/citas/" + cita + "/cuestionario").cookie(mio.sesion()), cuestionario()))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/citas/" + cita + "/cuestionario").cookie(mio.sesion())).andExpect(status().isForbidden());
            }
            assertThat(jdbc.queryForObject("SELECT estado::text FROM citas WHERE id = ?", String.class, ajena)).isEqualTo("CONFIRMADA");
            mvc.perform(json(patch("/api/v1/citas/" + UUID.randomUUID() + "/confirmar").cookie(mio.sesion()), "{}")).andExpect(status().isNotFound());
        }

        @Test
        void confirmaUnaCitaVirtualConSuEnlaceYAvisaAlCliente() throws Exception {
            Equipo equipo = equipo();
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            UUID cita = cita(cliente, equipo.id(), "PENDIENTE", "VIRTUAL", hora(56));

            mvc.perform(json(patch("/api/v1/citas/" + cita + "/confirmar").cookie(equipo.sesion()),
                            "{\"enlaceReunion\":\"https://meet.google.com/abc-defg-hij\",\"nota\":\"Trae ejemplos de páginas que te gusten.\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                    .andExpect(jsonPath("$.confirmadaAt").exists());

            var fila = jdbc.queryForMap("SELECT estado::text AS estado, enlace_reunion, nota_cliente FROM citas WHERE id = ?", cita);
            assertThat(fila).containsEntry("estado", "CONFIRMADA").containsEntry("enlace_reunion", "https://meet.google.com/abc-defg-hij")
                    .containsEntry("nota_cliente", "Trae ejemplos de páginas que te gusten.");
            assertThat(jdbc.queryForObject("SELECT mensaje FROM notificaciones WHERE usuario_id = ? AND tipo = 'CITA_CONFIRMADA'",
                    String.class, cliente)).contains("https://meet.google.com/abc-defg-hij");
        }

        @Test
        void unaCitaPresencialSeConfirmaSinEnlace() throws Exception {
            Equipo equipo = equipo();
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), equipo.id(), "PENDIENTE", "PRESENCIAL", hora(57));

            mvc.perform(json(patch("/api/v1/citas/" + cita + "/confirmar").cookie(equipo.sesion()), "{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        }

        @Test
        void unaCitaVirtualSinEnlaceOConEnlaceInvalidoDa422YNoCambiaDeEstado() throws Exception {
            Equipo equipo = equipo();
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), equipo.id(), "PENDIENTE", "VIRTUAL", hora(58));

            mvc.perform(json(patch("/api/v1/citas/" + cita + "/confirmar").cookie(equipo.sesion()), "{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("enlaceReunion"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Las citas virtuales necesitan el enlace de la reunión"));
            mvc.perform(json(patch("/api/v1/citas/" + cita + "/confirmar").cookie(equipo.sesion()), "{\"enlaceReunion\":\"meet.google.com/abc\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("El enlace debe empezar por https://"));
            mvc.perform(json(patch("/api/v1/citas/" + cita + "/confirmar").cookie(equipo.sesion()),
                            "{\"enlaceReunion\":\"https://meet.google.com/" + "a".repeat(250) + "\"}"))
                    .andExpect(status().isUnprocessableEntity());
            assertThat(jdbc.queryForObject("SELECT estado::text FROM citas WHERE id = ?", String.class, cita)).isEqualTo("PENDIENTE");
        }

        @Test
        void confirmarUnaCitaYaConfirmadaOCanceladaDa409() throws Exception {
            Equipo equipo = equipo();
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            UUID confirmada = cita(cliente, equipo.id(), "CONFIRMADA", "PRESENCIAL", hora(59));
            UUID cancelada = cita(cliente, equipo.id(), "CANCELADA", "PRESENCIAL", hora(60));

            mvc.perform(json(patch("/api/v1/citas/" + confirmada + "/confirmar").cookie(equipo.sesion()), "{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Esta cita ya fue confirmada"));
            mvc.perform(json(patch("/api/v1/citas/" + cancelada + "/confirmar").cookie(equipo.sesion()), "{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value(MENSAJE_NO_MODIFICABLE));
        }

        @Test
        void llenaElCuestionarioDejaLaCitaAtendidaYAvisaAlClienteYALaAdministracion() throws Exception {
            Equipo equipo = equipo();
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            String emailAdmin = crearUsuario("ADMINISTRADOR");
            UUID cita = cita(cliente, equipo.id(), "CONFIRMADA", "VIRTUAL", hora(-62));

            mvc.perform(json(post("/api/v1/citas/" + cita + "/cuestionario").cookie(equipo.sesion()), cuestionario()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cuestionarioId").exists())
                    .andExpect(jsonPath("$.citaEstado").value("ATENDIDA"));

            var fila = jdbc.queryForMap("SELECT estado::text AS estado, atendida_at FROM citas WHERE id = ?", cita);
            assertThat(fila).containsEntry("estado", "ATENDIDA");
            assertThat(fila.get("atendida_at")).isNotNull();
            var q = jdbc.queryForMap("SELECT presupuesto::text AS presupuesto, idioma::text AS idioma, urgencia::text AS urgencia, "
                    + "fecha_deseada, registrado_por FROM cuestionarios_cita WHERE cita_id = ?", cita);
            assertThat(q).containsEntry("presupuesto", "DE_5_A_15M").containsEntry("idioma", "ESPANOL").containsEntry("urgencia", "ALTA")
                    .containsEntry("fecha_deseada", "Antes de marzo").containsEntry("registrado_por", equipo.usuarioId());
            assertThat(avisos(cliente, "CITA_ATENDIDA")).isEqualTo(1);
            assertThat(avisos(idDe(emailAdmin), "CITA_ATENDIDA")).isEqualTo(1);
            mvc.perform(get("/api/v1/citas/" + cita + "/cuestionario").cookie(equipo.sesion()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.urgencia").value("ALTA"));
        }

        @Test
        void unCuestionarioIncompletoDa422ConUnErrorPorCampoYNoCierraLaCita() throws Exception {
            Equipo equipo = equipo();
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), equipo.id(), "CONFIRMADA", "VIRTUAL", hora(-63));

            mvc.perform(json(post("/api/v1/citas/" + cita + "/cuestionario").cookie(equipo.sesion()),
                            "{\"resumen\":\"corto\",\"descripcionCliente\":\"\",\"presupuesto\":\"\",\"idioma\":\"KLINGON\","
                                    + "\"fechaDeseada\":\"" + "x".repeat(61) + "\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[?(@.campo=='resumen')].mensaje").value("Escribe al menos 30 caracteres."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='descripcionCliente')].mensaje").value("Describe al cliente."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='presupuesto')].mensaje").value("Elige el presupuesto aproximado."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='idioma')].mensaje").value("Elige el idioma."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='urgencia')].mensaje").value("Elige el nivel de urgencia."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='fechaDeseada')]").isNotEmpty());
            assertThat(jdbc.queryForObject("SELECT estado::text FROM citas WHERE id = ?", String.class, cita)).isEqualTo("CONFIRMADA");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cuestionarios_cita WHERE cita_id = ?", Integer.class, cita)).isZero();
        }

        @Test
        void elCuestionarioAntesDeLaHoraDeLaCitaDa409() throws Exception {
            Equipo equipo = equipo();
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), equipo.id(), "CONFIRMADA", "VIRTUAL", hora(61));

            mvc.perform(json(post("/api/v1/citas/" + cita + "/cuestionario").cookie(equipo.sesion()), cuestionario()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La cita aún no ha ocurrido"));
        }

        @Test
        void sinConfirmarOCanceladaNoSeLlenaElCuestionario() throws Exception {
            Equipo equipo = equipo();
            UUID cliente = idDe(crearUsuario("CLIENTE"));
            UUID pendiente = cita(cliente, equipo.id(), "PENDIENTE", "VIRTUAL", hora(-64));
            UUID cancelada = cita(cliente, equipo.id(), "CANCELADA", "VIRTUAL", hora(-65));

            mvc.perform(json(post("/api/v1/citas/" + pendiente + "/cuestionario").cookie(equipo.sesion()), cuestionario()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Confirma la cita antes de llenar el cuestionario"));
            mvc.perform(json(post("/api/v1/citas/" + cancelada + "/cuestionario").cookie(equipo.sesion()), cuestionario()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value(MENSAJE_NO_MODIFICABLE));
        }

        @Test
        void unaCitaConCuestionarioNoSeVuelveALlenarNiSeEdita() throws Exception {
            Equipo equipo = equipo();
            UUID cita = cita(idDe(crearUsuario("CLIENTE")), equipo.id(), "CONFIRMADA", "VIRTUAL", hora(-66));
            mvc.perform(json(post("/api/v1/citas/" + cita + "/cuestionario").cookie(equipo.sesion()), cuestionario()))
                    .andExpect(status().isCreated());

            mvc.perform(json(post("/api/v1/citas/" + cita + "/cuestionario").cookie(equipo.sesion()), cuestionario()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Esta cita ya tiene su cuestionario"));
            // RNF-02: ni siquiera con SQL directo (tg_cuestionarios_inmutables)
            assertThatThrownBy(() -> jdbc.update("UPDATE cuestionarios_cita SET resumen = 'cambiado' WHERE cita_id = ?", cita))
                    .hasMessageContaining("registro inmutable");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM cuestionarios_cita WHERE cita_id = ?", cita))
                    .hasMessageContaining("registro inmutable");
        }

        @Test
        void clientesYSinSesionNoTienenEstosEndpoints() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            UUID cualquiera = UUID.randomUUID();

            mvc.perform(get("/api/v1/citas/equipo")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/citas/equipo").cookie(cliente)).andExpect(status().isForbidden());
            mvc.perform(json(patch("/api/v1/citas/" + cualquiera + "/confirmar").cookie(cliente), "{}")).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/v1/citas/" + cualquiera + "/cuestionario").cookie(cliente), "{}")).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/citas/" + cualquiera + "/cuestionario").cookie(cliente)).andExpect(status().isForbidden());
        }

        @Test
        void unProductOwnerSinEquipoNoVeCitas() throws Exception {
            Cookie sinEquipo = sesionDe(crearUsuario("PRODUCT_OWNER"));
            mvc.perform(get("/api/v1/citas/equipo").cookie(sinEquipo)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ---------------------------------------------------------------- ayudas

    /** Un equipo de prueba con su Product Owner, que ya tiene cuenta. */
    record Equipo(UUID id, UUID usuarioId, String email, GestionCitasTest prueba) {

        Cookie sesion() throws Exception {
            return prueba.sesionDe(email);
        }
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
        return new Equipo(equipo, usuario, email, this);
    }

    /**
     * Las 10:00 (hora de Colombia) de un día propio de cada llamada: de aquí a más de 60 días si {@code sentido} es positivo
     * (aún no ocurre) o de hace más de 60 días si es negativo (ya pasó). Un día distinto por llamada evita repetir la franja.
     */
    private static Instant hora(int sentido) {
        int dias = 60 + HORAS.getAndIncrement();
        return java.time.LocalDate.now(ZoneId.of("America/Bogota")).plusDays(sentido < 0 ? -dias : dias)
                .atTime(10, 0).atZone(ZoneId.of("America/Bogota")).toInstant();
    }

    private static String diaColombia(Instant instante) {
        return instante.atZone(ZoneId.of("America/Bogota")).toLocalDate().toString();
    }

    private UUID cita(UUID cliente, UUID equipo, String estado, String modalidad, Instant fecha) {
        UUID id = UUID.randomUUID();
        String enlace = estado.equals("CONFIRMADA") && modalidad.equals("VIRTUAL") ? "https://meet.google.com/abc-defg-hij" : null;
        jdbc.update("""
                INSERT INTO citas (id, codigo, cliente_id, servicio_id, equipo_id, fecha_hora, modalidad, descripcion_necesidad, estado,
                                   enlace_reunion, confirmada_at, atendida_at, cancelada_at)
                VALUES (?, fn_siguiente_codigo_cita(), ?, (SELECT id FROM servicios ORDER BY orden LIMIT 1), ?, ?, ?::modalidad, ?,
                        ?::estado_cita, ?, ?, ?, ?)
                """, id, cliente, equipo, Timestamp.from(fecha), modalidad, "Necesito una tienda en línea para vender mis productos.",
                estado, enlace,
                estado.equals("CONFIRMADA") || estado.equals("ATENDIDA") ? Timestamp.from(fecha.minus(1, ChronoUnit.DAYS)) : null,
                estado.equals("ATENDIDA") ? Timestamp.from(fecha.plus(1, ChronoUnit.HOURS)) : null,
                estado.equals("CANCELADA") ? Timestamp.from(fecha.minus(1, ChronoUnit.DAYS)) : null);
        if (estado.equals("ATENDIDA") && modalidad.equals("VIRTUAL")) {
            jdbc.update("UPDATE citas SET enlace_reunion = 'https://meet.google.com/abc-defg-hij' WHERE id = ?", id);
        }
        return id;
    }

    private int avisos(UUID usuario, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = ?", Integer.class, usuario, tipo);
    }

    private static String cuestionario() {
        return """
                {"resumen":"%s","descripcionCliente":"%s","presupuesto":"DE_5_A_15M","fechaDeseada":"Antes de marzo",
                 "idioma":"ESPANOL","urgencia":"ALTA"}
                """.formatted(RESUMEN, DESCRIPCION);
    }

    private String crearUsuario(String rol) {
        String email = "gestion-" + UUID.randomUUID().toString().substring(0, 12)
                + (rol.equals("CLIENTE") ? "@correo.com" : "@coretechsolutions.co");
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, true, now() FROM roles WHERE codigo = ?
                """, email, codificador.encode(CLAVE), rol);
        return email;
    }

    private UUID idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);
    }

    Cookie sesionDe(String email) throws Exception {
        Cookie cookie = mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s","recordarme":false}
                        """.formatted(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }
}
