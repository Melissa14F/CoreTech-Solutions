package co.coretech.portal.compartido.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.compartido.service.CorreoSaliente;

/**
 * RNF-01 y RNF-10 con la configuración de producción donde importa: cookie {@code Secure} activada (en las demás
 * pruebas va apagada porque no hay HTTPS) y registros del log en DEBUG, el nivel que más escribe.
 */
@SpringBootTest(properties = {
        "coretech.sesion.cookie-segura=true",
        "logging.level.co.coretech=DEBUG",
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.org.springframework.web=DEBUG"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@ExtendWith(OutputCaptureExtension.class)
class SeguridadTransversalTest {

    private static final String CLAVE_CORRECTA = "Unica-Correcta-2026!";
    private static final String CLAVE_EQUIVOCADA = "Unica-Equivocada-2026!";
    private static final String CLAVE_REGISTRO = "Unica-Registro-2026!";
    private static final String CLAVE_NUEVA = "Unica-Nueva-2026!";
    private static final String TOKEN_FALSO = "TokenFalsoUnico123456789";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @MockitoBean
    CorreoSaliente correo;

    @Test
    void laCookieDeSesionLlevaHttpOnlySecureYSameSiteStrict() throws Exception {
        String email = crearCliente();
        MvcResult r = iniciarSesion(email, CLAVE_CORRECTA).andReturn();

        assertThat(r.getResponse().getHeader("Set-Cookie"))
                .startsWith("sesion=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/");
    }

    @Test
    void nadaSensibleLlegaAlLog(CapturedOutput log) throws Exception {
        String email = crearCliente();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoLogin(email, CLAVE_EQUIVOCADA))).andExpect(status().isUnauthorized());
        MvcResult ok = iniciarSesion(email, CLAVE_CORRECTA).andReturn();
        String token = ok.getResponse().getCookie("sesion").getValue();
        mvc.perform(post("/api/v1/auth/recuperacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().is4xxClientError());
        // Registro, cambio de contraseña y restablecer: sus cuerpos también llevan contraseñas y el token del enlace
        String nuevo = "seg-" + UUID.randomUUID().toString().substring(0, 12) + "@correo.com";
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Laura Prueba","email":"%s","celular":"3142208815","empresa":"","password":"%s","confirmacion":"%s","aceptaDatos":true}
                """.formatted(nuevo, CLAVE_REGISTRO, CLAVE_REGISTRO))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/cambiar-password").cookie(ok.getResponse().getCookie("sesion"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"passwordActual":"%s","passwordNueva":"%s","confirmacion":"%s"}
                """.formatted(CLAVE_CORRECTA, CLAVE_NUEVA, CLAVE_NUEVA))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/restablecer").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s","passwordNueva":"%s","confirmacion":"%s"}
                """.formatted(TOKEN_FALSO, CLAVE_NUEVA, CLAVE_NUEVA))).andExpect(status().is4xxClientError());

        assertThat(log.getAll()).as("el log debería tener contenido para que esta prueba valga").contains("co.coretech");
        assertThat(log.getAll())
                .doesNotContain(CLAVE_CORRECTA)
                .doesNotContain(CLAVE_EQUIVOCADA)
                .doesNotContain(CLAVE_REGISTRO)
                .doesNotContain(CLAVE_NUEVA)
                .doesNotContain(TOKEN_FALSO)
                .doesNotContain(token)
                .doesNotContain(codificador.encode("x").substring(0, 7)); // ningún hash BCrypt ($2a$10$…)
    }

    private org.springframework.test.web.servlet.ResultActions iniciarSesion(String email, String clave) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpoLogin(email, clave)))
                .andExpect(status().isOk());
    }

    private static String cuerpoLogin(String email, String clave) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"recordarme\":false}".formatted(email, clave);
    }

    private String crearCliente() {
        String email = "seg-" + UUID.randomUUID().toString().substring(0, 12) + "@correo.com";
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, debe_cambiar_password, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, false, true, now() FROM roles WHERE codigo = 'CLIENTE'
                """, email, codificador.encode(CLAVE_CORRECTA));
        return email;
    }
}
