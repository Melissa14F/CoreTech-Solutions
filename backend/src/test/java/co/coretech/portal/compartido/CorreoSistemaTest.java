package co.coretech.portal.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.compartido.service.ClienteGoogle;
import co.coretech.portal.compartido.service.ClienteGoogle.PermisoRetiradoException;
import co.coretech.portal.compartido.service.ClienteGoogle.TokenAcceso;
import co.coretech.portal.compartido.service.ClienteGoogle.Tokens;
import jakarta.servlet.http.Cookie;

/**
 * D-51: "Conectar con Gmail" de punta a punta con Google simulado (nunca sale a internet): conectar, enviar por
 * Gmail, cancelar, permiso incompleto, permiso retirado, desconectar y permisos de la pantalla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class CorreoSistemaTest {

    static final String CLAVE = "Segura2026!";
    static final String CUENTA = "helix.jmdev2026@gmail.com";
    static final String PERMISO_SECRETO = "1//permiso-de-google-que-nunca-se-guarda-en-claro";

    @MockitoBean
    ClienteGoogle google;

    @MockitoBean
    JavaMailSender smtp;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    /** El "estado" que el portal mandó a Google en la última conexión. */
    final AtomicReference<String> estadoEnviado = new AtomicReference<>();

    @BeforeEach
    void googleSimulado() {
        reset(google);
        when(google.urlAutorizacion(anyString(), anyString(), anyString())).thenAnswer(i -> {
            estadoEnviado.set(i.getArgument(1));
            return "https://accounts.google.com/o/oauth2/v2/auth?state=" + i.getArgument(1);
        });
        // expira en 0 s: cada envío renueva el token, así se puede probar el permiso retirado
        when(google.intercambiar(eq("codigo-bueno"), anyString(), anyString()))
                .thenReturn(new Tokens(PERMISO_SECRETO, "acceso-1", 0, "openid email " + ClienteGoogle.ALCANCE_ENVIAR));
        when(google.correoDe("acceso-1")).thenReturn(CUENTA);
        when(google.refrescar(PERMISO_SECRETO)).thenReturn(new TokenAcceso("acceso-2", 3600));
    }

    @AfterEach
    void sinConexion() {
        jdbc.update("DELETE FROM correo_sistema");
    }

    @Test
    void conectarGuardaElPermisoCifradoYLosCorreosSalenPorGmail() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        mvc.perform(get("/api/v1/correo-sistema").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(true))
                .andExpect(jsonPath("$.conectado").value(false));

        conectar(admin, "codigo-bueno")
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/admin/correo?resultado=conectado"));

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM correo_sistema");
        assertThat(fila).containsEntry("cuenta", CUENTA).containsEntry("estado", "CONECTADO");
        assertThat((String) fila.get("token_cifrado")).as("el permiso nunca queda en claro")
                .isNotBlank().doesNotContain("permiso-de-google");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auditoria WHERE accion = 'Conectó el correo del sistema con Gmail'
                  AND cambios::text LIKE ?
                """, Integer.class, "%" + CUENTA + "%")).isEqualTo(1);

        mvc.perform(get("/api/v1/correo-sistema").cookie(admin))
                .andExpect(jsonPath("$.conectado").value(true))
                .andExpect(jsonPath("$.cuenta").value(CUENTA))
                .andExpect(jsonPath("$.conectadoEn").exists());

        // "Enviar correo de prueba": sale por Gmail, con la cuenta conectada como remitente
        mvc.perform(post("/api/v1/correo-sistema/prueba").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"para\":\"" + CUENTA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("GMAIL"))
                .andExpect(jsonPath("$.mensaje").value("Correo enviado. Revisa la bandeja de entrada de " + CUENTA + "."));
        ArgumentCaptor<byte[]> mime = ArgumentCaptor.forClass(byte[].class);
        verify(google).enviar(eq("acceso-2"), mime.capture());
        String crudo = new String(mime.getValue(), StandardCharsets.UTF_8);
        assertThat(crudo).contains("From: CoreTech Solutions <" + CUENTA + ">", "To: " + CUENTA);
        verify(smtp, never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    @Test
    void siGoogleRetiraElPermisoQuedaRevocadoYLaPantallaLoDice() throws Exception {
        String correoAdmin = crearUsuario("ADMINISTRADOR");
        Cookie admin = sesionDe(correoAdmin);
        conectar(admin, "codigo-bueno").andExpect(status().isFound());
        when(google.refrescar(PERMISO_SECRETO)).thenThrow(new PermisoRetiradoException("Google retiró el permiso para enviar correos: vuelve a conectar Gmail"));

        mvc.perform(post("/api/v1/correo-sistema/prueba").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"para\":\"" + CUENTA + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value(org.hamcrest.Matchers.containsString("vuelve a conectar Gmail")));

        mvc.perform(get("/api/v1/correo-sistema").cookie(admin))
                .andExpect(jsonPath("$.conectado").value(false))
                .andExpect(jsonPath("$.estado").value("REVOCADO"))
                .andExpect(jsonPath("$.ultimoError").value(org.hamcrest.Matchers.containsString("retiró el permiso")));

        // Y la administradora se entera sola, en la campana, con el enlace para reconectar
        var aviso = jdbc.queryForMap("""
                SELECT n.titulo, n.mensaje, n.enlace FROM notificaciones n JOIN usuarios u ON u.id = n.usuario_id
                WHERE u.email = ? AND n.tipo = 'CORREO_DESCONECTADO'
                """, correoAdmin);
        assertThat(aviso).containsEntry("titulo", "Hay que reconectar el correo del sistema").containsEntry("enlace", "/admin/correo");
        assertThat((String) aviso.get("mensaje")).contains(CUENTA, "Volver a conectar");
        // Una sola vez: el segundo intento ya no encuentra una cuenta conectada
        mvc.perform(post("/api/v1/correo-sistema/prueba").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"para\":\"" + CUENTA + "\"}"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notificaciones n JOIN usuarios u ON u.id = n.usuario_id
                WHERE u.email = ? AND n.tipo = 'CORREO_DESCONECTADO'
                """, Integer.class, correoAdmin)).isEqualTo(1);
    }

    @Test
    void cancelarEnGoogleOUsarDosVecesElEstadoNoConecta() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        String estado = iniciar(admin);

        mvc.perform(get("/api/v1/correo-sistema/gmail/callback").param("state", estado).param("error", "access_denied"))
                .andExpect(header().string("Location", "http://localhost:5173/admin/correo?resultado=cancelado"));
        mvc.perform(get("/api/v1/correo-sistema/gmail/callback").param("state", estado).param("code", "codigo-bueno"))
                .andExpect(header().string("Location", "http://localhost:5173/admin/correo?resultado=vencido"));
        mvc.perform(get("/api/v1/correo-sistema/gmail/callback").param("state", "inventado").param("code", "codigo-bueno"))
                .andExpect(header().string("Location", "http://localhost:5173/admin/correo?resultado=vencido"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM correo_sistema", Integer.class)).isZero();
        verify(google, never()).intercambiar(anyString(), anyString(), anyString());
    }

    @Test
    void sinElPermisoDeEnviarNoConectaYLoDevuelveAGoogle() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        when(google.intercambiar(eq("codigo-sin-envio"), anyString(), anyString()))
                .thenReturn(new Tokens("1//permiso-incompleto", "acceso-x", 3600, "openid email"));

        conectar(admin, "codigo-sin-envio")
                .andExpect(header().string("Location", "http://localhost:5173/admin/correo?resultado=sin_permiso_envio"));

        verify(google).revocar("1//permiso-incompleto");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM correo_sistema", Integer.class)).isZero();
    }

    @Test
    void desconectarRetiraElPermisoEnGoogleYLoBorra() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        conectar(admin, "codigo-bueno").andExpect(status().isFound());

        mvc.perform(delete("/api/v1/correo-sistema/gmail").cookie(admin)).andExpect(status().isNoContent());

        verify(google).revocar(PERMISO_SECRETO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM correo_sistema", Integer.class)).isZero();
        mvc.perform(delete("/api/v1/correo-sistema/gmail").cookie(admin)).andExpect(status().isConflict());
    }

    @Test
    void sinGmailLaPruebaSaleAlBuzonDeDesarrolloYValidaElCorreo() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        when(smtp.createMimeMessage()).thenAnswer(i -> new jakarta.mail.internet.MimeMessage(
                jakarta.mail.Session.getInstance(new java.util.Properties())));

        mvc.perform(post("/api/v1/correo-sistema/prueba").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"para\":\"" + CUENTA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("SMTP"));
        mvc.perform(post("/api/v1/correo-sistema/prueba").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"para\":\"no-es-correo\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("para"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe un correo válido, por ejemplo nombre@empresa.com."));
        verify(google, never()).enviar(anyString(), any());
    }

    @Test
    void soloElAdministradorLaUsaYElRegresoDeGoogleEsPublico() throws Exception {
        for (String rol : new String[] { "CLIENTE", "PRODUCT_OWNER" }) {
            Cookie otro = sesionDe(crearUsuario(rol));
            mvc.perform(get("/api/v1/correo-sistema").cookie(otro)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/correo-sistema/gmail/conexion").cookie(otro)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/correo-sistema/prueba").cookie(otro).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"para\":\"" + CUENTA + "\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/correo-sistema")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/correo-sistema/gmail/callback").param("state", "x").param("code", "y"))
                .andExpect(status().isFound());
    }

    // ---------------------------------------------------------------- Ayudas

    private String iniciar(Cookie admin) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/correo-sistema/gmail/conexion").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("https://accounts.google.com/")))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(respuesta, "$.url")).endsWith(estadoEnviado.get());
        return estadoEnviado.get();
    }

    private org.springframework.test.web.servlet.ResultActions conectar(Cookie admin, String codigo) throws Exception {
        String estado = iniciar(admin);
        // Google devuelve al navegador sin la cookie de sesión (SameSite=Strict)
        return mvc.perform(get("/api/v1/correo-sistema/gmail/callback").param("state", estado).param("code", codigo));
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
        Cookie cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s","recordarme":false}
                        """.formatted(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
        assertThat(cookie).isNotNull();
        return cookie;
    }

}
