package co.coretech.portal.acceso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
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
import co.coretech.portal.compartido.service.CorreoSaliente;
import jakarta.servlet.http.Cookie;

/**
 * Administración de punta a punta contra PostgreSQL 15: usuarios (HU-20), roles y permisos (HU-21) y
 * auditoría (HU-22). Una prueba por cada fila de "Excepciones" de las fichas, más los permisos (403).
 * <p>
 * Los proyectos y equipos se simulan ({@link ProyectosDeUsuarios}, {@link EquiposDeUsuarios}): sin
 * configurar devuelven mapas vacíos, es decir, nadie tiene proyectos activos. Las pruebas de permisos
 * dejan la matriz como estaba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class AdministracionTest {

    static final String CLAVE = "Segura2026!";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @MockitoBean
    CorreoSaliente correo;

    @MockitoBean
    ProyectosDeUsuarios proyectos;

    @MockitoBean
    EquiposDeUsuarios equipos;

    // ---------------------------------------------------------------- HU-20 · Gestionar las cuentas de usuario

    @Nested
    class Cuentas {

        @Test
        void creaUnaCuentaDeAdministradorConContrasenaTemporalPorCorreo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String email = correoCorporativo();

            MvcResult r = mvc.perform(json(post("/api/v1/usuarios").cookie(admin), cuenta("Daniela Castro", "3001234567", email.toUpperCase())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.debeCambiarPassword").value(true))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andReturn();
            // La contraseña temporal nunca sale en la respuesta: solo en el correo (RF-05)
            assertThat(r.getResponse().getContentAsString()).doesNotContain("Ct-");

            String temporal = claveEnviadaA(email);
            mvc.perform(json(post("/api/v1/auth/login"), login(email, temporal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.debeCambiarPassword").value(true));
            assertThat(auditoria("Creó una cuenta de administrador", idDe(email))).isEqualTo(1);
        }

        @Test
        void correoRepetidoDa409() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String existente = crearUsuario("ADMINISTRADOR");

            mvc.perform(json(post("/api/v1/usuarios").cookie(admin), cuenta("Daniela Castro", "3001234567", existente.toUpperCase())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].campo").value("email"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ya existe una cuenta con este correo"));
        }

        @Test
        void elPersonalUsaElCorreoCorporativo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(json(post("/api/v1/usuarios").cookie(admin), cuenta("Daniela Castro", "3001234567", "daniela@gmail.com")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("email"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Usa el correo corporativo que termina en @coretechsolutions.co."));
        }

        @Test
        void datosInvalidosDan422ConUnErrorPorCampo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(json(post("/api/v1/usuarios").cookie(admin), cuenta("Ana", "2123", "")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(3)))
                    .andExpect(jsonPath("$.errores[?(@.campo=='nombre')].mensaje").value("Escribe nombre y apellido."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='celular')].mensaje").value("Escribe un celular de 10 dígitos que empiece por 3."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='email')].mensaje").value("Escribe el correo."));
        }

        @Test
        void editaUnClienteYQuedaEnLaAuditoriaConElValorAnteriorYElNuevo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String cliente = crearUsuario("CLIENTE");
            String nuevo = "nuevo-" + cliente;

            mvc.perform(json(put("/api/v1/usuarios/{id}", idDe(cliente)).cookie(admin), cuenta("Laura Gómez Ruiz", "3001234567", nuevo)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Laura Gómez Ruiz"))
                    .andExpect(jsonPath("$.email").value(nuevo));

            String cambios = jdbc.queryForObject("SELECT cambios::text FROM auditoria WHERE accion = 'Editó los datos de una cuenta' AND entidad_id = ?",
                    String.class, idDe(nuevo));
            assertThat(cambios).contains("\"nombre\": {\"nuevo\": \"Laura Gómez Ruiz\", \"anterior\": \"Usuario de Prueba\"}")
                    .contains("\"email\"").doesNotContain("celular");
        }

        @Test
        void unAdministradorNoPuedeDesactivarseASiMismo() throws Exception {
            String email = crearUsuario("ADMINISTRADOR");
            Cookie admin = sesionDe(email);

            mvc.perform(json(patch("/api/v1/usuarios/{id}/activo", idDe(email)).cookie(admin), "{\"activo\":false}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("No puedes desactivar tu propia cuenta"));
        }

        @Test
        void desactivarCortaLaSesionHastaQueSeReactive() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String cliente = crearUsuario("CLIENTE");
            Cookie sesionCliente = sesionDe(cliente);

            mvc.perform(json(patch("/api/v1/usuarios/{id}/activo", idDe(cliente)).cookie(admin), "{\"activo\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false));
            mvc.perform(get("/api/v1/auth/yo").cookie(sesionCliente)).andExpect(status().isUnauthorized());
            assertThat(auditoria("Desactivó la cuenta", idDe(cliente))).isEqualTo(1);

            mvc.perform(json(patch("/api/v1/usuarios/{id}/activo", idDe(cliente)).cookie(admin), "{\"activo\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(true));
            mvc.perform(get("/api/v1/auth/yo").cookie(sesionCliente)).andExpect(status().isOk());
        }

        @Test
        void desactivarUnProductOwnerConProyectosActivosDa409() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String po = crearUsuario("PRODUCT_OWNER");
            UUID equipo = UUID.randomUUID();
            when(equipos.porProductOwner()).thenReturn(Map.of(UUID.fromString(idDe(po)), new EquiposDeUsuarios.Equipo(equipo, 3)));
            when(proyectos.activosPorEquipo()).thenReturn(Map.of(equipo, 2));

            mvc.perform(json(patch("/api/v1/usuarios/{id}/activo", idDe(po)).cookie(admin), "{\"activo\":false}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo"));
            // La lista muestra su equipo con el cupo (columna Actividad)
            mvc.perform(get("/api/v1/usuarios/{id}", idDe(po)).cookie(admin))
                    .andExpect(jsonPath("$.actividad.equipo.proyectosActivos").value(2))
                    .andExpect(jsonPath("$.actividad.equipo.maxProyectos").value(3));
        }

        @Test
        void desbloqueaUnaCuentaBloqueadaPorIntentosFallidos() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String cliente = crearUsuario("CLIENTE");
            mvc.perform(post("/api/v1/usuarios/{id}/desbloquear", idDe(cliente)).cookie(admin))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Esta cuenta no está bloqueada"));

            jdbc.update("UPDATE usuarios SET intentos_fallidos = 5, bloqueado_hasta = now() + interval '15 minutes' WHERE email = ?", cliente);
            mvc.perform(json(post("/api/v1/auth/login"), login(cliente, CLAVE))).andExpect(status().isLocked());

            mvc.perform(post("/api/v1/usuarios/{id}/desbloquear", idDe(cliente)).cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bloqueadaHasta").doesNotExist());
            mvc.perform(json(post("/api/v1/auth/login"), login(cliente, CLAVE))).andExpect(status().isOk());
        }

        @Test
        void anonimizaLosDatosDeUnClienteYConservaElHistorial() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String cliente = crearUsuario("CLIENTE");
            String id = idDe(cliente);

            mvc.perform(post("/api/v1/usuarios/{id}/anonimizar", id).cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Cliente anónimo"))
                    .andExpect(jsonPath("$.anonimizada").value(true))
                    .andExpect(jsonPath("$.activo").value(false));

            Map<String, Object> fila = jdbc.queryForMap("SELECT nombre, email, celular, empresa, activo, anonimizado_at FROM usuarios WHERE id = ?::uuid", id);
            assertThat(fila.get("email")).isEqualTo("anonimo-" + id.replace("-", "") + "@anonimo.invalid");
            assertThat(fila.get("celular")).isEqualTo("3000000000");
            assertThat(fila.get("empresa")).isNull();
            assertThat(fila.get("activo")).isEqualTo(false);
            assertThat(fila.get("anonimizado_at")).isNotNull();
            // Ya no puede entrar con su correo, y la auditoría no guarda los datos que se borraron
            mvc.perform(json(post("/api/v1/auth/login"), login(cliente, CLAVE))).andExpect(status().isUnauthorized());
            String cambios = jdbc.queryForObject("SELECT cambios::text FROM auditoria WHERE accion = 'Anonimizó los datos de un cliente' AND entidad_id = ?", String.class, id);
            assertThat(cambios).doesNotContain(cliente);

            // Dos veces, editarla o reactivarla: 409
            mvc.perform(post("/api/v1/usuarios/{id}/anonimizar", id).cookie(admin)).andExpect(status().isConflict());
            mvc.perform(json(put("/api/v1/usuarios/{id}", id).cookie(admin), cuenta("Otra Persona", "3001234567", correoCliente())))
                    .andExpect(status().isConflict());
            mvc.perform(json(patch("/api/v1/usuarios/{id}/activo", id).cookie(admin), "{\"activo\":true}"))
                    .andExpect(status().isConflict());
        }

        @Test
        void soloSeAnonimizaUnCliente() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String otroAdmin = crearUsuario("ADMINISTRADOR");

            mvc.perform(post("/api/v1/usuarios/{id}/anonimizar", idDe(otroAdmin)).cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Solo se anonimizan los datos de un cliente"));
        }

        @Test
        void listaTodasLasCuentasConSuRol() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String cliente = crearUsuario("CLIENTE");

            mvc.perform(get("/api/v1/usuarios").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].rol").value("ADMINISTRADOR"))
                    .andExpect(jsonPath("$[*].email", hasItem(cliente)))
                    // Nunca viaja la contraseña ni su hash
                    .andExpect(content().string(not(org.hamcrest.Matchers.containsString("$2a$"))));
        }

        @Test
        void unUsuarioQueNoExisteDa404() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            mvc.perform(get("/api/v1/usuarios/{id}", UUID.randomUUID()).cookie(admin))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Usuario no encontrado"));
        }

        @Test
        void sinElPermisoSeguridadCuentasDa403() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            Cookie po = sesionDe(crearUsuario("PRODUCT_OWNER"));
            mvc.perform(get("/api/v1/usuarios").cookie(cliente)).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/v1/usuarios").cookie(po), cuenta("Daniela Castro", "3001234567", correoCorporativo())))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- HU-21 · Configurar roles y permisos

    @Nested
    class Permisos {

        @Test
        void laMatrizEsLaDelPrototipo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(get("/api/v1/roles/permisos").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles[*].codigo").value(org.hamcrest.Matchers.contains("CLIENTE", "PRODUCT_OWNER", "ADMINISTRADOR")))
                    .andExpect(jsonPath("$.permisos", hasSize(22)))
                    .andExpect(jsonPath("$.permisos[0].nombre").value("Agendar citas"))
                    .andExpect(jsonPath("$.permisos[0].regla").value("RN-01 a RN-03"))
                    .andExpect(jsonPath("$.permisos[0].grupo").value("Citas"))
                    // Agendar citas: editable para cliente y Product Owner, fijo para el administrador
                    .andExpect(jsonPath("$.permisos[0].celdas[0].permitido").value(true))
                    .andExpect(jsonPath("$.permisos[0].celdas[0].fijo").value(false))
                    .andExpect(jsonPath("$.permisos[0].celdas[2].motivo").value("El administrador siempre lo tiene"))
                    // Registrar avances: vetado al administrador (RN-07)
                    .andExpect(jsonPath("$.permisos[?(@.codigo=='proyectos.registrar_avance')].celdas[2].permitido").value(false))
                    .andExpect(jsonPath("$.permisos[?(@.codigo=='proyectos.registrar_avance')].celdas[2].motivo").value("Fijo por RN-07"))
                    .andExpect(jsonPath("$.permisos[?(@.codigo=='proyectos.registrar_avance')].celdas[1].motivo").value("Fijo por RN-07"));
        }

        @Test
        void quitarUnPermisoBloqueadoDa422() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(json(put("/api/v1/roles/permisos").cookie(admin), cambio("CLIENTE", "proyectos.ver_propios", false)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("permisos"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este permiso lo exige una regla del negocio"));
            mvc.perform(json(put("/api/v1/roles/permisos").cookie(admin), cambio("ADMINISTRADOR", "seguridad.permisos", false)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void darUnPermisoVetadoDa422() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(json(put("/api/v1/roles/permisos").cookie(admin), cambio("CLIENTE", "citas.ver_todas", true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Una regla del negocio no permite dar este permiso a este rol"));
        }

        @Test
        void unaCombinacionQueNoExisteOSinCambiosDa422() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(json(put("/api/v1/roles/permisos").cookie(admin), cambio("INVITADO", "citas.agendar", true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ese rol o ese permiso no existe"));
            mvc.perform(json(put("/api/v1/roles/permisos").cookie(admin), cambio("CLIENTE", "citas.agendar", true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("No hay cambios por guardar."));
        }

        @Test
        void unCambioAplicaDeInmediatoYQuedaEnLaAuditoria() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            Cookie po = sesionDe(crearUsuario("PRODUCT_OWNER"));
            try {
                mvc.perform(json(put("/api/v1/roles/permisos").cookie(admin), cambio("PRODUCT_OWNER", "citas.ver_todas", true)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.permisos[?(@.codigo=='citas.ver_todas')].celdas[1].permitido").value(true));
                // D-35: sin volver a iniciar sesión
                mvc.perform(get("/api/v1/auth/yo").cookie(po))
                        .andExpect(jsonPath("$.permisos", hasItem("citas.ver_todas")));
                String cambios = jdbc.queryForObject("""
                        SELECT cambios::text FROM auditoria WHERE categoria = 'permisos' AND entidad_id = 'PRODUCT_OWNER'
                         ORDER BY id DESC LIMIT 1
                        """, String.class);
                assertThat(cambios).isEqualTo("{\"Ver todas las citas\": {\"nuevo\": true, \"anterior\": false}}");
            } finally {
                mvc.perform(json(put("/api/v1/roles/permisos").cookie(admin), cambio("PRODUCT_OWNER", "citas.ver_todas", false)))
                        .andExpect(status().isOk());
            }
            mvc.perform(get("/api/v1/auth/yo").cookie(po))
                    .andExpect(jsonPath("$.permisos", not(hasItem("citas.ver_todas"))));
        }

        @Test
        void sinElPermisoSeguridadPermisosDa403() throws Exception {
            Cookie po = sesionDe(crearUsuario("PRODUCT_OWNER"));
            mvc.perform(get("/api/v1/roles/permisos").cookie(po)).andExpect(status().isForbidden());
            mvc.perform(json(put("/api/v1/roles/permisos").cookie(po), cambio("PRODUCT_OWNER", "citas.ver_todas", true)))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- HU-22 · Consultar la auditoría

    @Nested
    class Auditoria {

        @Test
        void filtraPorUsuarioYPaginaDelMasRecienteAlMasAntiguo() throws Exception {
            String emailAdmin = crearUsuario("ADMINISTRADOR");
            Cookie admin = sesionDe(emailAdmin);
            String idAdmin = idDe(emailAdmin);
            for (int i = 0; i < 3; i++) {
                String cliente = crearUsuario("CLIENTE");
                mvc.perform(json(patch("/api/v1/usuarios/{id}/activo", idDe(cliente)).cookie(admin), "{\"activo\":false}"))
                        .andExpect(status().isOk());
            }

            // Inició sesión + 3 desactivaciones
            mvc.perform(get("/api/v1/auditoria").param("usuarioId", idAdmin).param("tamano", "2").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(4))
                    .andExpect(jsonPath("$.totalPaginas").value(2))
                    .andExpect(jsonPath("$.registros", hasSize(2)))
                    .andExpect(jsonPath("$.registros[0].accion").value("Desactivó la cuenta"))
                    .andExpect(jsonPath("$.registros[0].usuario.rol").value("ADMINISTRADOR"))
                    .andExpect(jsonPath("$.registros[0].cambios[0].campo").value("activo"))
                    .andExpect(jsonPath("$.registros[0].cambios[0].anterior").value(true))
                    .andExpect(jsonPath("$.registros[0].cambios[0].nuevo").value(false))
                    .andExpect(jsonPath("$.porCategoria.usuarios").value(3))
                    .andExpect(jsonPath("$.porCategoria.sesiones").value(1));
            mvc.perform(get("/api/v1/auditoria").param("usuarioId", idAdmin).param("categoria", "sesiones").cookie(admin))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.registros[0].accion").value("Inició sesión"));
            mvc.perform(get("/api/v1/auditoria").param("usuarioId", idAdmin).param("pagina", "1").param("tamano", "2").cookie(admin))
                    .andExpect(jsonPath("$.registros[1].accion").value("Inició sesión"));
        }

        @Test
        void buscaPorTextoYPorFechas() throws Exception {
            String emailAdmin = crearUsuario("ADMINISTRADOR");
            Cookie admin = sesionDe(emailAdmin);
            String hoy = LocalDate.now(java.time.ZoneId.of("America/Bogota")).toString();

            mvc.perform(get("/api/v1/auditoria").param("usuarioId", idDe(emailAdmin)).param("q", "inició")
                            .param("desde", hoy).param("hasta", hoy).cookie(admin))
                    .andExpect(jsonPath("$.total").value(1));
            mvc.perform(get("/api/v1/auditoria").param("usuarioId", idDe(emailAdmin)).param("desde", "2020-01-01").param("hasta", "2020-01-31").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.registros", hasSize(0)));
        }

        @Test
        void filtrosInvalidosDan422() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));

            mvc.perform(get("/api/v1/auditoria").param("desde", "2026-11-10").param("hasta", "2026-11-01").cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("hasta"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La fecha final debe ser igual o posterior a la inicial."));
            mvc.perform(get("/api/v1/auditoria").param("desde", "2025-01-01").param("hasta", "2026-06-01").cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Elige un rango de máximo un año."));
            mvc.perform(get("/api/v1/auditoria").param("categoria", "pagos").param("usuarioId", UUID.randomUUID().toString()).cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(2)))
                    .andExpect(jsonPath("$.errores[?(@.campo=='categoria')].mensaje").value("Elige una categoría de la lista."))
                    .andExpect(jsonPath("$.errores[?(@.campo=='usuarioId')].mensaje").value("Elige un usuario de la lista."));
        }

        @Test
        void exportaACsvYLoDejaEnLaAuditoria() throws Exception {
            String emailAdmin = crearUsuario("ADMINISTRADOR");
            Cookie admin = sesionDe(emailAdmin);

            MvcResult r = mvc.perform(get("/api/v1/auditoria/exportar").param("usuarioId", idDe(emailAdmin)).cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("auditoria-coretech-")))
                    .andReturn();
            String csv = new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            assertThat(csv).startsWith("﻿\"Fecha\",\"Usuario\",\"Acción\",\"Elemento\",\"Módulo\",\"Cambios\",\"Origen\"");
            assertThat(csv).contains("\"Usuario de Prueba (Administrador)\",\"Inició sesión\"");
            assertThat(auditoria("Exportó la auditoría", null, idDe(emailAdmin))).isEqualTo(1);
        }

        @Test
        void elResumenTraeLasCifrasYLasOpcionesDeLosFiltros() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            mvc.perform(get("/api/v1/auditoria/resumen").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventosHoy").isNumber())
                    .andExpect(jsonPath("$.categorias", hasSize(9)))
                    .andExpect(jsonPath("$.categorias[6].nombre").value("Sitio web"))
                    .andExpect(jsonPath("$.usuarios[*].nombre", hasItem("Usuario de Prueba")));
        }

        @Test
        void laAuditoriaNoSeEditaNiSeBorra() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            // No hay endpoint para cambiarla…
            mvc.perform(delete("/api/v1/auditoria").cookie(admin)).andExpect(status().is4xxClientError());
            mvc.perform(json(put("/api/v1/auditoria").cookie(admin), "{}")).andExpect(status().is4xxClientError());
            // …y la base la protege (tg_auditoria_inmutable)
            assertThatThrownBy(() -> jdbc.update("UPDATE auditoria SET accion = 'Otra' WHERE id = (SELECT max(id) FROM auditoria)"))
                    .hasMessageContaining("auditoria");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria WHERE id = (SELECT max(id) FROM auditoria)"))
                    .hasMessageContaining("auditoria");
        }

        @Test
        void sinElPermisoSeguridadAuditoriaDa403() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(get("/api/v1/auditoria").cookie(cliente)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/auditoria/exportar").cookie(cliente)).andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- Ayudas

    private static String correoCliente() {
        return "prueba-" + UUID.randomUUID().toString().substring(0, 12) + "@correo.com";
    }

    private static String correoCorporativo() {
        return "prueba-" + UUID.randomUUID().toString().substring(0, 12) + "@coretechsolutions.co";
    }

    /** Crea una cuenta activa con la contraseña {@link #CLAVE}; el personal lleva el dominio de CoreTech. */
    private String crearUsuario(String rol) {
        String email = rol.equals("CLIENTE") ? correoCliente() : correoCorporativo();
        jdbc.update("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', ?, true, now() FROM roles WHERE codigo = ?
                """, email, codificador.encode(CLAVE), rol);
        return email;
    }

    private String idDe(String email) {
        return jdbc.queryForObject("SELECT id::text FROM usuarios WHERE email = ?", String.class, email);
    }

    private int auditoria(String accion, String entidadId) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = ? AND entidad_id = ?", Integer.class, accion, entidadId);
    }

    private int auditoria(String accion, String entidadId, String usuarioId) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = ? AND entidad_id IS NOT DISTINCT FROM ? AND usuario_id = ?::uuid",
                Integer.class, accion, entidadId, usuarioId);
    }

    private Cookie sesionDe(String email) throws Exception {
        Cookie cookie = mvc.perform(json(post("/api/v1/auth/login"), login(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
        assertThat(cookie).as("cookie de sesión").isNotNull();
        return cookie;
    }

    private String claveEnviadaA(String email) {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correo, timeout(2000)).enviar(eq(email), anyString(), texto.capture());
        Matcher m = Pattern.compile("Contraseña temporal: (Ct-\\S+)").matcher(texto.getValue());
        assertThat(m.find()).as("contraseña temporal en el correo").isTrue();
        return m.group(1);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static String cuenta(String nombre, String celular, String email) {
        return """
                {"nombre":"%s","celular":"%s","email":"%s"}
                """.formatted(nombre, celular, email);
    }

    private static String cambio(String rol, String permiso, boolean permitido) {
        return """
                {"cambios":[{"rol":"%s","permiso":"%s","permitido":%s}]}
                """.formatted(rol, permiso, permitido);
    }

    private static String login(String email, String password) {
        return """
                {"email":"%s","password":"%s","recordarme":false}
                """.formatted(email, password);
    }
}
