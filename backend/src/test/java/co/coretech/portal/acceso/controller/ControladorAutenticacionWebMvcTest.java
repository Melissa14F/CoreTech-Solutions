package co.coretech.portal.acceso.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.coretech.portal.acceso.service.Ingreso;
import co.coretech.portal.acceso.service.NuevoCliente;
import co.coretech.portal.acceso.service.ServicioAutenticacion;
import co.coretech.portal.acceso.service.ServicioRecuperacion;
import co.coretech.portal.compartido.service.AuditoriaAccesoDenegado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.CuentaBloqueadaException;
import co.coretech.portal.compartido.exception.NoAutenticadoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.controller.ConfiguracionErroresPrueba;
import co.coretech.portal.compartido.controller.ServicioSesion;

/**
 * Pruebas de INTEGRACIÓN de la API de acceso con {@code @WebMvcTest} + MockMvc (HU-05 y HU-06): rutas, validación de los
 * cuerpos con el manejador global REAL (siempre 422 con {@code {errores:[{campo,mensaje}]}}), códigos HTTP de cada excepción del
 * servicio y la cookie de sesión. Los servicios van simulados: aquí no hay reglas ni base de datos. Los permisos y la cookie
 * firmada de verdad se prueban de punta a punta en {@code AccesoTest}.
 */
@WebMvcTest(controllers = ControladorAutenticacion.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorAutenticacionWebMvcTest {

    static final UUID USUARIO = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final String REGISTRO = """
            {"nombre":"Laura Gómez","email":"laura@correo.com","celular":"3001234567","empresa":"Salón Laura",
             "password":"Abcdef1!","confirmacion":"Abcdef1!","aceptaDatos":true}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioAutenticacion autenticacion;
    @MockitoBean
    ServicioRecuperacion recuperacion;
    @MockitoBean
    ServicioSesion sesiones;
    @MockitoBean
    SesionActual sesionActual;
    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    @BeforeEach
    void cookieDeSesion() {
        when(sesiones.abrir(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(ResponseCookie.from("sesion", "token-de-prueba").httpOnly(true).sameSite("Strict").path("/").build());
        when(sesiones.cerrar(any())).thenReturn(ResponseCookie.from("sesion", "").maxAge(0).path("/").build());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Nested
    @DisplayName("POST /auth/registro (HU-05)")
    class Registro {

        @Test
        @DisplayName("Con datos válidos responde 201, abre la sesión (Set-Cookie) y devuelve id, rol y nombre sin contraseña")
        void creaLaCuenta() throws Exception {
            when(autenticacion.registrar(any(NuevoCliente.class))).thenReturn(new Ingreso(USUARIO, "CLIENTE", "Laura Gómez", false, 0));

            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/auth/yo"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("sesion=token-de-prueba")))
                    .andExpect(jsonPath("$.usuarioId").value(USUARIO.toString()))
                    .andExpect(jsonPath("$.rol").value("CLIENTE"))
                    .andExpect(jsonPath("$.nombre").value("Laura Gómez"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("Pasa al servicio exactamente lo que escribió la persona")
        void pasaLosDatos() throws Exception {
            when(autenticacion.registrar(any(NuevoCliente.class))).thenReturn(new Ingreso(USUARIO, "CLIENTE", "Laura Gómez", false, 0));

            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO)).andExpect(status().isCreated());

            verify(autenticacion).registrar(eq(new NuevoCliente("Laura Gómez", "laura@correo.com", "3001234567", "Salón Laura", "Abcdef1!", "Abcdef1!", true)));
            verify(sesiones).abrir(USUARIO, 0, false);
        }

        @Test
        @DisplayName("Un cuerpo vacío da 422 con un error por cada campo obligatorio")
        void cuerpoVacio() throws Exception {
            mvc.perform(json(post("/api/v1/auth/registro"), "{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("email")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("celular")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("password")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("confirmacion")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("aceptaDatos")));
            verify(autenticacion, never()).registrar(any());
        }

        @Test
        @DisplayName("Nombre con números, correo sin punto o celular que no empieza por 3: 422 con el mensaje del prototipo en su campo")
        void formatosInvalidos() throws Exception {
            String cuerpo = REGISTRO.replace("Laura Gómez", "Laura 123").replace("laura@correo.com", "laura@correo").replace("3001234567", "2001234567");

            mvc.perform(json(post("/api/v1/auth/registro"), cuerpo))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[?(@.campo=='nombre')].mensaje").value("Escribe solo letras y espacios."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='email')].mensaje").value("Escribe un correo válido, por ejemplo nombre@empresa.com."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='celular')].mensaje").value("Escribe 10 dígitos que empiecen por 3."));
        }

        @Test
        @DisplayName("Valores límite del nombre: 2 caracteres no; 3 sí")
        void largoDelNombre() throws Exception {
            when(autenticacion.registrar(any())).thenReturn(new Ingreso(USUARIO, "CLIENTE", "Ana", false, 0));

            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO.replace("Laura Gómez", "An")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO.replace("Laura Gómez", "Ana"))).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Sin aceptar la política de datos (false): 422 en aceptaDatos")
        void sinAceptar() throws Exception {
            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO.replace("\"aceptaDatos\":true", "\"aceptaDatos\":false")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("aceptaDatos"));
        }

        @Test
        @DisplayName("Un cuerpo que no es JSON da 422 con el mensaje estándar, no 400 ni 500")
        void noEsJson() throws Exception {
            mvc.perform(json(post("/api/v1/auth/registro"), "esto no es json"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Los datos enviados no tienen el formato esperado"));
        }

        @Test
        @DisplayName("Correo repetido: 409 con el mensaje en el campo email")
        void correoRepetido() throws Exception {
            when(autenticacion.registrar(any())).thenThrow(new ConflictoException("email", "Ya existe una cuenta con este correo"));

            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].campo").value("email"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ya existe una cuenta con este correo"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        @Test
        @DisplayName("Contraseña que no cumple RN-22: 422 en el campo password que manda el servicio")
        void contrasenaDebil() throws Exception {
            when(autenticacion.registrar(any())).thenThrow(new ReglaNegocioException("password", "La contraseña aún no cumple todos los requisitos"));

            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("password"));
        }

        @Test
        @DisplayName("Un error inesperado responde 500 con un mensaje genérico, sin detalles internos")
        void errorInesperado() throws Exception {
            when(autenticacion.registrar(any())).thenThrow(new IllegalStateException("fallo interno con datos sensibles"));

            mvc.perform(json(post("/api/v1/auth/registro"), REGISTRO))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errores", hasSize(1)))
                    .andExpect(jsonPath("$.errores[0].mensaje", not(containsString("sensibles"))));
        }
    }

    @Nested
    @DisplayName("POST /auth/login (HU-06)")
    class Login {

        static final String LOGIN = "{\"email\":\"laura@correo.com\",\"password\":\"Abcdef1!\"}";

        @Test
        @DisplayName("Credenciales correctas: 200, cookie de sesión y rol, nombre y si debe cambiar la contraseña")
        void entra() throws Exception {
            when(autenticacion.iniciarSesion("laura@correo.com", "Abcdef1!")).thenReturn(new Ingreso(USUARIO, "CLIENTE", "Laura Gómez", false, 3));

            mvc.perform(json(post("/api/v1/auth/login"), LOGIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("sesion=token-de-prueba")))
                    .andExpect(jsonPath("$.rol").value("CLIENTE"))
                    .andExpect(jsonPath("$.nombre").value("Laura Gómez"))
                    .andExpect(jsonPath("$.debeCambiarPassword").value(false));
            verify(sesiones).abrir(USUARIO, 3, false);
        }

        @Test
        @DisplayName("«Recordarme» se pasa a la sesión de 30 días")
        void recordarme() throws Exception {
            when(autenticacion.iniciarSesion(any(), any())).thenReturn(new Ingreso(USUARIO, "CLIENTE", "Laura", false, 0));

            mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"laura@correo.com\",\"password\":\"Abcdef1!\",\"recordarme\":true}"))
                    .andExpect(status().isOk());

            verify(sesiones).abrir(USUARIO, 0, true);
        }

        @Test
        @DisplayName("Con la contraseña temporal avisa que debe cambiarla")
        void conTemporal() throws Exception {
            when(autenticacion.iniciarSesion(any(), any())).thenReturn(new Ingreso(USUARIO, "PRODUCT_OWNER", "Andrés", true, 0));

            mvc.perform(json(post("/api/v1/auth/login"), LOGIN)).andExpect(jsonPath("$.debeCambiarPassword").value(true));
        }

        @Test
        @DisplayName("Credenciales incorrectas: 401 sin cookie; cuenta bloqueada: 423; cuenta desactivada: 403")
        void errores() throws Exception {
            when(autenticacion.iniciarSesion(any(), any())).thenThrow(new NoAutenticadoException("Correo o contraseña incorrectos"));
            mvc.perform(json(post("/api/v1/auth/login"), LOGIN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Correo o contraseña incorrectos"));

            // doThrow: volver a usar when() sobre un mock que ya lanza, lanzaría la excepción anterior al preparar la prueba
            org.mockito.Mockito.doThrow(new CuentaBloqueadaException("Cuenta bloqueada 15 minutos por seguridad")).when(autenticacion).iniciarSesion(any(), any());
            mvc.perform(json(post("/api/v1/auth/login"), LOGIN)).andExpect(status().isLocked());

            org.mockito.Mockito.doThrow(new ProhibidoException("Tu cuenta está desactivada, contacta al administrador")).when(autenticacion).iniciarSesion(any(), any());
            mvc.perform(json(post("/api/v1/auth/login"), LOGIN)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sin correo o sin contraseña: 422 y no llega al servicio")
        void camposObligatorios() throws Exception {
            mvc.perform(json(post("/api/v1/auth/login"), "{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(2)));
            verify(autenticacion, never()).iniciarSesion(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Sesión: logout, yo y cambiar la contraseña")
    class Sesion {

        @Test
        @DisplayName("POST /auth/logout: 204 y la cookie se borra; funciona aunque no llegue la cookie")
        void logout() throws Exception {
            mvc.perform(post("/api/v1/auth/logout").cookie(new jakarta.servlet.http.Cookie("sesion", "abc")))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
            verify(sesiones).cerrar("abc");

            mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
            verify(sesiones).cerrar(null);
        }

        @Test
        @DisplayName("GET /auth/yo devuelve el usuario con sus permisos ordenados")
        void yo() throws Exception {
            when(sesionActual.usuario()).thenReturn(new UsuarioSesion(USUARIO, "Laura Gómez", "laura@correo.com", "CLIENTE",
                    Set.of("proyectos.ver_propios", "citas.agendar"), false, 0));

            mvc.perform(get("/api/v1/auth/yo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usuarioId").value(USUARIO.toString()))
                    .andExpect(jsonPath("$.email").value("laura@correo.com"))
                    .andExpect(jsonPath("$.permisos[0]").value("citas.agendar"))
                    .andExpect(jsonPath("$.permisos[1]").value("proyectos.ver_propios"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("GET /auth/yo sin sesión: 401 con el mensaje estándar")
        void yoSinSesion() throws Exception {
            when(sesionActual.usuario()).thenThrow(new NoAutenticadoException("Tu sesión venció, ingresa de nuevo"));

            mvc.perform(get("/api/v1/auth/yo")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /auth/cambiar-password: 200 y una cookie nueva que conserva «Recordarme»")
        void cambiaLaContrasena() throws Exception {
            when(autenticacion.cambiarPassword("Abcdef1!", "Nueva123#", "Nueva123#")).thenReturn(new Ingreso(USUARIO, "CLIENTE", "Laura", false, 4));
            when(sesiones.leer("abc")).thenReturn(Optional.of(new ServicioSesion.Token(USUARIO, 3, true, UUID.randomUUID(), Instant.now().plusSeconds(3600))));

            mvc.perform(json(post("/api/v1/auth/cambiar-password").cookie(new jakarta.servlet.http.Cookie("sesion", "abc")),
                    "{\"passwordActual\":\"Abcdef1!\",\"passwordNueva\":\"Nueva123#\",\"confirmacion\":\"Nueva123#\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("sesion=token-de-prueba")))
                    .andExpect(jsonPath("$.debeCambiarPassword").value(false));
            verify(sesiones).abrir(USUARIO, 4, true);
        }

        @Test
        @DisplayName("Contraseña actual incorrecta: 422 en passwordActual; campos vacíos: 422 sin llegar al servicio")
        void errores() throws Exception {
            when(autenticacion.cambiarPassword(any(), any(), any())).thenThrow(new ReglaNegocioException("passwordActual", "La contraseña actual no es correcta"));
            mvc.perform(json(post("/api/v1/auth/cambiar-password"), "{\"passwordActual\":\"x\",\"passwordNueva\":\"Nueva123#\",\"confirmacion\":\"Nueva123#\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("passwordActual"));

            mvc.perform(json(post("/api/v1/auth/cambiar-password"), "{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(3)));
        }
    }

    @Nested
    @DisplayName("Recuperar la contraseña (RF-04)")
    class Recuperacion {

        @Test
        @DisplayName("POST /auth/recuperacion: 202 con el mismo mensaje exista o no el correo")
        void respondeIgual() throws Exception {
            mvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"email\":\"nadie@correo.com\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.mensaje").value("Si el correo existe, te enviamos un enlace"));
            verify(recuperacion).solicitarPorCorreo("nadie@correo.com");
        }

        @Test
        @DisplayName("Un correo mal escrito o vacío da 422 y no llega al servicio")
        void correoInvalido() throws Exception {
            mvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"email\":\"sin-arroba\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("email"));
            mvc.perform(json(post("/api/v1/auth/recuperacion"), "{}")).andExpect(status().isUnprocessableEntity());
            verify(recuperacion, never()).solicitarPorCorreo(any());
        }

        @Test
        @DisplayName("POST /auth/restablecer: 200 con el mensaje de éxito")
        void restablece() throws Exception {
            mvc.perform(json(post("/api/v1/auth/restablecer"), "{\"token\":\"abc\",\"passwordNueva\":\"Nueva123#\",\"confirmacion\":\"Nueva123#\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensaje").value(containsString("quedó cambiada")));
            verify(recuperacion).restablecer("abc", "Nueva123#", "Nueva123#");
        }

        @Test
        @DisplayName("Un enlace vencido o usado: 422 en el campo token")
        void enlaceInvalido() throws Exception {
            org.mockito.Mockito.doThrow(new ReglaNegocioException("token", "Este enlace ya no es válido, solicita uno nuevo"))
                    .when(recuperacion).restablecer(any(), any(), any());

            mvc.perform(json(post("/api/v1/auth/restablecer"), "{\"token\":\"abc\",\"passwordNueva\":\"Nueva123#\",\"confirmacion\":\"Nueva123#\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("token"));
        }
    }
}
