package co.coretech.portal.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

import com.jayway.jsonpath.JsonPath;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/**
 * Agendar, consultar y cancelar citas contra PostgreSQL 15 (HU-01, HU-08, RN-01 a RN-03, RN-11, RN-23): una prueba
 * por cada fila de "Excepciones" de las dos fichas, más permisos (401 sin sesión, 403 sin el permiso) y la carrera
 * de dos clientes por la misma franja. Cada prueba toma su propia franja libre de los próximos 30 días, para no
 * depender del orden de las demás. Las pruebas del reloj fijo y de los bordes están en {@link DisponibilidadTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class CitasTest {

    static final String CLAVE = "Segura2026!";
    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final String DESCRIPCION = "Necesito una tienda en línea para vender mis productos.";
    static final String MENSAJE_RN01 =
            "Las citas se agendan de lunes a viernes, con mínimo 24 horas y máximo 30 días de anticipación";

    /** Franjas válidas de los próximos días (de lunes a viernes, 8–12 y 14–18), cada prueba usa una distinta. */
    static final List<Instant> FRANJAS = franjasLibres();
    static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    private static List<Instant> franjasLibres() {
        List<Instant> lista = new ArrayList<>();
        LocalDate hoy = LocalDate.now(COLOMBIA);
        for (int d = 3; d <= 28; d++) {
            LocalDate dia = hoy.plusDays(d);
            if (dia.getDayOfWeek().getValue() > 5) {
                continue;
            }
            for (int hora : new int[] { 8, 9, 10, 11, 14, 15, 16, 17 }) {
                lista.add(dia.atTime(hora, 0).atZone(COLOMBIA).toInstant());
            }
        }
        return lista;
    }

    private static Instant franja() {
        return FRANJAS.get(SIGUIENTE.getAndIncrement());
    }

    private static String iso(Instant instante, String zona) {
        return instante.atZone(ZoneId.of(zona)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // ---------------------------------------------------------------- HU-01 · Agendar

    @Nested
    class Agendar {

        @Test
        void creaLaCitaPendienteConSuCodigoLaGuardaEnUtcLaAuditaYAvisaAlCliente() throws Exception {
            String email = crearUsuario("CLIENTE");
            Cookie cliente = sesionDe(email);
            Instant hora = franja();

            var resultado = mvc.perform(json(post("/api/v1/citas").cookie(cliente),
                            cuerpo(servicioVisible(), iso(hora, "Europe/Madrid"), "VIRTUAL", DESCRIPCION, "España", "Europe/Madrid")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.codigo", matchesPattern("C-\\d{4,}")))
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.fechaHoraLocal").value(org.hamcrest.Matchers.endsWith("+01:00")))
                    .andExpect(jsonPath("$.fechaHoraColombia").value(org.hamcrest.Matchers.endsWith("-05:00")))
                    .andReturn();
            String codigo = JsonPath.read(resultado.getResponse().getContentAsString(), "$.codigo");

            var fila = jdbc.queryForMap("SELECT fecha_hora, pais, zona_horaria, estado::text AS estado, descripcion_necesidad FROM citas WHERE codigo = ?", codigo);
            assertThat(((java.sql.Timestamp) fila.get("fecha_hora")).toInstant()).isEqualTo(hora);
            assertThat(fila).containsEntry("pais", "España").containsEntry("zona_horaria", "Europe/Madrid").containsEntry("estado", "PENDIENTE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE categoria = 'citas' AND accion = 'Agendó una cita' AND entidad_id = ?",
                    Integer.class, codigo)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM notificaciones n JOIN usuarios u ON u.id = n.usuario_id
                     WHERE u.email = ? AND n.tipo = 'CITA_REGISTRADA'
                    """, Integer.class, email)).isEqualTo(1);
        }

        @Test
        void laFranjaTomadaPorOtroClienteDa409() throws Exception {
            Instant hora = franja();
            confirmar(sesionDe(crearUsuario("CLIENTE")), hora).andExpect(status().isCreated());

            confirmar(sesionDe(crearUsuario("CLIENTE")), hora)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].campo").value("fechaHora"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La hora que elegiste ya no está disponible, elige otra"));
        }

        @Test
        void siElClienteYaTieneUnaCitaAEsaHoraDa409() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            Instant hora = franja();
            confirmar(cliente, hora).andExpect(status().isCreated());

            confirmar(cliente, hora)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ya tienes una cita"));
        }

        @Test
        void unaCitaCanceladaLiberaLaFranjaParaOtroCliente() throws Exception {
            Cookie primero = sesionDe(crearUsuario("CLIENTE"));
            Instant hora = franja();
            String citaId = JsonPath.read(confirmar(primero, hora).andExpect(status().isCreated()).andReturn()
                    .getResponse().getContentAsString(), "$.citaId");
            mvc.perform(patch("/api/v1/citas/" + citaId + "/cancelar").cookie(primero)).andExpect(status().isOk());

            confirmar(sesionDe(crearUsuario("CLIENTE")), hora).andExpect(status().isCreated());
        }

        @Test
        void unSabadoUnFestivoMenosDe24HorasOMasDe30DiasDan422ConElMensajeDeRn01() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            LocalDate hoy = LocalDate.now(COLOMBIA);
            LocalDate sabado = hoy.plusDays(5);
            while (sabado.getDayOfWeek().getValue() != 6) {
                sabado = sabado.plusDays(1);
            }
            Instant ahora = Instant.now();
            List<Instant> invalidas = List.of(
                    sabado.atTime(9, 0).atZone(COLOMBIA).toInstant(),
                    ahora.plusSeconds(3600 * 2).truncatedTo(java.time.temporal.ChronoUnit.HOURS),
                    hoy.plusDays(31).atTime(9, 0).atZone(COLOMBIA).toInstant(),
                    hoy.plusDays(60).atTime(9, 0).atZone(COLOMBIA).toInstant());
            for (Instant hora : invalidas) {
                confirmar(cliente, hora)
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[0].campo").value("fechaHora"))
                        .andExpect(jsonPath("$.errores[0].mensaje").value(MENSAJE_RN01));
            }
        }

        @Test
        void aLasDoceYALasDieciochoNoHayFranja() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            LocalDate dia = FRANJAS.get(0).atZone(COLOMBIA).toLocalDate();
            for (int hora : new int[] { 12, 13, 18, 7 }) {
                confirmar(cliente, dia.atTime(hora, 0).atZone(COLOMBIA).toInstant())
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[0].mensaje").value(MENSAJE_RN01));
            }
        }

        @Test
        void unaDescripcionCortaDa422EnElCampo() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(json(post("/api/v1/citas").cookie(cliente),
                            cuerpo(servicioVisible(), iso(franja(), "America/Bogota"), "VIRTUAL", "  Muy corta  ", "Colombia", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("descripcionNecesidad"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Cuéntanos un poco más (mínimo 20 caracteres)"));
        }

        @Test
        void laDescripcionSeGuardaSinEspaciosAlInicioNiAlFinal() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            String codigo = JsonPath.read(mvc.perform(json(post("/api/v1/citas").cookie(cliente),
                            cuerpo(servicioVisible(), iso(franja(), "America/Bogota"), "PRESENCIAL", "   " + DESCRIPCION + "   ", "Colombia", null)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.codigo");
            assertThat(jdbc.queryForObject("SELECT descripcion_necesidad FROM citas WHERE codigo = ?", String.class, codigo))
                    .isEqualTo(DESCRIPCION);
        }

        @Test
        void faltanLaModalidadElPaisOLaZonaDeUnPaisConVarias() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            String hora = iso(franja(), "America/Bogota");
            mvc.perform(json(post("/api/v1/citas").cookie(cliente), cuerpo(servicioVisible(), hora, null, DESCRIPCION, "Colombia", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("modalidad"));
            mvc.perform(json(post("/api/v1/citas").cookie(cliente), cuerpo(servicioVisible(), hora, "VIRTUAL", DESCRIPCION, "Narnia", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("pais"));
            mvc.perform(json(post("/api/v1/citas").cookie(cliente), cuerpo(servicioVisible(), hora, "VIRTUAL", DESCRIPCION, "Estados Unidos", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("zonaHoraria"));
            mvc.perform(json(post("/api/v1/citas").cookie(cliente), cuerpo(servicioVisible(), hora, "VIRTUAL", DESCRIPCION, "España", "America/Bogota")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("zonaHoraria"));
        }

        @Test
        void unServicioOcultoOInexistenteDa404() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            String hora = iso(franja(), "America/Bogota");
            for (String servicio : List.of(servicioOculto(), UUID.randomUUID().toString())) {
                mvc.perform(json(post("/api/v1/citas").cookie(cliente), cuerpo(servicio, hora, "VIRTUAL", DESCRIPCION, "Colombia", null)))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.errores[0].mensaje").value("Este servicio ya no está disponible"));
            }
        }

        @Test
        void sinSesionDa401YUnDesarrolladorSinElPermisoDa403() throws Exception {
            String cuerpo = cuerpo(servicioVisible(), iso(franja(), "America/Bogota"), "VIRTUAL", DESCRIPCION, "Colombia", null);
            mvc.perform(json(post("/api/v1/citas"), cuerpo)).andExpect(status().isUnauthorized());
            mvc.perform(json(post("/api/v1/citas").cookie(sesionDe(crearUsuario("PRODUCT_OWNER"))), cuerpo))
                    .andExpect(status().isForbidden());
        }

        @Test
        void dosClientesQueConfirmanLaMismaFranjaAlTiempoObtienenUno201YElOtro409() throws Exception {
            Cookie a = sesionDe(crearUsuario("CLIENTE"));
            Cookie b = sesionDe(crearUsuario("CLIENTE"));
            Instant hora = franja();
            CyclicBarrier salida = new CyclicBarrier(2);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> resultados = new ArrayList<>();
                for (Cookie cookie : List.of(a, b)) {
                    resultados.add(hilos.submit(() -> {
                        salida.await();
                        return confirmar(cookie, hora).andReturn().getResponse().getStatus();
                    }));
                }
                List<Integer> estados = new ArrayList<>();
                for (Future<Integer> r : resultados) {
                    estados.add(r.get());
                }
                assertThat(estados).containsExactlyInAnyOrder(201, 409);
            } finally {
                hilos.shutdownNow();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE fecha_hora = ? AND estado IN ('PENDIENTE','CONFIRMADA')",
                    Integer.class, java.sql.Timestamp.from(hora))).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- HU-01 · Disponibilidad

    @Nested
    class Disponibilidad {

        @Test
        void devuelveLasFranjasDelMesEnLaZonaDelCliente() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            YearMonth mes = YearMonth.from(FRANJAS.get(0).atZone(COLOMBIA));
            mvc.perform(get("/api/v1/citas/disponibilidad").cookie(cliente).param("servicioId", servicioVisible())
                            .param("mes", mes.toString()).param("zona", "Europe/Madrid"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].fecha").exists())
                    .andExpect(jsonPath("$[0].hora").exists())
                    .andExpect(jsonPath("$[0].horaColombia").exists())
                    .andExpect(jsonPath("$[*].estado", hasItem("LIBRE")));
        }

        @Test
        void unaFranjaTomadaApareceOcupadaYSoloAlDuenoSeLeMarcaTuya() throws Exception {
            Cookie duena = sesionDe(crearUsuario("CLIENTE"));
            Cookie otro = sesionDe(crearUsuario("CLIENTE"));
            Instant hora = franja();
            confirmar(duena, hora).andExpect(status().isCreated());
            String mes = YearMonth.from(hora.atZone(COLOMBIA)).toString();
            String local = iso(hora, "America/Bogota");

            for (Object[] caso : new Object[][] { { duena, true }, { otro, false } }) {
                mvc.perform(get("/api/v1/citas/disponibilidad").cookie((Cookie) caso[0]).param("servicioId", servicioVisible())
                                .param("mes", mes).param("zona", "America/Bogota"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[?(@.fechaHora=='" + local + "')].estado").value(hasItem("OCUPADO")))
                        .andExpect(jsonPath("$[?(@.fechaHora=='" + local + "')].tuya").value(hasItem((Boolean) caso[1])));
            }
        }

        @Test
        void unMesUnaZonaOUnServicioInvalidosDan422O404() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(get("/api/v1/citas/disponibilidad").cookie(cliente).param("servicioId", servicioVisible())
                            .param("mes", "noviembre").param("zona", "America/Bogota"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("mes"));
            mvc.perform(get("/api/v1/citas/disponibilidad").cookie(cliente).param("servicioId", servicioVisible())
                            .param("mes", "2031-03").param("zona", "Mi/Casa"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("zona"));
            mvc.perform(get("/api/v1/citas/disponibilidad").cookie(cliente).param("servicioId", servicioOculto())
                            .param("mes", "2031-03").param("zona", "America/Bogota"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void sinSesionDa401YSinElPermisoDa403() throws Exception {
            mvc.perform(get("/api/v1/citas/disponibilidad").param("servicioId", servicioVisible()).param("mes", "2031-03")
                    .param("zona", "America/Bogota")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/citas/disponibilidad").cookie(sesionDe(crearUsuario("PRODUCT_OWNER")))
                    .param("servicioId", servicioVisible()).param("mes", "2031-03").param("zona", "America/Bogota"))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- HU-08 · Mis citas y cancelar

    @Nested
    class MisCitas {

        @Test
        void cadaClienteVeSoloSusCitasConLasDosHoras() throws Exception {
            Cookie ana = sesionDe(crearUsuario("CLIENTE"));
            Cookie luis = sesionDe(crearUsuario("CLIENTE"));
            confirmar(ana, franja()).andExpect(status().isCreated());
            confirmar(luis, franja()).andExpect(status().isCreated());
            confirmar(luis, franja()).andExpect(status().isCreated());

            mvc.perform(get("/api/v1/citas").cookie(ana))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$[0].puedeCancelar").value(true))
                    .andExpect(jsonPath("$[0].servicio").isNotEmpty())
                    .andExpect(jsonPath("$[0].fechaHoraLocal").exists())
                    .andExpect(jsonPath("$[0].fechaHoraColombia").exists());
            mvc.perform(get("/api/v1/citas").cookie(luis)).andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void elClientePuedeCancelarUnaCitaPendienteYQuedaAuditado() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            String citaId = agendarUna(cliente);

            mvc.perform(patch("/api/v1/citas/" + citaId + "/cancelar").cookie(cliente)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Ya no necesito la reunión\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.canceladaAt").exists());
            assertThat(jdbc.queryForObject("SELECT motivo_cancelacion FROM citas WHERE id = ?::uuid", String.class, citaId))
                    .isEqualTo("Ya no necesito la reunión");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE categoria = 'citas' AND accion = 'Canceló su cita'",
                    Integer.class)).isPositive();
            mvc.perform(get("/api/v1/citas").cookie(cliente)).andExpect(jsonPath("$[0].puedeCancelar").value(false));
        }

        @Test
        void sePuedeCancelarSinCuerpoNiMotivo() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(patch("/api/v1/citas/" + agendarUna(cliente) + "/cancelar").cookie(cliente))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        }

        @Test
        void unMotivoDeMenosDe15OMasDe300CaracteresDa422() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            String citaId = agendarUna(cliente);
            for (String motivo : List.of("Muy corto", "x".repeat(301))) {
                mvc.perform(patch("/api/v1/citas/" + citaId + "/cancelar").cookie(cliente)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + motivo + "\"}"))
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
            }
            mvc.perform(get("/api/v1/citas").cookie(cliente)).andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
        }

        @Test
        void unaCitaYaConfirmadaDa409ConElMensajeDeLaFicha() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            String citaId = agendarUna(cliente);
            jdbc.update("UPDATE citas SET estado = 'CONFIRMADA', confirmada_at = now(), enlace_reunion = 'https://meet.example.com/abc' WHERE id = ?::uuid", citaId);

            mvc.perform(patch("/api/v1/citas/" + citaId + "/cancelar").cookie(cliente))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Esta cita ya fue confirmada, escríbenos para reprogramarla"));
            mvc.perform(get("/api/v1/citas").cookie(cliente)).andExpect(jsonPath("$[0].puedeCancelar").value(false));
        }

        @Test
        void unaCitaYaCanceladaDa409() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            String citaId = agendarUna(cliente);
            mvc.perform(patch("/api/v1/citas/" + citaId + "/cancelar").cookie(cliente)).andExpect(status().isOk());
            mvc.perform(patch("/api/v1/citas/" + citaId + "/cancelar").cookie(cliente)).andExpect(status().isConflict());
        }

        @Test
        void unaCitaAjenaOInexistenteDa404() throws Exception {
            Cookie duena = sesionDe(crearUsuario("CLIENTE"));
            Cookie otro = sesionDe(crearUsuario("CLIENTE"));
            String citaId = agendarUna(duena);

            mvc.perform(patch("/api/v1/citas/" + citaId + "/cancelar").cookie(otro)).andExpect(status().isNotFound());
            mvc.perform(patch("/api/v1/citas/" + UUID.randomUUID() + "/cancelar").cookie(otro)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/citas").cookie(duena)).andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
        }

        @Test
        void sinSesionDa401YElDesarrolladorNoVeCitasDeClientes() throws Exception {
            mvc.perform(get("/api/v1/citas")).andExpect(status().isUnauthorized());
            mvc.perform(patch("/api/v1/citas/" + UUID.randomUUID() + "/cancelar")).andExpect(status().isUnauthorized());
            Cookie po = sesionDe(crearUsuario("PRODUCT_OWNER"));
            mvc.perform(get("/api/v1/citas").cookie(po)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/citas/" + UUID.randomUUID() + "/cancelar").cookie(po)).andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- ayudas

    private org.springframework.test.web.servlet.ResultActions confirmar(Cookie cliente, Instant hora) throws Exception {
        return mvc.perform(json(post("/api/v1/citas").cookie(cliente),
                cuerpo(servicioVisible(), iso(hora, "America/Bogota"), "VIRTUAL", DESCRIPCION, "Colombia", null)));
    }

    private String agendarUna(Cookie cliente) throws Exception {
        return JsonPath.read(confirmar(cliente, franja()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.citaId");
    }

    private static String cuerpo(String servicioId, String fechaHora, String modalidad, String descripcion, String pais, String zona) {
        return """
                {"servicioId":%s,"fechaHora":%s,"modalidad":%s,"descripcionNecesidad":%s,"pais":%s,"zonaHoraria":%s}
                """.formatted(texto(servicioId), texto(fechaHora), texto(modalidad), texto(descripcion), texto(pais), texto(zona));
    }

    private static String texto(String valor) {
        return valor == null ? "null" : "\"" + valor.replace("\"", "\\\"") + "\"";
    }

    private String servicioVisible() {
        return jdbc.queryForObject("SELECT id::text FROM servicios WHERE visible ORDER BY orden LIMIT 1", String.class);
    }

    private String servicioOculto() {
        String nombre = "Oculto " + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                INSERT INTO servicios (nombre, slug, descripcion_corta, imagen_url, imagen_alt, orden, visible)
                VALUES (?, ?, 'Servicio oculto de prueba', 'img/servicios/app-movil.webp', 'Imagen de prueba',
                        (SELECT max(orden) + 1 FROM servicios), false)
                RETURNING id::text
                """, String.class, nombre, nombre.toLowerCase().replace(' ', '-'));
    }

    private String crearUsuario(String rol) {
        String email = "cita-" + UUID.randomUUID().toString().substring(0, 12)
                + (rol.equals("CLIENTE") ? "@correo.com" : "@coretechsolutions.co");
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, true, now() FROM roles WHERE codigo = ?
                """, email, codificador.encode(CLAVE), rol);
        return email;
    }

    private Cookie sesionDe(String email) throws Exception {
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
