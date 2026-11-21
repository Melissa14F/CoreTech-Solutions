package co.coretech.portal.acceso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.compartido.aplicacion.CorreoSaliente;
import jakarta.servlet.http.Cookie;

/**
 * Acceso de punta a punta contra PostgreSQL 15 (HU-05, HU-06, RNF-01, RNF-10): una prueba por cada
 * fila de "Excepciones" de las fichas, más 401 sin sesión, 403 por permiso y el control de Origin.
 * Cada prueba usa correos nuevos, así que no depende de las demás.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class AccesoTest {

    static final String CLAVE = "Segura2026!";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @MockitoBean
    CorreoSaliente correo;

    // ---------------------------------------------------------------- HU-05 · Registrarme como cliente

    @Nested
    class Registro {

        @Test
        void creaLaCuentaDeClienteYAbreLaSesion() throws Exception {
            String email = correoNuevo();
            MvcResult r = mvc.perform(json(post("/api/v1/auth/registro"), registro("Laura Prueba", email.toUpperCase(), CLAVE, CLAVE, true)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rol").value("CLIENTE"))
                    .andExpect(jsonPath("$.nombre").value("Laura Prueba"))
                    .andReturn();

            String cookie = r.getResponse().getHeader("Set-Cookie");
            assertThat(cookie).startsWith("sesion=").contains("HttpOnly", "SameSite=Strict", "Path=/", "Max-Age=28800");
            // El correo se guarda en minúsculas y con la fecha de aceptación de la política (RNF-09)
            assertThat(jdbc.queryForObject("SELECT acepta_datos_at IS NOT NULL FROM usuarios WHERE email = ?", Boolean.class, email)).isTrue();
            assertThat(auditoria("Creó su cuenta de cliente", email)).isEqualTo(1);
            // El autor es la cuenta nueva, no "Sin identificar"
            assertThat(jdbc.queryForObject("SELECT usuario_id::text FROM auditoria WHERE accion = 'Creó su cuenta de cliente' AND entidad_id = ?",
                    String.class, idDe(email))).isEqualTo(idDe(email));
            // Con la cookie ya tiene sesión
            mvc.perform(get("/api/v1/auth/yo").cookie(cookieDe(r)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.permisos", hasItem("citas.agendar")));
        }

        @Test
        void correoYaRegistradoSinDistinguirMayusculasDa409() throws Exception {
            String email = correoNuevo();
            mvc.perform(json(post("/api/v1/auth/registro"), registro("Laura Prueba", email, CLAVE, CLAVE, true)))
                    .andExpect(status().isCreated());

            mvc.perform(json(post("/api/v1/auth/registro"), registro("Otra Persona", email.toUpperCase(), CLAVE, CLAVE, true)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].campo").value("email"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ya existe una cuenta con este correo"));
        }

        @Test
        void unaContrasenaDeMasDe72BytesDa422YNo500() throws Exception {
            // 73 bytes: BCrypt no las acepta; debe ser un error del formulario, no un error inesperado
            String larga = "Aa1!" + "x".repeat(69);
            mvc.perform(json(post("/api/v1/auth/registro"), registro("Laura Prueba", correoNuevo(), larga, larga, true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("password"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La contraseña es demasiado larga: usa máximo 64 caracteres."));
        }

        @Test
        void contrasenasDistintasDa422EnLaConfirmacion() throws Exception {
            mvc.perform(json(post("/api/v1/auth/registro"), registro("Laura Prueba", correoNuevo(), CLAVE, "Otra2026!", true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("confirmacion"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Las contraseñas no coinciden."));
        }

        @Test
        void sinAceptarLaPoliticaDeDatosDa422() throws Exception {
            mvc.perform(json(post("/api/v1/auth/registro"), registro("Laura Prueba", correoNuevo(), CLAVE, CLAVE, false)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("aceptaDatos"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Debes aceptar la política de datos para crear la cuenta."));
        }

        @Test
        void contrasenaQueNoCumpleRn22Da422() throws Exception {
            mvc.perform(json(post("/api/v1/auth/registro"), registro("Laura Prueba", correoNuevo(), "sinmayuscula1!", "sinmayuscula1!", true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("password"));
        }

        @Test
        void datosInvalidosDan422ConUnErrorPorCampo() throws Exception {
            String cuerpo = """
                    {"nombre":"L4ura","email":"no-es-correo","celular":"2123456789","password":"%s","confirmacion":"%s","aceptaDatos":true}
                    """.formatted(CLAVE, CLAVE);
            mvc.perform(json(post("/api/v1/auth/registro"), cuerpo))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[?(@.campo == 'nombre')].mensaje").value("Escribe solo letras y espacios."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'email')].mensaje").value("Escribe un correo válido, por ejemplo nombre@empresa.com."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'celular')].mensaje").value("Escribe 10 dígitos que empiecen por 3."));
        }
    }

    // ---------------------------------------------------------------- HU-06 · Iniciar y cerrar sesión

    @Nested
    class InicioDeSesion {

        @Test
        void conCredencialesCorrectasAbreUnaSesionDe8Horas() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE, false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value("CLIENTE"))
                    .andExpect(jsonPath("$.debeCambiarPassword").value(false))
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=28800")));
            assertThat(jdbc.queryForObject("SELECT ultimo_acceso_at IS NOT NULL FROM usuarios WHERE email = ?", Boolean.class, email)).isTrue();
        }

        @Test
        void conRecordarmeLaSesionDura30Dias() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE, true)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=2592000")));
        }

        @Test
        void credencialesIncorrectasDan401SinDecirCualFallo() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            mvc.perform(json(post("/api/v1/auth/login"), login(email, "Equivocada1!", false)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Correo o contraseña incorrectos"));
            mvc.perform(json(post("/api/v1/auth/login"), login(correoNuevo(), CLAVE, false)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Correo o contraseña incorrectos"));
        }

        @Test
        void cincoIntentosFallidosBloqueanLaCuenta15Minutos() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            for (int i = 1; i <= 4; i++) {
                mvc.perform(json(post("/api/v1/auth/login"), login(email, "Equivocada1!", false)))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(json(post("/api/v1/auth/login"), login(email, "Equivocada1!", false)))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Cuenta bloqueada 15 minutos por seguridad"));
            // Bloqueada, ni la contraseña correcta entra
            mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE, false)))
                    .andExpect(status().isLocked());

            Long bloqueadaPorMinutos = jdbc.queryForObject(
                    "SELECT round(extract(epoch FROM bloqueado_hasta - now()) / 60) FROM usuarios WHERE email = ?", Long.class, email);
            assertThat(bloqueadaPorMinutos).isBetween(14L, 15L);
            String id = idDe(email);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = 'Intento fallido de inicio de sesión'",
                    Integer.class, id)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = 'Bloqueó la cuenta por intentos fallidos'",
                    Integer.class, id)).isEqualTo(1);
        }

        @Test
        void pasadoElBloqueoPuedeEntrarYEmpiezaDeCero() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            jdbc.update("UPDATE usuarios SET intentos_fallidos = 5, bloqueado_hasta = now() - interval '1 minute' WHERE email = ?", email);

            mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE, false))).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuarios WHERE email = ?", Integer.class, email)).isZero();
        }

        @Test
        void cuentaDesactivadaDa403() throws Exception {
            String email = crearUsuario("CLIENTE", false, false);
            mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE, false)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Tu cuenta está desactivada, contacta al administrador"));
        }

        @Test
        void cerrarSesionInvalidaEseTokenPeroNoLasOtrasSesiones() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            Cookie esta = sesionDe(email);
            Cookie otra = sesionDe(email);

            mvc.perform(post("/api/v1/auth/logout").cookie(esta)).andExpect(status().isNoContent());
            // Aunque alguien haya copiado la cookie, ya no sirve (RNF-10)
            mvc.perform(get("/api/v1/auth/yo").cookie(esta)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/auth/yo").cookie(otra)).andExpect(status().isOk());
        }

        @Test
        void cerrarSesionBorraLaCookie() throws Exception {
            mvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.startsWith("sesion=;"), org.hamcrest.Matchers.containsString("Max-Age=0"))));
        }
    }

    // ---------------------------------------------------------------- HU-06 · Contraseña temporal (RF-05)

    @Nested
    class ContrasenaTemporal {

        @Test
        void conLaTemporalSoloPuedeCambiarla() throws Exception {
            String email = crearUsuario("PRODUCT_OWNER", true, true);
            MvcResult r = mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE, false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.debeCambiarPassword").value(true))
                    .andReturn();
            Cookie sesion = cookieDe(r);

            mvc.perform(get("/api/v1/auth/yo").cookie(sesion)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/proyectos").cookie(sesion))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Cambia tu contraseña temporal para continuar"));
        }

        @Test
        void conLaTemporalPuedePedirElEnlaceSiLaOlvido() throws Exception {
            String email = crearUsuario("PRODUCT_OWNER", true, true);
            Cookie sesion = sesionDe(email);
            // Las rutas públicas no se bloquean aunque llegue la cookie de una sesión con contraseña temporal
            mvc.perform(json(post("/api/v1/auth/recuperacion").cookie(sesion), "{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted());
            mvc.perform(json(post("/api/v1/auth/login").cookie(sesion), login(email, CLAVE, false)))
                    .andExpect(status().isOk());
        }

        @Test
        void laContrasenaActualDebeCoincidirConLaTemporal() throws Exception {
            Cookie sesion = sesionDe(crearUsuario("PRODUCT_OWNER", true, true));
            mvc.perform(json(post("/api/v1/auth/cambiar-password").cookie(sesion), cambio("NoEsLaTemporal1!", "Nueva2026!", "Nueva2026!")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("passwordActual"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La contraseña actual no es correcta"));
        }

        @Test
        void laNuevaDebeSerDistintaDeLaTemporal() throws Exception {
            Cookie sesion = sesionDe(crearUsuario("PRODUCT_OWNER", true, true));
            mvc.perform(json(post("/api/v1/auth/cambiar-password").cookie(sesion), cambio(CLAVE, CLAVE, CLAVE)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La nueva contraseña debe ser distinta de la temporal."));
        }

        @Test
        void alCambiarlaQuedaLibreYSeAudita() throws Exception {
            String email = crearUsuario("PRODUCT_OWNER", true, true);
            Cookie sesion = sesionDe(email);
            Cookie otroDispositivo = sesionDe(email);
            MvcResult r = mvc.perform(json(post("/api/v1/auth/cambiar-password").cookie(sesion), cambio(CLAVE, "Nueva2026!", "Nueva2026!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.debeCambiarPassword").value(false))
                    .andReturn();

            // Esta sesión sigue con una cookie nueva; las demás (y la cookie anterior) dejan de valer (RNF-10)
            Cookie nueva = cookieDe(r);
            mvc.perform(get("/api/v1/proyectos").cookie(nueva)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/auth/yo").cookie(sesion)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/auth/yo").cookie(otroDispositivo)).andExpect(status().isUnauthorized());
            mvc.perform(json(post("/api/v1/auth/login"), login(email, "Nueva2026!", false))).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = 'Cambió su contraseña'",
                    Integer.class, idDe(email))).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- HU-06 · Recuperar la contraseña

    @Nested
    class Recuperacion {

        @Test
        void conUnCorreoQueNoExisteRespondeLoMismoYNoEnvia() throws Exception {
            mvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"email\":\"" + correoNuevo() + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.mensaje").value("Si el correo existe, te enviamos un enlace"));
            verify(correo, never()).enviar(anyString(), anyString(), anyString());
        }

        @Test
        void restablecerLaContrasenaCierraLasSesionesAbiertas() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            Cookie sesion = sesionDe(email);
            mvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted());

            mvc.perform(json(post("/api/v1/auth/restablecer"), restablecer(tokenEnviadoA(email), "Nueva2026!", "Nueva2026!")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/auth/yo").cookie(sesion)).andExpect(status().isUnauthorized());
        }

        @Test
        void elEnlaceSirveUnaSolaVez() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            mvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted());
            String token = tokenEnviadoA(email);

            mvc.perform(json(post("/api/v1/auth/restablecer"), restablecer(token, "Nueva2026!", "Nueva2026!")))
                    .andExpect(status().isOk());
            mvc.perform(json(post("/api/v1/auth/login"), login(email, "Nueva2026!", false))).andExpect(status().isOk());

            mvc.perform(json(post("/api/v1/auth/restablecer"), restablecer(token, "Otra2026!", "Otra2026!")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este enlace ya no es válido, solicita uno nuevo"));
        }

        @Test
        void unEnlaceVencidoNoSirve() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            mvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"email\":\"" + email + "\"}"));
            String token = tokenEnviadoA(email);
            jdbc.update("UPDATE tokens_recuperacion SET expira_at = now() - interval '1 second' WHERE usuario_id = ?::uuid", idDe(email));

            mvc.perform(json(post("/api/v1/auth/restablecer"), restablecer(token, "Nueva2026!", "Nueva2026!")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("token"));
        }

        @Test
        void elAdministradorEnviaElEnlace() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR", false, true));
            String cliente = crearUsuario("CLIENTE", false, true);

            mvc.perform(post("/api/v1/usuarios/{id}/recuperacion", idDe(cliente)).cookie(admin))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.enviadoA").value(cliente))
                    .andExpect(jsonPath("$.expiraEnMinutos").value(30));
            assertThat(tokenEnviadoA(cliente)).isNotBlank();
        }
    }

    // ---------------------------------------------------------------- RNF-01 · Sesión, permisos y origen

    @Nested
    class Seguridad {

        @Test
        void sinCookieDa401() throws Exception {
            mvc.perform(get("/api/v1/auth/yo"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Tu sesión venció, ingresa de nuevo"));
            mvc.perform(post("/api/v1/usuarios/{id}/recuperacion", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void unaCookieAlteradaDa401() throws Exception {
            Cookie sesion = sesionDe(crearUsuario("CLIENTE", false, true));
            mvc.perform(get("/api/v1/auth/yo").cookie(new Cookie("sesion", sesion.getValue() + "x")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void unClienteEnUnEndpointDelAdministradorRecibe403() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE", false, true));
            mvc.perform(post("/api/v1/usuarios/{id}/recuperacion", UUID.randomUUID()).cookie(cliente))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("No tienes permiso para hacer esta acción"));
        }

        @Test
        void siDesactivanLaCuentaLaSesionDejaDeValer() throws Exception {
            String email = crearUsuario("CLIENTE", false, true);
            Cookie sesion = sesionDe(email);
            jdbc.update("UPDATE usuarios SET activo = false WHERE email = ?", email);

            mvc.perform(get("/api/v1/auth/yo").cookie(sesion)).andExpect(status().isUnauthorized());
        }

        @Test
        void unaPeticionQueCambiaDatosDesdeOtroSitioDa403() throws Exception {
            mvc.perform(json(post("/api/v1/auth/login").header("Origin", "https://sitio-malicioso.example"), login(correoNuevo(), CLAVE, false)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La petición viene de un sitio no permitido"));
            String email = crearUsuario("CLIENTE", false, true);
            mvc.perform(json(post("/api/v1/auth/login").header("Origin", "http://localhost:5173"), login(email, CLAVE, false)))
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------- Ayudas

    private static String correoNuevo() {
        return "prueba-" + UUID.randomUUID().toString().substring(0, 12) + "@correo.com";
    }

    /** Crea una cuenta con la contraseña {@link #CLAVE}; los correos del personal llevan el dominio de CoreTech. */
    private String crearUsuario(String rol, boolean temporal, boolean activo) {
        String email = rol.equals("CLIENTE") ? correoNuevo()
                : "prueba-" + UUID.randomUUID().toString().substring(0, 12) + "@coretechsolutions.co";
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, debe_cambiar_password, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, ?, ?, now() FROM roles WHERE codigo = ?
                """, email, codificador.encode(CLAVE), temporal, activo, rol);
        return email;
    }

    private String idDe(String email) {
        return jdbc.queryForObject("SELECT id::text FROM usuarios WHERE email = ?", String.class, email);
    }

    private int auditoria(String accion, String email) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = ? AND entidad_id = ?", Integer.class, accion, idDe(email));
    }

    private Cookie sesionDe(String email) throws Exception {
        return cookieDe(mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE, false)))
                .andExpect(status().isOk()).andReturn());
    }

    private static Cookie cookieDe(MvcResult resultado) {
        Cookie cookie = resultado.getResponse().getCookie("sesion");
        assertThat(cookie).as("cookie de sesión").isNotNull();
        return cookie;
    }

    private String tokenEnviadoA(String email) {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo, timeout(2000)).enviar(eq(email), anyString(), texto.capture());
        Matcher m = Pattern.compile("/restablecer\\?token=([A-Za-z0-9_-]+)").matcher(texto.getValue());
        assertThat(m.find()).as("enlace en el correo").isTrue();
        return m.group(1);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static String registro(String nombre, String email, String password, String confirmacion, boolean acepta) {
        return """
                {"nombre":"%s","email":"%s","celular":"3142208815","empresa":"","password":"%s","confirmacion":"%s","aceptaDatos":%s}
                """.formatted(nombre, email, password, confirmacion, acepta);
    }

    private static String login(String email, String password, boolean recordarme) {
        return """
                {"email":"%s","password":"%s","recordarme":%s}
                """.formatted(email, password, recordarme);
    }

    private static String cambio(String actual, String nueva, String confirmacion) {
        return """
                {"passwordActual":"%s","passwordNueva":"%s","confirmacion":"%s"}
                """.formatted(actual, nueva, confirmacion);
    }

    private static String restablecer(String token, String nueva, String confirmacion) {
        return """
                {"token":"%s","passwordNueva":"%s","confirmacion":"%s"}
                """.formatted(token, nueva, confirmacion);
    }
}
