package co.coretech.portal.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.catalogo.aplicacion.ServicioCatalogo;
import co.coretech.portal.compartido.PropiedadesCoretech;
import jakarta.servlet.http.Cookie;

/**
 * Catálogo y contenido del sitio contra PostgreSQL 15 (HU-07, HU-18, HU-19, RN-21): una prueba por cada
 * excepción de las fichas, más permisos (401 sin sesión, 403 sin el permiso). Cada prueba crea sus propios
 * servicios con nombres únicos, para no depender del orden de las demás.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class CatalogoTest {

    static final String CLAVE = "Segura2026!";
    static final String GALERIA = "img/servicios/app-movil.webp";
    /** Cabecera de un PNG: basta para reconocerlo por su contenido. */
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R' };

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    TransactionTemplate transaccion;

    @Autowired
    ServicioCatalogo catalogo;

    @Autowired
    PropiedadesCoretech propiedades;

    // ---------------------------------------------------------------- HU-07 · Sitio público

    @Nested
    class SitioPublico {

        @Test
        void sinSesionVeLosServiciosVisiblesEnElOrdenDelCarrusel() throws Exception {
            var resultado = mvc.perform(get("/api/v1/servicios"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].slug", hasItem("tienda-en-linea")))
                    .andReturn();
            List<String> slugs = JsonPath.read(resultado.getResponse().getContentAsString(), "$[*].slug");
            List<String> esperados = jdbc.queryForList("SELECT slug FROM servicios WHERE visible ORDER BY orden", String.class);
            assertThat(slugs).isEqualTo(esperados);
        }

        @Test
        void unServicioOcultoNoApareceEnElSitio() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String id = crearServicio(admin, nombreNuevo(), false);
            mvc.perform(get("/api/v1/servicios"))
                    .andExpect(jsonPath("$[*].servicioId", not(hasItem(id))));
        }

        @Test
        void sinSesionLeeLosTextosDeLaPaginaPrincipal() throws Exception {
            mvc.perform(get("/api/v1/contenido"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.textos['portada-boton']").value("Agendar una cita"))
                    .andExpect(jsonPath("$.textos.length()").value(27));
        }
    }

    // ---------------------------------------------------------------- HU-18 · Administrar los servicios

    @Nested
    class Servicios {

        @Test
        void creaElServicioAlFinalDelCarruselConSuSlugYLoAudita() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            int ultimo = jdbc.queryForObject("SELECT max(orden) FROM servicios", Integer.class);
            String nombre = nombreNuevo();

            mvc.perform(json(post("/api/v1/servicios").cookie(admin), servicio(nombre, true, GALERIA)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nombre").value(nombre))
                    .andExpect(jsonPath("$.slug").value(nombre.toLowerCase().replace(' ', '-')))
                    .andExpect(jsonPath("$.orden").value(ultimo + 1))
                    .andExpect(jsonPath("$.visible").value(true))
                    .andExpect(jsonPath("$.citasUltimos6Meses").value(0));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'Creó un servicio del catálogo' AND categoria = 'sitio'",
                    Integer.class)).isPositive();
        }

        @Test
        void unNombreRepetidoSinDistinguirMayusculasDa409EnElNombre() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            mvc.perform(json(post("/api/v1/servicios").cookie(admin), servicio("PÁGINA WEB CORPORATIVA", true, GALERIA)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ya existe un servicio con este nombre."));
        }

        @Test
        void datosInvalidosDan422ConUnErrorPorCampo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String cuerpo = """
                    {"nombre":"  ","descripcionCorta":"Muy corta","imagenUrl":"https://otro-sitio.com/x.png","imagenAlt":"Corto"}
                    """;
            mvc.perform(json(post("/api/v1/servicios").cookie(admin), cuerpo))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(4)))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'nombre')].mensaje").value("Escribe el nombre del servicio."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'descripcionCorta')].mensaje").value("Escribe al menos 20 caracteres."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'imagenUrl')].mensaje").value("Elige o sube una imagen."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'imagenAlt')].mensaje").value("Escribe al menos 10 caracteres."));
        }

        @Test
        void editaElServicioYGuardaElValorAnteriorEnLaAuditoria() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String id = crearServicio(admin, nombreNuevo(), true);
            String nuevo = nombreNuevo();

            mvc.perform(json(put("/api/v1/servicios/" + id).cookie(admin), servicio(nuevo, true, "img/servicios/tienda-en-linea.webp")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value(nuevo))
                    .andExpect(jsonPath("$.imagenUrl").value("img/servicios/tienda-en-linea.webp"));
            String cambios = jdbc.queryForObject("""
                    SELECT cambios::text FROM auditoria WHERE accion = 'Editó un servicio del catálogo' ORDER BY id DESC LIMIT 1
                    """, String.class);
            assertThat(cambios).contains(nuevo).contains("app-movil.webp");
        }

        @Test
        void ocultarloLoSacaDelSitioYMostrarloLoDevuelve() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String id = crearServicio(admin, nombreNuevo(), true);

            mvc.perform(json(patch("/api/v1/servicios/" + id + "/visible").cookie(admin), "{\"visible\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.visible").value(false));
            mvc.perform(get("/api/v1/servicios")).andExpect(jsonPath("$[*].servicioId", not(hasItem(id))));

            mvc.perform(json(patch("/api/v1/servicios/" + id + "/visible").cookie(admin), "{\"visible\":true}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/servicios")).andExpect(jsonPath("$[*].servicioId", hasItem(id)));
        }

        @Test
        void subirloEnElCarruselLoCambiaDeLugarConElAnterior() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String primero = crearServicio(admin, nombreNuevo(), true);
            String segundo = crearServicio(admin, nombreNuevo(), true);
            int ordenPrimero = ordenDe(primero);

            mvc.perform(json(patch("/api/v1/servicios/" + segundo + "/orden").cookie(admin), "{\"direccion\":-1}"))
                    .andExpect(status().isOk());
            assertThat(ordenDe(segundo)).isEqualTo(ordenPrimero);
            assertThat(ordenDe(primero)).isEqualTo(ordenPrimero + 1);
        }

        @Test
        void elPrimeroNoPuedeSubirMas() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String primero = jdbc.queryForObject("SELECT id::text FROM servicios ORDER BY orden LIMIT 1", String.class);
            mvc.perform(json(patch("/api/v1/servicios/" + primero + "/orden").cookie(admin), "{\"direccion\":-1}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este servicio ya es el primero del carrusel"));
        }

        @Test
        void sinCitasNiProyectosSeEliminaConSuPlantillaYLosDemasSuben() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String porEliminar = crearServicio(admin, nombreNuevo(), true);
            String siguiente = crearServicio(admin, nombreNuevo(), true);
            int ordenEliminado = ordenDe(porEliminar);
            crearPlantilla(porEliminar);

            mvc.perform(delete("/api/v1/servicios/" + porEliminar).cookie(admin)).andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM servicios WHERE id = ?::uuid", Integer.class, porEliminar)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_fases WHERE servicio_id = ?::uuid", Integer.class, porEliminar)).isZero();
            assertThat(ordenDe(siguiente)).isEqualTo(ordenEliminado);
        }

        @Test
        void conCitasNoSeEliminaYSeOfreceOcultarlo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String id = crearServicio(admin, nombreNuevo(), true);
            crearCita(id);

            mvc.perform(delete("/api/v1/servicios/" + id).cookie(admin))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este servicio tiene citas o proyectos; puedes ocultarlo"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM servicios WHERE id = ?::uuid", Integer.class, id)).isOne();
        }

        @Test
        void unServicioQueNoExisteDa404() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            mvc.perform(json(patch("/api/v1/servicios/" + UUID.randomUUID() + "/visible").cookie(admin), "{\"visible\":false}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- HU-18 · Imagen propia

    @Nested
    class Imagenes {

        @Test
        void subeUnaImagenLaSirveSinSesionYSePuedeUsarEnUnServicio() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String url = JsonPath.read(mvc.perform(multipart("/api/v1/servicios/imagenes")
                            .file(new MockMultipartFile("archivo", "foto.png", "image/png", PNG)).cookie(admin))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "$.imagenUrl");
            assertThat(url).startsWith("/api/v1/servicios/imagenes/").endsWith(".png");

            mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/png"))
                    .andExpect(content().bytes(PNG));
            mvc.perform(json(post("/api/v1/servicios").cookie(admin), servicio(nombreNuevo(), true, url)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imagenUrl").value(url));
        }

        @Test
        void laLimpiezaDiariaBorraLasImagenesSubidasQueNingunServicioUsa() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String usada = subir(admin);
            String sinUso = subir(admin);
            String reciente = subir(admin);
            mvc.perform(json(post("/api/v1/servicios").cookie(admin), servicio(nombreNuevo(), true, usada))).andExpect(status().isCreated());
            // Las de hace dos días; la reciente puede ser de un servicio que se está editando (no se borra)
            envejecer(usada);
            envejecer(sinUso);

            assertThat(catalogo.limpiarImagenesSinUso()).isGreaterThanOrEqualTo(1);

            mvc.perform(get(usada)).andExpect(status().isOk());
            mvc.perform(get(sinUso)).andExpect(status().isNotFound());
            mvc.perform(get(reciente)).andExpect(status().isOk());
        }

        @Test
        void unArchivoQueNoEsImagenSeRechazaAunqueDigaPng() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            byte[] ejecutable = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0 };
            mvc.perform(multipart("/api/v1/servicios/imagenes")
                            .file(new MockMultipartFile("archivo", "virus.png", "image/png", ejecutable)).cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("imagen"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Sube una imagen en formato WebP, PNG o JPG."));
        }

        @Test
        void unaImagenDeMasDe2MBSeRechaza() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            byte[] grande = Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);
            mvc.perform(multipart("/api/v1/servicios/imagenes")
                            .file(new MockMultipartFile("archivo", "grande.png", "image/png", grande)).cookie(admin))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La imagen pesa más de 2 MB. Comprímela o usa formato WebP."));
        }

        @Test
        void unNombreDeArchivoInventadoDa404() throws Exception {
            mvc.perform(get("/api/v1/servicios/imagenes/application.yml")).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/servicios/imagenes/" + UUID.randomUUID() + ".png")).andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- HU-19 · Textos de la página principal

    @Nested
    class Contenido {

        @Test
        void publicaElTextoQueDaLaAuditoriaYLaUltimaPublicacion() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String nuevo = "Empieza hoy tu proyecto " + UUID.randomUUID().toString().substring(0, 6);

            mvc.perform(json(put("/api/v1/contenido").cookie(admin), "{\"textos\":{\"llamado-titulo\":\"  " + nuevo + "  \"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ultimaPublicacion.nombre").value("Usuario de Prueba"))
                    .andExpect(jsonPath("$.textos[?(@.clave == 'llamado-titulo')].valor").value(nuevo));
            mvc.perform(get("/api/v1/contenido")).andExpect(jsonPath("$.textos['llamado-titulo']").value(nuevo));
            String cambios = jdbc.queryForObject("""
                    SELECT cambios::text FROM auditoria WHERE accion = 'Publicó cambios en la página principal' ORDER BY id DESC LIMIT 1
                    """, String.class);
            assertThat(cambios).contains("llamado-titulo").contains(nuevo);
        }

        @Test
        void siUnTextoNoCumpleNoSePublicaNingunoYCadaErrorVaEnSuCampo() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String antes = jdbc.queryForObject("SELECT valor FROM contenido_sitio WHERE clave = 'servicios-titulo'", String.class);
            String cuerpo = """
                    {"textos":{"servicios-titulo":"Nuestros servicios","portada-etiqueta":"ab","contacto-telefono":"300-123-4567",
                               "contacto-correo":"no es un correo","pie-mision":"","clave-inventada":"Hola mundo"}}
                    """;
            mvc.perform(json(put("/api/v1/contenido").cookie(admin), cuerpo))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores", hasSize(5)))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'portada-etiqueta')].mensaje").value("Escribe al menos 3 caracteres."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'contacto-telefono')].mensaje")
                            .value("Escribe solo números y espacios, por ejemplo 300 123 4567."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'contacto-correo')].mensaje")
                            .value("Escribe un correo válido, por ejemplo nombre@empresa.com."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'pie-mision')].mensaje").value("Este texto no puede quedar vacío."))
                    .andExpect(jsonPath("$.errores[?(@.campo == 'clave-inventada')].mensaje").value("Este texto no existe en la página principal."));
            assertThat(jdbc.queryForObject("SELECT valor FROM contenido_sitio WHERE clave = 'servicios-titulo'", String.class)).isEqualTo(antes);
        }

        @Test
        void sinCambiosDa422() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            String actual = jdbc.queryForObject("SELECT valor FROM contenido_sitio WHERE clave = 'proceso-titulo'", String.class);
            mvc.perform(json(put("/api/v1/contenido").cookie(admin), "{\"textos\":{\"proceso-titulo\":\"" + actual + "\"}}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("No hay cambios por publicar."));
        }

        @Test
        void laEdicionTraeElLargoPermitidoDeCadaTexto() throws Exception {
            Cookie admin = sesionDe(crearUsuario("ADMINISTRADOR"));
            mvc.perform(get("/api/v1/contenido/edicion").cookie(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.textos", hasSize(27)))
                    .andExpect(jsonPath("$.textos[?(@.clave == 'pie-vision')].maxLongitud").value(300))
                    .andExpect(jsonPath("$.textos[?(@.clave == 'contacto-telefono')].seccion").value("Contacto"));
        }
    }

    // ---------------------------------------------------------------- Permisos (RNF-01)

    @Nested
    class Permisos {

        @Test
        void sinSesionNoSeAdministraNada() throws Exception {
            mvc.perform(get("/api/v1/servicios/gestion")).andExpect(status().isUnauthorized());
            mvc.perform(json(post("/api/v1/servicios"), servicio(nombreNuevo(), true, GALERIA))).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/contenido/edicion")).andExpect(status().isUnauthorized());
        }

        @Test
        void unClienteRecibe403() throws Exception {
            Cookie cliente = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(get("/api/v1/servicios/gestion").cookie(cliente)).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/v1/servicios").cookie(cliente), servicio(nombreNuevo(), true, GALERIA)))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart("/api/v1/servicios/imagenes")
                    .file(new MockMultipartFile("archivo", "foto.png", "image/png", PNG)).cookie(cliente)).andExpect(status().isForbidden());
            mvc.perform(json(put("/api/v1/contenido").cookie(cliente), "{\"textos\":{\"proceso-titulo\":\"Cómo trabajamos\"}}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void elDesarrolladorTampocoAdministraElSitio() throws Exception {
            Cookie po = sesionDe(crearUsuario("PRODUCT_OWNER"));
            mvc.perform(get("/api/v1/servicios/gestion").cookie(po)).andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- Ayudas

    private static final java.util.Random AZAR = new java.util.Random();

    /** "Servicio qwertyui": solo letras, para que el slug esperado sea fácil de calcular. */
    private String subir(Cookie admin) throws Exception {
        return JsonPath.read(mvc.perform(multipart("/api/v1/servicios/imagenes")
                        .file(new MockMultipartFile("archivo", "foto.png", "image/png", PNG)).cookie(admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.imagenUrl");
    }

    /** Cambia la fecha del archivo a hace dos días. */
    private void envejecer(String url) throws Exception {
        Path archivo = Path.of(propiedades.archivos().directorio(), "servicios", url.substring(url.lastIndexOf('/') + 1));
        Files.setLastModifiedTime(archivo, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
    }

    private static String nombreNuevo() {
        StringBuilder letras = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            letras.append((char) ('a' + AZAR.nextInt(26)));
        }
        return "Servicio " + letras;
    }

    private static String servicio(String nombre, boolean visible, String imagen) {
        return """
                {"nombre":"%s","descripcionCorta":"Una descripción corta y clara del servicio.","imagenUrl":"%s",
                 "imagenAlt":"Ilustración del servicio de prueba","visible":%s}
                """.formatted(nombre, imagen, visible);
    }

    private String crearServicio(Cookie admin, String nombre, boolean visible) throws Exception {
        String respuesta = mvc.perform(json(post("/api/v1/servicios").cookie(admin), servicio(nombre, visible, GALERIA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.servicioId");
    }

    private int ordenDe(String servicioId) {
        return jdbc.queryForObject("SELECT orden FROM servicios WHERE id = ?::uuid", Integer.class, servicioId);
    }

    /** Plantilla válida (RN-28: 2 fases, una la aprueba el cliente, un paso cada una), en una sola transacción. */
    private void crearPlantilla(String servicioId) {
        transaccion.executeWithoutResult(t -> {
            for (int orden = 1; orden <= 2; orden++) {
                UUID fase = UUID.randomUUID();
                jdbc.update("INSERT INTO plantilla_fases (id, servicio_id, orden, nombre, aprueba_cliente) VALUES (?, ?::uuid, ?, ?, ?)",
                        fase, servicioId, orden, "Fase " + orden, orden == 1);
                jdbc.update("INSERT INTO plantilla_pasos (plantilla_fase_id, orden, titulo) VALUES (?, 1, 'Primer paso')", fase);
            }
        });
    }

    private void crearCita(String servicioId) {
        String cliente = crearUsuario("CLIENTE");
        jdbc.update("""
                INSERT INTO citas (codigo, cliente_id, servicio_id, fecha_hora, modalidad, descripcion_necesidad)
                SELECT 'T-' || substr(md5(random()::text), 1, 6), u.id, ?::uuid,
                       date_trunc('hour', now()) + make_interval(hours => 72 + (random() * 5000)::int),
                       'VIRTUAL', 'Necesito una tienda para vender mis productos.'
                  FROM usuarios u WHERE u.email = ?
                """, servicioId, cliente);
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
        Cookie cookie = mvc.perform(json(post("/api/v1/auth/login"), """
                        {"email":"%s","password":"%s","recordarme":false}
                        """.formatted(email, CLAVE)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("sesion");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }
}
