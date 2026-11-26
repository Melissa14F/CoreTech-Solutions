package co.coretech.portal.comunicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.acceso.service.TareasAcceso;
import co.coretech.portal.comunicacion.service.ServicioNotificaciones;
import co.coretech.portal.comunicacion.service.TareaEnvioCorreos;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;

/**
 * HU-24, RF-39, RNF-13, RNF-09: aviso publicado → notificación guardada al confirmar → la cola manda el correo
 * (con reintentos) → la campana la muestra y la marca como leída. El servidor de correo es un JavaMailSender
 * simulado; la cola se llama a mano (en el perfil test no corre sola).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class NotificacionesTest {

    static final String CLAVE = "Segura2026!";

    @MockitoBean
    JavaMailSender servidorSmtp;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    TransactionTemplate transaccion;

    @Autowired
    ApplicationEventPublisher eventos;

    @Autowired
    TareaEnvioCorreos cola;

    @Autowired
    ServicioNotificaciones servicio;

    @Autowired
    TareasAcceso tareasAcceso;

    /** Correos que "llegaron" al servidor simulado. */
    final List<MimeMessage> enviados = new ArrayList<>();

    @BeforeEach
    void servidorQueAcepta() {
        reset(servidorSmtp);
        enviados.clear();
        when(servidorSmtp.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> enviados.add(i.getArgument(0))).when(servidorSmtp).send(any(MimeMessage.class));
    }

    // ---------------------------------------------------------------- Evento → notificación → correo

    @Nested
    class Cola {

        @Test
        void avisoConfirmadoGuardaUnaNotificacionPorDestinatarioYLaColaEnviaElCorreo() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            Cuenta andres = crearUsuario("PRODUCT_OWNER", true);

            transaccion.executeWithoutResult(t -> eventos.publishEvent(new AvisoSolicitado(List.of(laura.id(), andres.id(), laura.id()),
                    "CITA_CONFIRMADA", "Tu cita fue confirmada", "La cita C-0064 quedó para el lunes a las 9:00 a. m.",
                    "/cliente/citas")));

            Map<String, Object> fila = notificacionDe(laura.id());
            assertThat(fila).containsEntry("tipo", "CITA_CONFIRMADA").containsEntry("leida", false)
                    .containsEntry("enviada_email", false).containsEntry("intentos_envio", 0);
            assertThat(contar(andres.id())).isEqualTo(1);
            assertThat(contar(laura.id())).as("un destinatario repetido recibe un solo aviso").isEqualTo(1);

            cola.enviarPendientes();

            MimeMessage correo = correoPara(laura.email());
            assertThat(correo.getSubject()).isEqualTo("Tu cita fue confirmada");
            assertThat(correo.getFrom()[0].toString()).as("remitente de coretech.correos.remitente")
                    .isEqualTo("CoreTech Solutions <no-responder@coretech.local>");
            String contenido = contenido(correo);
            assertThat(contenido).contains("Hola, Usuario de Prueba:", "La cita C-0064 quedó para el lunes",
                    "http://localhost:5173/cliente/citas", "Ver en el portal");
            assertThat(notificacionDe(laura.id())).containsEntry("enviada_email", true).containsEntry("intentos_envio", 1);
            assertThat(correoPara(andres.email())).isNotNull();

            enviados.clear();
            cola.enviarPendientes();
            assertThat(enviados).as("lo enviado no se vuelve a enviar")
                    .noneMatch(m -> destinatario(m).equals(laura.email()));
        }

        @Test
        void siLaTransaccionSeRevierteNoSeAvisaNada() {
            Cuenta laura = crearUsuario("CLIENTE", true);

            transaccion.executeWithoutResult(t -> {
                eventos.publishEvent(AvisoSolicitado.para(laura.id(), "CITA_CANCELADA", "Cita cancelada", "No pasó.", null));
                t.setRollbackOnly();
            });

            assertThat(contar(laura.id())).isZero();
        }

        @Test
        void sinTransaccionTambienSeGuarda() {
            Cuenta laura = crearUsuario("CLIENTE", true);

            eventos.publishEvent(AvisoSolicitado.para(laura.id(), "PLAN_VENCIDO", "La propuesta venció", "Pasaron 10 días hábiles.", null));

            assertThat(contar(laura.id())).isEqualTo(1);
        }

        @Test
        void siFallaElServidorDeCorreoReintentaHastaTresVecesConEsperaCreciente() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            doAnswer(i -> {
                MimeMessage m = i.getArgument(0);
                if (destinatario(m).equals(laura.email())) {
                    throw new MailSendException("Connection refused");
                }
                return null;
            }).when(servidorSmtp).send(any(MimeMessage.class));
            eventos.publishEvent(AvisoSolicitado.para(laura.id(), "PASO_HECHO", "Nuevo avance", "Tu equipo terminó un paso.", null));

            cola.enviarPendientes();
            assertThat(notificacionDe(laura.id())).containsEntry("intentos_envio", 1)
                    .containsEntry("enviada_email", false).containsEntry("ultimo_error", "Connection refused");

            cola.enviarPendientes();
            assertThat(notificacionDe(laura.id())).as("el segundo intento espera un minuto")
                    .containsEntry("intentos_envio", 1);

            envejecer(laura.id(), "2 minutes");
            cola.enviarPendientes();
            assertThat(notificacionDe(laura.id())).containsEntry("intentos_envio", 2);

            cola.enviarPendientes();
            assertThat(notificacionDe(laura.id())).as("el tercer intento espera hasta el minuto 5")
                    .containsEntry("intentos_envio", 2);

            envejecer(laura.id(), "6 minutes");
            cola.enviarPendientes();
            assertThat(notificacionDe(laura.id())).containsEntry("intentos_envio", 3).containsEntry("enviada_email", false);

            envejecer(laura.id(), "1 hour");
            cola.enviarPendientes();
            assertThat(notificacionDe(laura.id())).as("después del tercero no se intenta más")
                    .containsEntry("intentos_envio", 3);
        }

        @Test
        void unReintentoQueFuncionaMarcaLaNotificacionComoEnviada() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            doAnswer(i -> {
                throw new MailSendException("Timeout");
            }).when(servidorSmtp).send(any(MimeMessage.class));
            eventos.publishEvent(AvisoSolicitado.para(laura.id(), "PASO_HECHO", "Nuevo avance", "Tu equipo terminó un paso.", null));
            cola.enviarPendientes();

            servidorQueAcepta();
            envejecer(laura.id(), "2 minutes");
            cola.enviarPendientes();

            assertThat(notificacionDe(laura.id())).containsEntry("enviada_email", true)
                    .containsEntry("intentos_envio", 2).containsEntry("ultimo_error", null);
            assertThat(correoPara(laura.email())).isNotNull();
        }

        @Test
        void aUnaCuentaDesactivadaNoSeLeEnviaCorreoPeroLaNotificacionQueda() {
            Cuenta inactiva = crearUsuario("CLIENTE", false);
            eventos.publishEvent(AvisoSolicitado.para(inactiva.id(), "PASO_HECHO", "Nuevo avance", "Tu equipo terminó un paso.", null));

            cola.enviarPendientes();

            assertThat(enviados).noneMatch(m -> destinatario(m).equals(inactiva.email()));
            assertThat(notificacionDe(inactiva.id())).containsEntry("intentos_envio", 3)
                    .containsEntry("enviada_email", false);
        }

        @Test
        void elTextoDelUsuarioNoSeInterpretaComoHtml() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            eventos.publishEvent(AvisoSolicitado.para(laura.id(), "NUEVO_MENSAJE", "Nuevo mensaje",
                    "Andrés escribió: <script>alert(1)</script>", null));

            cola.enviarPendientes();

            String html = parteHtml(correoPara(laura.email()));
            assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>").doesNotContain("Ver en el portal");
        }

        @Test
        void unAvisoMalArmadoFallaAlPublicarlo() {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> AvisoSolicitado.para(id, "cita confirmada", "Título", "Texto", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AvisoSolicitado.para(id, "CITA", "Título", "Texto", "https://otro-sitio.com"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AvisoSolicitado(List.of(), "CITA", "Título", "Texto", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AvisoSolicitado.para(id, "CITA", "x".repeat(81), "Texto", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** D-52: al registrarse, el cliente recibe la bienvenida en la campana y en el correo con el que se registró. */
        @Test
        void elClienteQueSeRegistraRecibeLaBienvenida() throws Exception {
            String email = "nuevo-" + UUID.randomUUID().toString().substring(0, 8) + "@empresa-cliente.com";
            mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                            {"nombre":"Sofía Duarte","email":"%s","celular":"3142208815","empresa":"","password":"%s","confirmacion":"%s","aceptaDatos":true}
                            """.formatted(email, CLAVE, CLAVE)))
                    .andExpect(status().isCreated());
            UUID id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);

            assertThat(notificacionDe(id)).containsEntry("tipo", "CUENTA_CREADA")
                    .containsEntry("titulo", "Te damos la bienvenida a CoreTech Solutions")
                    .containsEntry("enlace", "/cliente/agendar").containsEntry("leida", false);

            cola.enviarPendientes();

            MimeMessage correo = correoPara(email);
            assertThat(correo.getSubject()).isEqualTo("Te damos la bienvenida a CoreTech Solutions");
            assertThat(contenido(correo)).contains("Hola, Sofía Duarte:", "Tu cuenta de cliente quedó creada",
                    "http://localhost:5173/cliente/agendar");
        }

        @Test
        void unRegistroRechazadoNoDaLaBienvenida() throws Exception {
            String email = "rechazado-" + UUID.randomUUID().toString().substring(0, 8) + "@empresa-cliente.com";
            mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                            {"nombre":"Sofía Duarte","email":"%s","celular":"3142208815","empresa":"","password":"%s","confirmacion":"%s","aceptaDatos":false}
                            """.formatted(email, CLAVE, CLAVE)))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones n JOIN usuarios u ON u.id = n.usuario_id WHERE u.email = ?",
                    Integer.class, email)).isZero();
        }
    }

    // ---------------------------------------------------------------- Campana (API)

    @Nested
    class Campana {

        @Test
        void listaLasMiasConLasNoLeidasPrimeroYElContador() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            Cuenta otra = crearUsuario("CLIENTE", true);
            UUID vieja = insertar(laura.id(), "Vieja sin leer", false, "3 days");
            UUID leida = insertar(laura.id(), "Leída reciente", true, "1 minute");
            UUID nueva = insertar(laura.id(), "Nueva sin leer", false, "1 hour");
            insertar(otra.id(), "De otra persona", false, "1 minute");

            mvc.perform(get("/api/v1/notificaciones").cookie(sesionDe(laura.email())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.noLeidas").value(2))
                    .andExpect(jsonPath("$.notificaciones.length()").value(3))
                    .andExpect(jsonPath("$.notificaciones[0].notificacionId").value(nueva.toString()))
                    .andExpect(jsonPath("$.notificaciones[1].notificacionId").value(vieja.toString()))
                    .andExpect(jsonPath("$.notificaciones[2].notificacionId").value(leida.toString()))
                    .andExpect(jsonPath("$.notificaciones[0].titulo").value("Nueva sin leer"))
                    .andExpect(jsonPath("$.notificaciones[0].mensaje").value("Texto de prueba"))
                    .andExpect(jsonPath("$.notificaciones[0].enlace").value("/cliente/mis-citas"))
                    .andExpect(jsonPath("$.notificaciones[0].leida").value(false))
                    .andExpect(jsonPath("$.notificaciones[0].fecha").exists())
                    // Nada de los datos internos de la cola
                    .andExpect(jsonPath("$.notificaciones[0].intentosEnvio").doesNotExist())
                    .andExpect(jsonPath("$.notificaciones[0].usuarioId").doesNotExist());
        }

        @Test
        void marcarUnaComoLeida() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            UUID id = insertar(laura.id(), "Sin leer", false, "1 minute");
            Cookie sesion = sesionDe(laura.email());

            mvc.perform(patch("/api/v1/notificaciones/" + id + "/leida").cookie(sesion)).andExpect(status().isNoContent());
            // Marcarla otra vez no es un error
            mvc.perform(patch("/api/v1/notificaciones/" + id + "/leida").cookie(sesion))
                    .andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT leida FROM notificaciones WHERE id = ?", Boolean.class, id)).isTrue();
            mvc.perform(get("/api/v1/notificaciones").cookie(sesion)).andExpect(jsonPath("$.noLeidas").value(0));
        }

        @Test
        void laNotificacionDeOtroUsuarioResponde404() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            Cuenta otra = crearUsuario("PRODUCT_OWNER", true);
            UUID ajena = insertar(otra.id(), "De otra persona", false, "1 minute");

            mvc.perform(patch("/api/v1/notificaciones/" + ajena + "/leida").cookie(sesionDe(laura.email())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Notificación no encontrada"));
            mvc.perform(patch("/api/v1/notificaciones/" + UUID.randomUUID() + "/leida").cookie(sesionDe(laura.email())))
                    .andExpect(status().isNotFound());

            assertThat(jdbc.queryForObject("SELECT leida FROM notificaciones WHERE id = ?", Boolean.class, ajena)).isFalse();
        }

        @Test
        void marcarTodasSoloTocaLasMias() throws Exception {
            Cuenta laura = crearUsuario("CLIENTE", true);
            Cuenta otra = crearUsuario("CLIENTE", true);
            insertar(laura.id(), "Una", false, "1 minute");
            insertar(laura.id(), "Dos", false, "2 minutes");
            UUID ajena = insertar(otra.id(), "Ajena", false, "1 minute");

            mvc.perform(patch("/api/v1/notificaciones/leidas").cookie(sesionDe(laura.email()))).andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND NOT leida",
                    Integer.class, laura.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT leida FROM notificaciones WHERE id = ?", Boolean.class, ajena)).isFalse();
        }

        @Test
        void sinSesionResponde401() throws Exception {
            mvc.perform(get("/api/v1/notificaciones")).andExpect(status().isUnauthorized());
            mvc.perform(patch("/api/v1/notificaciones/leidas")).andExpect(status().isUnauthorized());
        }

        @Test
        void losTresRolesTienenCampana() throws Exception {
            for (String rol : List.of("CLIENTE", "PRODUCT_OWNER", "ADMINISTRADOR")) {
                Cuenta cuenta = crearUsuario(rol, true);
                mvc.perform(get("/api/v1/notificaciones").cookie(sesionDe(cuenta.email())))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.noLeidas").value(0));
            }
        }
    }

    // ---------------------------------------------------------------- Conservación de datos (RNF-09)

    @Nested
    class Conservacion {

        @Test
        void borraLasLeidasConMasDeSeisMesesYConservaLasDemas() {
            Cuenta laura = crearUsuario("CLIENTE", true);
            UUID leidaVieja = insertar(laura.id(), "Leída vieja", true, "7 months");
            UUID sinLeerVieja = insertar(laura.id(), "Sin leer vieja", false, "7 months");
            UUID leidaReciente = insertar(laura.id(), "Leída de hace 5 meses", true, "5 months");

            assertThat(servicio.borrarLeidasAntiguas()).isPositive();

            List<UUID> quedan = jdbc.queryForList("SELECT id FROM notificaciones WHERE usuario_id = ?", UUID.class, laura.id());
            assertThat(quedan).containsExactlyInAnyOrder(sinLeerVieja, leidaReciente).doesNotContain(leidaVieja);
        }

        @Test
        void borraLosEnlacesDeRecuperacionUsadosOVencidos() {
            Cuenta laura = crearUsuario("CLIENTE", true);
            UUID vigente = token(laura.id(), "now() + interval '20 minutes'", null);
            UUID vencido = token(laura.id(), "now() - interval '1 minute'", null);
            UUID usado = token(laura.id(), "now() + interval '20 minutes'", "now()");

            assertThat(tareasAcceso.borrarEnlacesRecuperacionVencidos()).isGreaterThanOrEqualTo(2);

            List<UUID> quedan = jdbc.queryForList("SELECT id FROM tokens_recuperacion WHERE usuario_id = ?", UUID.class, laura.id());
            assertThat(quedan).containsExactly(vigente).doesNotContain(vencido, usado);
        }
    }

    // ---------------------------------------------------------------- Ayudas

    record Cuenta(UUID id, String email) {
    }

    private Cuenta crearUsuario(String rol, boolean activo) {
        UUID id = UUID.randomUUID();
        String email = "prueba-" + id.toString().substring(0, 12)
                + (rol.equals("CLIENTE") ? "@correo.com" : "@coretechsolutions.co");
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT ?, id, 'Usuario de Prueba', ?, '3001234567', ?, ?, now() FROM roles WHERE codigo = ?
                """, id, email, codificador.encode(CLAVE), activo, rol);
        return new Cuenta(id, email);
    }

    private UUID insertar(UUID usuarioId, String titulo, boolean leida, String antiguedad) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notificaciones (id, usuario_id, tipo, titulo, mensaje, enlace, leida, enviada_email, created_at)
                VALUES (?, ?, 'PRUEBA', ?, 'Texto de prueba', '/cliente/mis-citas', ?, true, now() - CAST(? AS interval))
                """, id, usuarioId, titulo, leida, antiguedad);
        return id;
    }

    private UUID token(UUID usuarioId, String expira, String usado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tokens_recuperacion (id, usuario_id, token_hash, expira_at, usado_at) VALUES (?, ?, ?, "
                + expira + ", " + (usado == null ? "NULL" : usado) + ")", id, usuarioId, UUID.randomUUID().toString());
        return id;
    }

    /** La única notificación del usuario, con intentos_envio como int. */
    private Map<String, Object> notificacionDe(UUID usuarioId) {
        Map<String, Object> fila = new java.util.HashMap<>(
                jdbc.queryForMap("SELECT * FROM notificaciones WHERE usuario_id = ?", usuarioId));
        fila.put("intentos_envio", ((Number) fila.get("intentos_envio")).intValue());
        return fila;
    }

    private int contar(UUID usuarioId) {
        return jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ?", Integer.class, usuarioId);
    }

    /** Simula que la notificación se creó antes (la espera entre intentos se cuenta desde la creación). */
    private void envejecer(UUID usuarioId, String cuanto) {
        jdbc.update("UPDATE notificaciones SET created_at = created_at - CAST(? AS interval) WHERE usuario_id = ?", cuanto, usuarioId);
    }

    private MimeMessage correoPara(String email) {
        verify(servidorSmtp, atLeastOnce()).send(any(MimeMessage.class));
        return enviados.stream().filter(m -> destinatario(m).equals(email)).findFirst()
                .orElseThrow(() -> new AssertionError("No se envió correo a " + email));
    }

    private static String destinatario(MimeMessage m) {
        try {
            return m.getRecipients(Message.RecipientType.TO)[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Todo el correo (texto y HTML) ya decodificado. */
    private static String contenido(MimeMessage m) throws Exception {
        return partes(copia(m), null);
    }

    /** Solo la versión HTML del correo. */
    private static String parteHtml(MimeMessage m) throws Exception {
        return partes(copia(m), "text/html");
    }

    /** El correo tal como sale por la red, leído de nuevo (así se decodifica igual que en el buzón). */
    private static MimeMessage copia(MimeMessage m) throws Exception {
        m.saveChanges();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        m.writeTo(salida);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(salida.toByteArray()));
    }

    private static String partes(Part parte, String tipo) throws Exception {
        Object contenido = parte.getContent();
        if (contenido instanceof Multipart multi) {
            StringBuilder todo = new StringBuilder();
            for (int i = 0; i < multi.getCount(); i++) {
                todo.append(partes(multi.getBodyPart(i), tipo));
            }
            return todo.toString();
        }
        return tipo == null || parte.isMimeType(tipo) ? contenido + "\n" : "";
    }

    private Cookie sesionDe(String email) throws Exception {
        Cookie cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","recordarme":false}
                        """.formatted(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
