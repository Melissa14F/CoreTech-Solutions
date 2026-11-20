package co.coretech.portal.especialidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.compartido.aplicacion.CorreoSaliente;
import jakarta.servlet.http.Cookie;

/**
 * Desarrolladores y equipos contra PostgreSQL 15 (HU-25, HU-26; RN-05, RN-18, RN-25, RN-26): una prueba por cada fila de
 * "Excepciones" de las dos fichas, la cuenta con contraseña temporal del Product Owner y la puerta pública EquipoConsultas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class EspecialidadTest {

    static final String CLAVE = "Segura2026!";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    EquipoConsultas consultas;

    @MockitoBean
    CorreoSaliente correo;

    // ---------------------------------------------------------------- HU-25 · Desarrolladores

    @Nested
    class Desarrolladores {

        @Test
        void registraListaYEditaSinCrearCuenta() throws Exception {
            Cookie admin = sesionAdmin();
            String correo = correoNuevo();
            String id = registrar(admin, "Laura Gómez Ruiz", correo, "BACKEND");

            mvc.perform(get("/api/v1/desarrolladores").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.desarrolladorId=='" + id + "')].tieneCuenta").value(false))
                    .andExpect(jsonPath("$[?(@.desarrolladorId=='" + id + "')].equipo").value(org.hamcrest.Matchers.contains((Object) null)));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, correo)).isZero();

            mvc.perform(json(put("/api/v1/desarrolladores/" + id).cookie(admin), cuerpo("Laura Gómez Ruiz", "3109998877", correo, "QA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.especialidad").value("QA"))
                    .andExpect(jsonPath("$.celular").value("3109998877"));
        }

        @Test
        void correoRepetidoDa409() throws Exception {
            Cookie admin = sesionAdmin();
            String correo = correoNuevo();
            registrar(admin, "Laura Gómez Ruiz", correo, "BACKEND");
            mvc.perform(json(post("/api/v1/desarrolladores").cookie(admin), cuerpo("Otra Persona", "3001234567", correo.toUpperCase(), "QA")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ya hay un desarrollador registrado con este correo"));
        }

        @Test
        void validaTodosLosCamposALaVez() throws Exception {
            Cookie admin = sesionAdmin();
            mvc.perform(json(post("/api/v1/desarrolladores").cookie(admin), cuerpo("Ana", "2001234567", "ana@gmail.com", "COCINA")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(4)))
                    .andExpect(jsonPath("$.errores[?(@.campo=='correo')].mensaje")
                            .value("Usa el correo corporativo que termina en @coretechsolutions.co."));
            mvc.perform(json(post("/api/v1/desarrolladores").cookie(admin), cuerpo("", "", "", "")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(4)));
        }

        @Test
        void soloQuienTienePermisoDeCuentasEntra() throws Exception {
            Cookie po = sesionDe(crearUsuario("PRODUCT_OWNER"));
            mvc.perform(get("/api/v1/desarrolladores").cookie(po)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/equipos").cookie(po)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/equipos")).andExpect(status().isUnauthorized());
        }

        @Test
        void cambiarLaEspecialidadAjustaElRolQueYaNoCorresponde() throws Exception {
            Cookie admin = sesionAdmin();
            String correo = correoNuevo();
            String id = registrar(admin, "Kevin Rojas Pardo", correo, "FULL_STACK");
            String otro = registrar(admin, "Sandra Mejía Peña", correoNuevo(), "QA");
            String po = registrar(admin, "Tomás Vera Díaz", correoNuevo(), "UX_UI");
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null, fila(id, "BACKEND"), fila(otro, "QA"))))
                    .andExpect(status().isCreated());

            mvc.perform(json(put("/api/v1/desarrolladores/" + id).cookie(admin), cuerpo("Kevin Rojas Pardo", "3001234567", correo, "FRONTEND")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value("FRONTEND"));
        }

        @Test
        void desactivarAUnIntegranteLoSacaDelEquipoPeroNoAlUnicoQueQueda() throws Exception {
            Cookie admin = sesionAdmin();
            String po = registrar(admin, "Paula Núñez Soto", correoNuevo(), "BACKEND");
            String a = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            String b = registrar(admin, "Elena Sáenz Mora", correoNuevo(), "UX_UI");
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null, fila(a, "QA"), fila(b, "UX_UI"))))
                    .andExpect(status().isCreated());

            mvc.perform(json(patch("/api/v1/desarrolladores/" + a + "/activo").cookie(admin), "{\"activo\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false))
                    .andExpect(jsonPath("$.equipo").doesNotExist());
            mvc.perform(json(patch("/api/v1/desarrolladores/" + b + "/activo").cookie(admin), "{\"activo\":false}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value(
                            "El equipo de Paula Núñez Soto quedaría sin integrantes; agrega otro antes de desactivarlo"));
        }

        @Test
        void unProductOwnerConProyectosActivosNoSeDesactivaYSinProyectosQuedaBloqueado() throws Exception {
            Cookie admin = sesionAdmin();
            String po = registrar(admin, "Rafael Ibarra Luna", correoNuevo(), "BACKEND");
            String a = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            String equipoId = crearEquipo(admin, po, fila(a, "QA"));
            UUID usuarioPO = jdbc.queryForObject("SELECT usuario_id FROM desarrolladores WHERE id = ?::uuid", UUID.class, po);
            proyectoActivo(UUID.fromString(equipoId));

            mvc.perform(json(patch("/api/v1/desarrolladores/" + po + "/activo").cookie(admin), "{\"activo\":false}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value(
                            "Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo"));
            assertThat(jdbc.queryForObject("SELECT activo FROM usuarios WHERE id = ?", Boolean.class, usuarioPO)).isTrue();

            jdbc.update("UPDATE proyectos SET estado = 'PAUSADO' WHERE equipo_id = ?::uuid", equipoId);
            mvc.perform(json(patch("/api/v1/desarrolladores/" + po + "/activo").cookie(admin), "{\"activo\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false));
            assertThat(jdbc.queryForObject("SELECT activo FROM usuarios WHERE id = ?", Boolean.class, usuarioPO)).isFalse();
            assertThat(consultas.activo(UUID.fromString(equipoId))).isEmpty();
        }
    }

    // ---------------------------------------------------------------- HU-26 · Equipos

    @Nested
    class Equipos {

        @Test
        void creaElEquipoYLaCuentaDelProductOwnerConContrasenaTemporalSoloPorCorreo() throws Exception {
            Cookie admin = sesionAdmin();
            String correoPO = correoNuevo();
            String po = registrar(admin, "Andrea Quiroga Vela", correoPO, "FULL_STACK");
            String a = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            String b = registrar(admin, "Elena Sáenz Mora", correoNuevo(), "FULL_STACK");

            String respuesta = mvc.perform(json(post("/api/v1/equipos").cookie(admin),
                            equipo(po, null, fila(a, "QA"), fila(b, "BACKEND"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.productOwner.nombre").value("Andrea Quiroga Vela"))
                    .andExpect(jsonPath("$.integrantes", hasSize(2)))
                    .andExpect(jsonPath("$.maxProyectos").value(3))
                    .andExpect(jsonPath("$.proyectosActivos").value(0))
                    .andReturn().getResponse().getContentAsString();

            String clave = claveEnviadaA(correoPO);
            assertThat(respuesta).doesNotContain(clave).doesNotContainIgnoringCase("password");
            assertThat(jdbc.queryForObject("""
                    SELECT r.codigo || '/' || u.debe_cambiar_password FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE u.email = ?
                    """, String.class, correoPO)).isEqualTo("PRODUCT_OWNER/true");

            // Entra con la temporal, se le pide cambiarla y después entra al panel
            mvc.perform(json(post("/api/v1/auth/login"), login(correoPO, clave)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.debeCambiarPassword").value(true));
        }

        @Test
        void cambiarElProductOwnerDaCuentaAlNuevoYBloqueaAlAnterior() throws Exception {
            Cookie admin = sesionAdmin();
            String correoViejo = correoNuevo();
            String correoNuevo = correoNuevo();
            String viejo = registrar(admin, "Andrea Quiroga Vela", correoViejo, "BACKEND");
            String nuevo = registrar(admin, "Julián Ocampo Rey", correoNuevo, "FRONTEND");
            String a = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            String equipoId = crearEquipo(admin, viejo, fila(a, "QA"), fila(nuevo, "FRONTEND"));

            mvc.perform(json(put("/api/v1/equipos/" + equipoId).cookie(admin), equipo(nuevo, null, fila(a, "QA"), fila(viejo, "BACKEND"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productOwner.nombre").value("Julián Ocampo Rey"))
                    .andExpect(jsonPath("$.integrantes[?(@.nombre=='Andrea Quiroga Vela')].rol").value("BACKEND"));

            claveEnviadaA(correoNuevo);
            assertThat(jdbc.queryForObject("SELECT activo FROM usuarios WHERE email = ?", Boolean.class, correoViejo)).isFalse();
            assertThat(jdbc.queryForObject("SELECT activo FROM usuarios WHERE email = ?", Boolean.class, correoNuevo)).isTrue();
            assertThat(consultas.productOwner(UUID.fromString(equipoId))).get()
                    .extracting(EquipoConsultas.ProductOwner::nombre).isEqualTo("Julián Ocampo Rey");
        }

        @Test
        void sinIntegrantesOConFilasVaciasDa422() throws Exception {
            Cookie admin = sesionAdmin();
            String po = registrar(admin, "Andrea Quiroga Vela", correoNuevo(), "BACKEND");
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("integrantes"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(
                            "Agrega al menos un integrante además del desarrollador (Product Owner)"));
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null, "{\"desarrolladorId\":null,\"rol\":null}")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("integrantes"));
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), "{\"integrantes\":[]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[?(@.campo=='productOwnerId')]").isNotEmpty());
        }

        @Test
        void unRolQueNoCorrespondeALaEspecialidadDa422() throws Exception {
            Cookie admin = sesionAdmin();
            String po = registrar(admin, "Andrea Quiroga Vela", correoNuevo(), "BACKEND");
            String qa = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null, fila(qa, "BACKEND"))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("El rol no corresponde a la especialidad del desarrollador"));
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null, fila(qa, "PRODUCT_OWNER"))))
                    .andExpect(status().isUnprocessableEntity());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos e JOIN integrantes_equipo i ON i.equipo_id = e.id WHERE i.desarrollador_id = ?::uuid",
                    Integer.class, po)).isZero();
        }

        @Test
        void unDesarrolladorInactivoNoSeAsigna() throws Exception {
            Cookie admin = sesionAdmin();
            String po = registrar(admin, "Andrea Quiroga Vela", correoNuevo(), "BACKEND");
            String qa = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            mvc.perform(json(patch("/api/v1/desarrolladores/" + qa + "/activo").cookie(admin), "{\"activo\":false}")).andExpect(status().isOk());
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null, fila(qa, "QA"))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Mario Bernal Ríos está inactivo"));
        }

        @Test
        void unDesarrolladorYaEnOtroEquipoDa409() throws Exception {
            Cookie admin = sesionAdmin();
            String po1 = registrar(admin, "Andrea Quiroga Vela", correoNuevo(), "BACKEND");
            String po2 = registrar(admin, "Julián Ocampo Rey", correoNuevo(), "FRONTEND");
            String qa = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            crearEquipo(admin, po1, fila(qa, "QA"));
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po2, null, fila(qa, "QA"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este desarrollador ya está en otro equipo"));
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po1, null, fila(po2, "FRONTEND"))))
                    .andExpect(status().isConflict());
        }

        @Test
        void elCupoVaDe1A3YNoBajaDeLosProyectosActivos() throws Exception {
            Cookie admin = sesionAdmin();
            String po = registrar(admin, "Andrea Quiroga Vela", correoNuevo(), "BACKEND");
            String qa = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, 4, fila(qa, "QA"))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("maxProyectos"));
            String equipoId = mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, 2, fila(qa, "QA"))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.maxProyectos").value(2))
                    .andReturn().getResponse().getContentAsString().replaceAll(".*\"equipoId\":\"([^\"]+)\".*", "$1");
            assertThat(consultas.cupoDisponible(UUID.fromString(equipoId))).isEqualTo(2);
            proyectoActivo(UUID.fromString(equipoId));
            assertThat(consultas.cupoDisponible(UUID.fromString(equipoId))).isEqualTo(1);
            mvc.perform(json(put("/api/v1/equipos/" + equipoId).cookie(admin), equipo(po, 1, fila(qa, "QA"))))
                    .andExpect(status().isOk());
            assertThat(consultas.cupoDisponible(UUID.fromString(equipoId))).isZero();
        }

        @Test
        void agregaYQuitaIntegrantesSinDejarElEquipoSinNadie() throws Exception {
            Cookie admin = sesionAdmin();
            String po = registrar(admin, "Andrea Quiroga Vela", correoNuevo(), "BACKEND");
            String qa = registrar(admin, "Mario Bernal Ríos", correoNuevo(), "QA");
            String ux = registrar(admin, "Elena Sáenz Mora", correoNuevo(), "UX_UI");
            String equipoId = crearEquipo(admin, po, fila(qa, "QA"));

            mvc.perform(json(post("/api/v1/equipos/" + equipoId + "/integrantes").cookie(admin), fila(ux, "UX_UI")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.integrantes", hasSize(2)));
            mvc.perform(delete("/api/v1/equipos/" + equipoId + "/integrantes/" + qa).cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.integrantes", hasSize(1)));
            mvc.perform(delete("/api/v1/equipos/" + equipoId + "/integrantes/" + ux).cookie(admin))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value(
                            "El equipo de Andrea Quiroga Vela quedaría sin integrantes; agrega otro antes de quitarlo"));
            mvc.perform(get("/api/v1/equipos/" + UUID.randomUUID()).cookie(admin)).andExpect(status().isNotFound());
        }

        @Test
        void laBaseDeDatosRechazaUnSegundoProductOwnerYUnRolIncorrecto() {
            UUID equipo = jdbc.queryForObject("INSERT INTO equipos DEFAULT VALUES RETURNING id", UUID.class);
            UUID cuenta = jdbc.queryForObject("""
                    INSERT INTO usuarios (rol_id, nombre, email, celular, password_hash, activo)
                    SELECT id, 'Product Owner Uno', ?, '3001234567', 'x', true FROM roles WHERE codigo = 'PRODUCT_OWNER' RETURNING id
                    """, UUID.class, correoNuevo());
            UUID a = dev("QA", cuenta);
            UUID b = dev("BACKEND", null);
            jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, 'PRODUCT_OWNER')", equipo, a);
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class, () ->
                    jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, 'PRODUCT_OWNER')", equipo, b));
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class, () ->
                    jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, 'QA')", equipo, b));
        }
    }

    // ---------------------------------------------------------------- Apoyo

    private UUID dev(String especialidad, UUID usuario) {
        return jdbc.queryForObject("""
                INSERT INTO desarrolladores (nombre, correo, celular, especialidad, usuario_id)
                VALUES ('Desarrollador de Prueba', ?, '3001234567', ?::especialidad, ?) RETURNING id
                """, UUID.class, correoNuevo(), especialidad, usuario);
    }

    /** Proyecto ACTIVO del equipo; sin las llaves foráneas (solo cuenta para el cupo y la desactivación). */
    private void proyectoActivo(UUID equipo) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (var st = con.createStatement()) {
                st.execute("SET session_replication_role = replica");
                st.execute("INSERT INTO proyectos (id, codigo, nombre, alcance, cita_id, cliente_id, servicio_id, equipo_id, creado_por,"
                        + " fecha_inicio, fecha_estimada_entrega) VALUES (gen_random_uuid(), 'P-" + (int) (Math.random() * 1_000_000)
                        + "', 'Proyecto de prueba', 'Alcance de prueba con más de veinte caracteres', gen_random_uuid(), gen_random_uuid(),"
                        + " gen_random_uuid(), '" + equipo + "', gen_random_uuid(), current_date, current_date + 30)");
                st.execute("SET session_replication_role = DEFAULT");
            }
            return null;
        });
    }

    private String registrar(Cookie admin, String nombre, String correo, String especialidad) throws Exception {
        String cuerpo = mvc.perform(json(post("/api/v1/desarrolladores").cookie(admin), cuerpo(nombre, "3001234567", correo, especialidad)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"desarrolladorId\":\"([^\"]+)\"").matcher(cuerpo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private String crearEquipo(Cookie admin, String po, String... integrantes) throws Exception {
        String cuerpo = mvc.perform(json(post("/api/v1/equipos").cookie(admin), equipo(po, null, integrantes)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"equipoId\":\"([^\"]+)\"").matcher(cuerpo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static String cuerpo(String nombre, String celular, String correo, String especialidad) {
        return "{\"nombre\":\"%s\",\"celular\":\"%s\",\"correo\":\"%s\",\"especialidad\":\"%s\"}".formatted(nombre, celular, correo, especialidad);
    }

    private static String fila(String id, String rol) {
        return "{\"desarrolladorId\":\"%s\",\"rol\":\"%s\"}".formatted(id, rol);
    }

    private static String equipo(String po, Integer cupo, String... integrantes) {
        return "{\"productOwnerId\":\"%s\",\"integrantes\":[%s]%s}".formatted(po, String.join(",", integrantes),
                cupo == null ? "" : ",\"maxProyectos\":" + cupo);
    }

    private static String correoNuevo() {
        return "esp-" + UUID.randomUUID().toString().substring(0, 12) + "@coretechsolutions.co";
    }

    private String crearUsuario(String rol) {
        String email = correoNuevo();
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, true, now() FROM roles WHERE codigo = ?
                """, email, codificador.encode(CLAVE), rol);
        return email;
    }

    private Cookie sesionAdmin() throws Exception {
        return sesionDe(crearUsuario("ADMINISTRADOR"));
    }

    private Cookie sesionDe(String email) throws Exception {
        Cookie cookie = mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String claveEnviadaA(String email) {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo, timeout(2000)).enviar(eq(email), anyString(), texto.capture());
        Matcher m = Pattern.compile("Contraseña temporal: (Ct-\\S+)").matcher(texto.getValue());
        assertThat(m.find()).as("contraseña temporal en el correo").isTrue();
        return m.group(1);
    }

    private static String login(String email, String clave) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"recordarme\":false}".formatted(email, clave);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }
}
