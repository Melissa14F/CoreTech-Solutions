package co.coretech.portal.acceso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.ResultActions;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.acceso.aplicacion.ClienteIdentidadExterna;
import co.coretech.portal.acceso.aplicacion.ClienteIdentidadExterna.ErrorProveedorException;
import co.coretech.portal.acceso.aplicacion.ClienteIdentidadExterna.Identidad;
import co.coretech.portal.acceso.dominio.ProveedorExterno;
import co.coretech.portal.comunicacion.aplicacion.TareaEnvioCorreos;
import jakarta.servlet.http.Cookie;

/**
 * RF-50, D-50: registro e ingreso de clientes con Google o Microsoft, con el proveedor simulado (nunca sale a
 * internet): entrar, unir con una cuenta existente (solo con correo verificado), completar el registro, y los rechazos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class IngresoExternoTest {

    static final String PORTAL = "http://localhost:5173";
    static final String CLAVE = "Segura2026!";

    @MockitoBean
    ClienteIdentidadExterna proveedores;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    TareaEnvioCorreos cola;

    /** El "estado" que el portal mandó al proveedor en el último intento. */
    final AtomicReference<String> estado = new AtomicReference<>();

    @BeforeEach
    void proveedorSimulado() {
        reset(proveedores);
        when(proveedores.configurado(ProveedorExterno.GOOGLE)).thenReturn(true);
        when(proveedores.configurado(ProveedorExterno.MICROSOFT)).thenReturn(true);
        when(proveedores.urlAutorizacion(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString())).thenAnswer(i -> {
            estado.set(i.getArgument(2));
            return "https://proveedor.example/autorizar?state=" + i.getArgument(2);
        });
    }

    // ---------------------------------------------------------------- Botones e inicio

    @Test
    void losBotonesSoloMuestranLosProveedoresConfigurados() throws Exception {
        mvc.perform(get("/api/v1/auth/proveedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("GOOGLE"))
                .andExpect(jsonPath("$[0].nombre").value("Google"))
                .andExpect(jsonPath("$[0].url").value("/api/v1/auth/externo/google"))
                .andExpect(jsonPath("$[1].codigo").value("MICROSOFT"));

        when(proveedores.configurado(ProveedorExterno.MICROSOFT)).thenReturn(false);
        mvc.perform(get("/api/v1/auth/proveedores")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/auth/externo/microsoft"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=no_disponible"));
        mvc.perform(get("/api/v1/auth/externo/facebook"))
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=no_disponible"));
    }

    // ---------------------------------------------------------------- Cliente nuevo

    @Test
    void clienteNuevoCompletaElRegistroYRecibeLaBienvenida() throws Exception {
        String email = correoNuevo();
        when(proveedores.identidad(eq(ProveedorExterno.GOOGLE), eq("codigo"), anyString(), anyString()))
                .thenReturn(new Identidad("google-sub-1", email, true, "Sofía Duarte (Google)"));

        MvcResultSesion registro = volverDeProveedor("google", "codigo");
        registro.resultado().andExpect(status().isFound()).andExpect(header().string("Location", PORTAL + "/completar-registro"));
        assertThat(registro.cookieRegistro()).isNotNull();
        assertThat(registro.cookieRegistro().isHttpOnly()).isTrue();
        assertThat(registro.cookieRegistro().getMaxAge()).isEqualTo(600);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, email))
                .as("todavía no se crea la cuenta").isZero();

        mvc.perform(get("/api/v1/auth/externo/pendiente").cookie(registro.cookieRegistro()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor").value("Google"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nombre").value("Sofía Duarte Google"));

        var creada = mvc.perform(post("/api/v1/auth/externo/completar").cookie(registro.cookieRegistro())
                        .contentType(MediaType.APPLICATION_JSON).content(completar("Sofía Duarte", "3142208815", "Duarte SAS", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andReturn().getResponse();
        List<String> cookies = creada.getHeaders("Set-Cookie");
        assertThat(cookies).anyMatch(c -> c.startsWith("sesion=") && c.contains("HttpOnly"));
        assertThat(cookies).as("la cookie del registro se borra").anyMatch(c -> c.startsWith("registro_externo=;") && c.contains("Max-Age=0"));

        var fila = jdbc.queryForMap("SELECT id, password_hash, celular, empresa, acepta_datos_at FROM usuarios WHERE email = ?", email);
        assertThat(fila.get("password_hash")).as("sin contraseña del portal").isNull();
        assertThat(fila).containsEntry("celular", "3142208815").containsEntry("empresa", "Duarte SAS");
        assertThat(fila.get("acepta_datos_at")).isNotNull();
        UUID id = (UUID) fila.get("id");
        assertThat(jdbc.queryForObject("SELECT sujeto FROM cuentas_externas WHERE usuario_id = ? AND proveedor = 'GOOGLE'", String.class, id))
                .isEqualTo("google-sub-1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'Creó su cuenta de cliente con Google' AND entidad_id = ?",
                Integer.class, id.toString())).isEqualTo(1);

        // D-52: la bienvenida llega a la campana (y al correo con el que se registró)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = 'CUENTA_CREADA'", Integer.class, id))
                .isEqualTo(1);
        cola.enviarPendientes();
    }

    @Test
    void completarSinAceptarLaPoliticaOConDatosMalosNoCreaLaCuenta() throws Exception {
        String email = correoNuevo();
        when(proveedores.identidad(eq(ProveedorExterno.MICROSOFT), anyString(), anyString(), anyString()))
                .thenReturn(new Identidad("ms-sub-1", email, true, "Laura"));
        Cookie registro = volverDeProveedor("microsoft", "c").cookieRegistro();

        mvc.perform(post("/api/v1/auth/externo/completar").cookie(registro).contentType(MediaType.APPLICATION_JSON)
                        .content(completar("Laura Gómez", "3142208815", "", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("aceptaDatos"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Debes aceptar la política de datos para crear la cuenta."));
        mvc.perform(post("/api/v1/auth/externo/completar").cookie(registro).contentType(MediaType.APPLICATION_JSON)
                        .content(completar("L4ura", "12345", "", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[?(@.campo == 'nombre')].mensaje").value("Escribe solo letras y espacios."))
                .andExpect(jsonPath("$.errores[?(@.campo == 'celular')].mensaje").value("Escribe 10 dígitos que empiecen por 3."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, email)).isZero();
        // Sigue pudiendo completarlo con datos buenos
        mvc.perform(post("/api/v1/auth/externo/completar").cookie(registro).contentType(MediaType.APPLICATION_JSON)
                        .content(completar("Laura Gómez", "3142208815", "", true)))
                .andExpect(status().isCreated());
    }

    @Test
    void sinLaCookieDeRegistroOUsadaDosVecesNoSePuedeCompletar() throws Exception {
        String email = correoNuevo();
        when(proveedores.identidad(eq(ProveedorExterno.GOOGLE), anyString(), anyString(), anyString()))
                .thenReturn(new Identidad("google-sub-2", email, true, "Ana"));
        Cookie registro = volverDeProveedor("google", "c").cookieRegistro();

        mvc.perform(post("/api/v1/auth/externo/completar").contentType(MediaType.APPLICATION_JSON)
                        .content(completar("Ana Vera", "3142208815", "", true)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/externo/completar").cookie(registro).contentType(MediaType.APPLICATION_JSON)
                .content(completar("Ana Vera", "3142208815", "", true))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/externo/completar").cookie(registro).contentType(MediaType.APPLICATION_JSON)
                .content(completar("Ana Vera", "3142208815", "", true))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, email)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- Cuenta existente

    @Test
    void clienteConContrasenaQueEntraConGoogleSeUneASuCuentaYSigueTeniendoLaContrasena() throws Exception {
        String email = correoNuevo();
        UUID id = crearUsuario("CLIENTE", email, true);
        when(proveedores.identidad(eq(ProveedorExterno.GOOGLE), anyString(), anyString(), anyString()))
                .thenReturn(new Identidad("google-sub-3", email.toUpperCase(), true, "Laura"));

        MvcResultSesion r = volverDeProveedor("google", "c");

        r.resultado().andExpect(status().isFound()).andExpect(header().string("Location", PORTAL + "/cliente/proyectos"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("sesion=")));
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM cuentas_externas WHERE proveedor = 'GOOGLE' AND sujeto = 'google-sub-3'", UUID.class))
                .isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'Unió su cuenta de Google' AND entidad_id = ?",
                Integer.class, id.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, email))
                .as("no se duplica la cuenta").isEqualTo(1);
        // Su contraseña de siempre sigue sirviendo
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"recordarme\":false}".formatted(email, CLAVE)))
                .andExpect(status().isOk());
    }

    @Test
    void laSegundaVezEntraPorElVinculoSinPedirNada() throws Exception {
        String email = correoNuevo();
        UUID id = crearUsuario("CLIENTE", email, true);
        when(proveedores.identidad(eq(ProveedorExterno.MICROSOFT), anyString(), anyString(), anyString()))
                .thenReturn(new Identidad("ms-sub-9", email, true, "Laura"));
        volverDeProveedor("microsoft", "c").resultado().andExpect(header().string("Location", PORTAL + "/cliente/proyectos"));

        // La persona cambió su correo en Microsoft: el vínculo es por su identificador, no por el correo
        when(proveedores.identidad(eq(ProveedorExterno.MICROSOFT), anyString(), anyString(), anyString()))
                .thenReturn(new Identidad("ms-sub-9", "otro-correo-" + email, true, "Laura"));
        volverDeProveedor("microsoft", "c").resultado().andExpect(header().string("Location", PORTAL + "/cliente/proyectos"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuentas_externas WHERE usuario_id = ?", Integer.class, id)).isEqualTo(1);
        // La primera vez se une e inicia sesión; la segunda solo inicia sesión
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'Unió su cuenta de Microsoft' AND entidad_id = ?",
                Integer.class, id.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'Inició sesión con Microsoft' AND entidad_id = ?",
                Integer.class, id.toString())).isEqualTo(2);
    }

    @Test
    void sinCorreoVerificadoNoSeUneNiSeCrea() throws Exception {
        String email = correoNuevo();
        UUID id = crearUsuario("CLIENTE", email, true);
        when(proveedores.identidad(eq(ProveedorExterno.GOOGLE), anyString(), anyString(), anyString()))
                .thenReturn(new Identidad("google-sub-4", email, false, "Impostor"));

        volverDeProveedor("google", "c").resultado()
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=correo_sin_verificar&proveedor=google"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuentas_externas WHERE usuario_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void elPersonalNoEntraPorAquiYLasCuentasDesactivadasTampoco() throws Exception {
        String admin = "patricia-" + UUID.randomUUID().toString().substring(0, 6) + "@coretechsolutions.co";
        crearUsuario("ADMINISTRADOR", admin, true);
        when(proveedores.identidad(eq(ProveedorExterno.GOOGLE), eq("admin"), anyString(), anyString()))
                .thenReturn(new Identidad("google-sub-5", admin, true, "Patricia"));
        volverDeProveedor("google", "admin").resultado()
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=solo_clientes&proveedor=google"));

        String inactivo = correoNuevo();
        crearUsuario("CLIENTE", inactivo, false);
        when(proveedores.identidad(eq(ProveedorExterno.GOOGLE), eq("inactivo"), anyString(), anyString()))
                .thenReturn(new Identidad("google-sub-6", inactivo, true, "Inactivo"));
        volverDeProveedor("google", "inactivo").resultado()
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=desactivada&proveedor=google"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuentas_externas WHERE sujeto IN ('google-sub-5', 'google-sub-6')", Integer.class)).isZero();
    }

    // ---------------------------------------------------------------- Fallos del proveedor y del estado

    @Test
    void cancelarFallarOUsarUnEstadoAjenoLlevaAIngresarConElMotivo() throws Exception {
        String s = iniciar("google");
        mvc.perform(get("/api/v1/auth/externo/callback/google").param("state", s).param("error", "access_denied"))
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=cancelado&proveedor=google"));
        // El mismo estado no sirve dos veces
        mvc.perform(get("/api/v1/auth/externo/callback/google").param("state", s).param("code", "c"))
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=vencido&proveedor=google"));
        // Un estado de Google no vale para Microsoft
        String deGoogle = iniciar("google");
        mvc.perform(get("/api/v1/auth/externo/callback/microsoft").param("state", deGoogle).param("code", "c"))
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=vencido&proveedor=microsoft"));
        mvc.perform(get("/api/v1/auth/externo/callback/google").param("state", "inventado").param("code", "c"))
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=vencido&proveedor=google"));

        String fallo = iniciar("microsoft");
        when(proveedores.identidad(eq(ProveedorExterno.MICROSOFT), eq("falla"), anyString(), anyString()))
                .thenThrow(new ErrorProveedorException("Microsoft no respondió bien", null));
        mvc.perform(get("/api/v1/auth/externo/callback/microsoft").param("state", fallo).param("code", "falla"))
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=error&proveedor=microsoft"));

        String sinCorreo = iniciar("google");
        when(proveedores.identidad(eq(ProveedorExterno.GOOGLE), eq("sin-correo"), anyString(), anyString()))
                .thenReturn(new Identidad("google-sub-7", null, false, "Sin correo"));
        mvc.perform(get("/api/v1/auth/externo/callback/google").param("state", sinCorreo).param("code", "sin-correo"))
                .andExpect(header().string("Location", PORTAL + "/ingresar?externo=sin_correo&proveedor=google"));
    }

    @Test
    void anonimizarAUnClienteBorraSusVinculosConGoogleYMicrosoft() throws Exception {
        String email = correoNuevo();
        UUID cliente = crearUsuario("CLIENTE", email, true);
        jdbc.update("INSERT INTO cuentas_externas (usuario_id, proveedor, sujeto, email) VALUES (?, 'GOOGLE', ?, ?)", cliente, "sub-" + cliente, email);
        String admin = "admin-" + UUID.randomUUID().toString().substring(0, 6) + "@coretechsolutions.co";
        crearUsuario("ADMINISTRADOR", admin, true);
        Cookie sesion = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"recordarme\":false}".formatted(admin, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");

        mvc.perform(post("/api/v1/usuarios/" + cliente + "/anonimizar").cookie(sesion)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuentas_externas WHERE usuario_id = ?", Integer.class, cliente)).isZero();
    }

    // ---------------------------------------------------------------- Ayudas

    record MvcResultSesion(ResultActions resultado, Cookie cookieRegistro) {
    }

    private String iniciar(String proveedor) throws Exception {
        mvc.perform(get("/api/v1/auth/externo/" + proveedor)).andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://proveedor.example/autorizar?state=")));
        return estado.get();
    }

    /** Ida al proveedor y regreso con el código (el navegador vuelve sin la cookie de sesión). */
    private MvcResultSesion volverDeProveedor(String proveedor, String codigo) throws Exception {
        String s = iniciar(proveedor);
        ResultActions resultado = mvc.perform(get("/api/v1/auth/externo/callback/" + proveedor).param("state", s).param("code", codigo));
        Cookie registro = resultado.andReturn().getResponse().getCookie("registro_externo");
        return new MvcResultSesion(resultado, registro);
    }

    private static String completar(String nombre, String celular, String empresa, boolean acepta) {
        return """
                {"nombre":"%s","celular":"%s","empresa":"%s","aceptaDatos":%s}
                """.formatted(nombre, celular, empresa, acepta);
    }

    private static String correoNuevo() {
        return "ext-" + UUID.randomUUID().toString().substring(0, 10) + "@empresa-cliente.com";
    }

    private UUID crearUsuario(String rol, String email, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT ?, id, 'Usuario de Prueba', ?, '3001234567', ?, ?, now() FROM roles WHERE codigo = ?
                """, id, email, codificador.encode(CLAVE), activo, rol);
        return id;
    }

}
