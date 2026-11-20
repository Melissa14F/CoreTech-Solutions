package co.coretech.portal.proyectos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/**
 * Plantilla de fases de cada servicio contra PostgreSQL 15 (HU-27, RN-27, RN-28): los límites de fases (1, 2, 8 y 9)
 * y de pasos (12 y 13), el nombre repetido, la fase sin aprobación del cliente, los permisos y que la base también
 * rechace lo que el servicio no revise. Cada prueba crea su propio servicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class PlantillasTest {

    static final String CLAVE = "Segura2026!";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    // ---------------------------------------------------------------- Camino feliz

    @Test
    void guardaLaPlantillaCompletaYLaDevuelveEnElOrdenDeLaLista() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();

        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Diseño", true, 2), fase("Entrega", false, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases.length()").value(2))
                .andExpect(jsonPath("$.fases[0].orden").value(1))
                .andExpect(jsonPath("$.fases[0].nombre").value("Diseño"))
                .andExpect(jsonPath("$.fases[0].apruebaCliente").value(true))
                .andExpect(jsonPath("$.fases[0].pasos[1].orden").value(2))
                .andExpect(jsonPath("$.fases[1].nombre").value("Entrega"));

        mvc.perform(get(ruta(servicio)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases[1].orden").value(2))
                .andExpect(jsonPath("$.fases[0].pasos[0].titulo").value("Paso 1 de Diseño"));
    }

    @Test
    void reemplazaLaPlantillaAnteriorYLaDejaEnLaAuditoria() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Uno", true, 1), fase("Dos", false, 1))))
                .andExpect(status().isOk());

        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Solo", true, 1), fase("Otra", false, 2), fase("Final", false, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases.length()").value(3));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_fases WHERE servicio_id = ?", Integer.class, servicio)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_pasos p JOIN plantilla_fases f ON f.id = p.plantilla_fase_id WHERE f.servicio_id = ?",
                Integer.class, servicio)).isEqualTo(4);
        String cambios = jdbc.queryForObject("""
                SELECT cambios::text FROM auditoria WHERE accion = 'Guardó la plantilla de fases de un servicio'
                   AND entidad_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, servicio.toString());
        assertThat(cambios).contains("Solo").contains("Uno");
    }

    @Test
    void aceptaElMinimoYElMaximoDeFasesYDePasos() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Una", true, 1), fase("Dos", false, 12))))
                .andExpect(status().isOk());
        List<String> fases = new ArrayList<>(IntStream.rangeClosed(1, 8).mapToObj(n -> fase("Fase " + n, n == 1, 1)).toList());
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fases.toArray(String[]::new))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases.length()").value(8));
    }

    // ---------------------------------------------------------------- Límites y excepciones (RN-28)

    @Test
    void conUnaFaseNoGuardaNadaYLaPlantillaAnteriorQueda() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Uno", true, 1), fase("Dos", false, 1))))
                .andExpect(status().isOk());

        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Sola", true, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fases"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El plan necesita al menos 2 fases"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_fases WHERE servicio_id = ?", Integer.class, servicio)).isEqualTo(2);
    }

    @Test
    void conNueveFasesDa422() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        String[] fases = IntStream.rangeClosed(1, 9).mapToObj(n -> fase("Fase " + n, n == 1, 1)).toArray(String[]::new);
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fases)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El plan puede tener máximo 8 fases"));
    }

    @Test
    void conTrecePasosEnUnaFaseDa422EnLaFase() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Uno", true, 1), fase("Dos", false, 13))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fases[1].pasos"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Cada fase puede tener máximo 12 pasos."));
    }

    @Test
    void dosFasesConElMismoNombreSinImportarMayusculasDan422() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Pruebas", true, 1), fase("  PRUEBAS ", false, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fases[1].nombre"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ya hay una fase con ese nombre en este servicio"));
    }

    @Test
    void sinNingunaFaseQueApruebeElClienteDa422() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Uno", false, 1), fase("Dos", false, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fases"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Marca al menos una fase que apruebe el cliente"));
    }

    @Test
    void unaFaseSinNombreOSinPasosDa422ConUnMensajePorFase() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        mvc.perform(json(put(ruta(servicio)).cookie(admin), plantilla(fase("Uno", true, 1), fase(" ", false, 0))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[?(@.campo == 'fases')].mensaje").value("Fase 2: completa el nombre y los pasos"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'fases[1].nombre')].mensaje").value("Escribe el nombre de la fase (al menos 3 caracteres)."))
                .andExpect(jsonPath("$.errores[?(@.campo == 'fases[1].pasos')].mensaje").value("Agrega al menos un paso."));
    }

    @Test
    void unTituloDePasoCortoOUnaExplicacionLargaDan422EnElPaso() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID servicio = crearServicio();
        String cuerpo = """
                {"fases":[
                  {"nombre":"Uno","apruebaCliente":true,"pasos":[{"titulo":"ab","explicacion":"%s"}]},
                  {"nombre":"Dos","apruebaCliente":false,"pasos":[{"titulo":"Un paso"}]}]}
                """.formatted("x".repeat(161));
        mvc.perform(json(put(ruta(servicio)).cookie(admin), cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[?(@.campo == 'fases[0].pasos[0].titulo')].mensaje").value("Escribe qué se hace en este paso."))
                .andExpect(jsonPath("$.errores[?(@.campo == 'fases[0].pasos[0].explicacion')].mensaje").value("Escribe máximo 160 caracteres."));
    }

    @Test
    void unaPeticionSinFasesDa422() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        mvc.perform(json(put(ruta(crearServicio())).cookie(admin), "{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El plan necesita al menos 2 fases"));
    }

    // ---------------------------------------------------------------- La base de datos como última barrera (RN-28)

    @Test
    void laBaseRechazaUnaPlantillaConUnaSolaFaseAlConfirmar() {
        UUID servicio = crearServicio();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            UUID fase = UUID.randomUUID();
            jdbc.update("INSERT INTO plantilla_fases (id, servicio_id, orden, nombre, aprueba_cliente) VALUES (?, ?, 1, 'Sola', true)", fase, servicio);
            jdbc.update("INSERT INTO plantilla_pasos (plantilla_fase_id, orden, titulo) VALUES (?, 1, 'Un paso')", fase);
            // Sin transacción cada sentencia se confirma sola: el disparador revisa al confirmar y rechaza una sola fase.
        }).hasMessageContaining("2 a 8 fases");
    }

    // ---------------------------------------------------------------- Servicio inexistente y permisos

    @Test
    void unServicioQueNoExisteDa404() throws Exception {
        Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
        UUID falso = UUID.randomUUID();
        mvc.perform(get(ruta(falso)).cookie(admin)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este servicio ya no está disponible"));
        mvc.perform(json(put(ruta(falso)).cookie(admin), plantilla(fase("Uno", true, 1), fase("Dos", false, 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionDa401() throws Exception {
        UUID servicio = crearServicio();
        mvc.perform(get(ruta(servicio))).andExpect(status().isUnauthorized());
        mvc.perform(json(put(ruta(servicio)), plantilla(fase("Uno", true, 1), fase("Dos", false, 1)))).andExpect(status().isUnauthorized());
    }

    @Test
    void elClienteYElDesarrolladorNoLaVenNiLaCambian() throws Exception {
        UUID servicio = crearServicio();
        for (String rol : List.of("CLIENTE", "PRODUCT_OWNER")) {
            Cookie cookie = sesionDe(crearUsuario(rol));
            mvc.perform(get(ruta(servicio)).cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(json(put(ruta(servicio)).cookie(cookie), plantilla(fase("Uno", true, 1), fase("Dos", false, 1))))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- Ayudas

    private static String ruta(UUID servicio) {
        return "/api/v1/servicios/" + servicio + "/plantilla";
    }

    private static String fase(String nombre, boolean aprueba, int pasos) {
        String listaPasos = IntStream.rangeClosed(1, pasos)
                .mapToObj(n -> "{\"titulo\":\"Paso %d de %s\",\"explicacion\":\"Explicación sencilla\"}".formatted(n, nombre.trim()))
                .reduce((a, b) -> a + "," + b).orElse("");
        return "{\"nombre\":\"%s\",\"queEs\":\"Qué es\",\"apruebaCliente\":%s,\"pasos\":[%s]}".formatted(nombre, aprueba, listaPasos);
    }

    private static String plantilla(String... fases) {
        return "{\"fases\":[" + String.join(",", fases) + "]}";
    }

    private UUID crearServicio() {
        UUID id = UUID.randomUUID();
        String sufijo = id.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO servicios (id, nombre, slug, descripcion_corta, imagen_url, imagen_alt, orden, visible)
                VALUES (?, ?, ?, 'Servicio de prueba para plantillas', 'img/servicios/app-movil.webp', 'Imagen de prueba',
                        (SELECT coalesce(max(orden), 0) + 1 FROM servicios), true)
                """, id, "Plantilla " + sufijo, "plantilla-" + sufijo);
        return id;
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
        return mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s","recordarme":false}
                        """.formatted(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }
}
