package co.coretech.portal.proyectos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.servlet.http.Cookie;

/**
 * Datos de prueba para el plan de trabajo (P17): usuarios, equipos, servicios con plantilla, citas y proyectos creados
 * por la API. Cada prueba crea los suyos; las subclases repiten {@code @SpringBootTest}, {@code @AutoConfigureMockMvc},
 * {@code @ActiveProfiles("test")} y {@code @Import(ContenedoresPrueba.class)}.
 */
public abstract class BaseProyectosPrueba {

    public static final String CLAVE = "Segura2026!";
    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder codificador;

    @Autowired
    protected org.springframework.transaction.PlatformTransactionManager transacciones;

    public record Equipo(UUID id, UUID usuario, String email) {
    }

    /** Un proyecto recién creado: su código, el cliente dueño y el equipo asignado. */
    public record Proyecto(String codigo, String clienteEmail, UUID clienteId, Equipo equipo) {
    }

    public static LocalDate hoy() {
        return LocalDate.now(BOGOTA);
    }

    /** Crea un proyecto con el plan en BORRADOR (3 fases de 2 pasos; solo la primera la aprueba el cliente). */
    public Proyecto proyecto(String adminEmail) throws Exception {
        return proyecto(adminEmail, equipo());
    }

    public Proyecto proyecto(String adminEmail, Equipo equipo) throws Exception {
        String clienteEmail = crearUsuario("CLIENTE");
        UUID cliente = idDe(clienteEmail);
        UUID cita = cita(cliente, servicioConPlantilla(3), equipo.id(), "ATENDIDA");
        String cuerpo = """
                {"citaId":"%s","nombre":"Inventario del salón","alcance":"Alcance acordado en la reunión con el cliente de prueba.",
                 "fechaInicio":"%s","fechaEstimadaEntrega":"%s","equipoId":"%s"}
                """.formatted(cita, hoy(), hoy().plusDays(40), equipo.id());
        mvc.perform(json(post("/api/v1/proyectos").cookie(sesionDe(adminEmail)), cuerpo)).andExpect(status().isCreated());
        String codigo = jdbc.queryForObject("SELECT codigo FROM proyectos WHERE cita_id = ?", String.class, cita);
        return new Proyecto(codigo, clienteEmail, cliente, equipo);
    }

    /** Servicio con una plantilla de {@code fases} fases (la primera la aprueba el cliente), cada una con 2 pasos. */
    public UUID servicioConPlantilla(int fases) {
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

    public UUID cita(UUID cliente, UUID servicio, UUID equipo, String estado) {
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

    public Equipo equipo() {
        String email = crearUsuario("PRODUCT_OWNER");
        UUID usuario = idDe(email);
        UUID desarrollador = jdbc.queryForObject("""
                INSERT INTO desarrolladores (nombre, correo, celular, especialidad, usuario_id)
                VALUES ('Product Owner de Prueba', ?, '3001234567', 'FULL_STACK', ?) RETURNING id
                """, UUID.class, email, usuario);
        UUID equipo = jdbc.queryForObject("INSERT INTO equipos (max_proyectos, activo) VALUES (3, true) RETURNING id", UUID.class);
        jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, 'PRODUCT_OWNER')", equipo, desarrollador);
        return new Equipo(equipo, usuario, email);
    }

    public UUID idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);
    }

    public String crearUsuario(String rol) {
        String email = "prueba-" + UUID.randomUUID().toString().substring(0, 12)
                + (rol.equals("CLIENTE") ? "@correo.com" : "@coretechsolutions.co");
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, true, now() FROM roles WHERE codigo = ?
                """, email, codificador.encode(CLAVE), rol);
        return email;
    }

    public Cookie sesionDe(String email) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s","recordarme":false}
                        """.formatted(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
    }

    public static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    public int avisos(UUID usuario, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = ?", Integer.class, usuario, tipo);
    }
}
