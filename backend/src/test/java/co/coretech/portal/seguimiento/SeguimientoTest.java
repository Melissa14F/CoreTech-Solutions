package co.coretech.portal.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.proyectos.BaseProyectosPrueba;
import jakarta.servlet.http.Cookie;

/**
 * Registrar el trabajo de la fase actual y consultar el estado del proyecto contra PostgreSQL 15 (HU-03, HU-04, RN-07 a
 * RN-10, RNF-02): el camino feliz y cada excepción de las dos HU con su código HTTP, los adjuntos (tipo real por contenido) y
 * la protección de lo publicado, que también cuida la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class SeguimientoTest extends BaseProyectosPrueba {

    static final String NOTA = "Quedó listo y ya puedes verlo funcionando en tu cuenta de pruebas.";
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
    static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>";

    /** Un proyecto con el plan aprobado: la fase 1 en curso (la aprueba el cliente) y sus dos pasos pendientes. */
    record Aprobado(String admin, Proyecto proyecto, Cookie po, Cookie cliente) {
    }

    private Aprobado aprobado() throws Exception {
        String admin = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(admin);
        Cookie po = sesionDe(p.equipo().email());
        Cookie cliente = sesionDe(p.clienteEmail());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/enviar").cookie(po), planJson())).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isOk());
        return new Aprobado(admin, p, po, cliente);
    }

    /** Tres fases de dos pasos; solo la primera la aprueba el cliente. */
    private static String planJson() {
        StringBuilder s = new StringBuilder("{\"fases\":[");
        for (int f = 1; f <= 3; f++) {
            s.append(f > 1 ? "," : "").append("{\"nombre\":\"Fase ").append(f).append("\",\"queEs\":\"Qué es\",\"apruebaCliente\":")
                    .append(f == 1).append(",\"pasos\":[{\"titulo\":\"Paso 1 de la fase ").append(f)
                    .append("\",\"explicacion\":\"Explicación\"},{\"titulo\":\"Paso 2 de la fase ").append(f).append("\"}]}");
        }
        LocalDate entrega = hoy().plusDays(60);
        return s.append("],\"valorCotizado\":7800000,\"fechaEstimadaEntrega\":\"").append(entrega)
                .append("\",\"condicionesCotizacion\":\"Incluye 2 rondas de ajustes en cada fase y 30 días de garantía.\"}").toString();
    }

    /** Los pasos del proyecto en orden: fase 1 paso 1, fase 1 paso 2, fase 2 paso 1… */
    private List<UUID> pasos(String codigo) {
        return jdbc.queryForList("""
                SELECT s.id FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id JOIN proyectos p ON p.id = f.proyecto_id
                 WHERE p.codigo = ? ORDER BY f.orden, s.orden
                """, UUID.class, codigo);
    }

    private static String hecho(String nota) {
        return "{\"estado\":\"HECHO\",\"nota\":\"" + nota + "\"}";
    }

    private MockMultipartHttpServletRequestBuilder subir(UUID paso, Cookie sesion, MockMultipartFile... archivos) {
        MockMultipartHttpServletRequestBuilder peticion = multipart("/api/v1/pasos/" + paso + "/adjuntos");
        for (MockMultipartFile a : archivos) {
            peticion.file(a);
        }
        return (MockMultipartHttpServletRequestBuilder) peticion.cookie(sesion);
    }

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivos", nombre, "application/octet-stream", contenido);
    }

    private int auditoriaDe(String codigo, String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = ?", Integer.class, codigo, accion);
    }

    // ------------------------------------------------------------------------------------------ HU-03 y HU-04: camino feliz

    @Test
    void elProductOwnerMarcaPasosHechosYElClienteLosVeEnElDetalle() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        List<UUID> pasos = pasos(codigo);

        // Un integrante más del equipo, para verlo en el detalle
        UUID desarrollador = jdbc.queryForObject("""
                INSERT INTO desarrolladores (nombre, correo, celular, especialidad)
                VALUES ('Camilo de Prueba', ?, '3001234567', 'QA') RETURNING id
                """, UUID.class, "qa-" + UUID.randomUUID().toString().substring(0, 8) + "@coretechsolutions.co");
        jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, 'QA')", a.proyecto().equipo().id(), desarrollador);

        mvc.perform(json(patch("/api/v1/pasos/" + pasos.get(0)).cookie(a.po()), hecho(NOTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pasoId").value(pasos.get(0).toString()))
                .andExpect(jsonPath("$.hechoAt").exists())
                .andExpect(jsonPath("$.pasosHechos").value("1 de 2"));

        assertThat(jdbc.queryForObject("SELECT estado::text || '|' || nota || '|' || (registrado_por IS NOT NULL) FROM pasos WHERE id = ?",
                String.class, pasos.get(0))).isEqualTo("HECHO|" + NOTA + "|true");
        assertThat(avisos(a.proyecto().clienteId(), "PASO_HECHO")).isEqualTo(1);
        assertThat(auditoriaDe(codigo, "Marcó un paso como hecho")).isEqualTo(1);

        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.estadoPlan").value("APROBADO"))
                .andExpect(jsonPath("$.avancePorcentaje").value(0))
                .andExpect(jsonPath("$.faseActual.orden").value(1))
                .andExpect(jsonPath("$.pasosHechos").value(1))
                .andExpect(jsonPath("$.ultimoPaso.titulo").value("Paso 1 de la fase 1"))
                .andExpect(jsonPath("$.puedeRegistrar").value(false))
                .andExpect(jsonPath("$.fases", hasSize(3)))
                .andExpect(jsonPath("$.fases[0].estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.fases[0].porcentaje").value(33.3))
                .andExpect(jsonPath("$.fases[0].pasosHechos").value(1))
                .andExpect(jsonPath("$.fases[0].pasos[0].estado").value("HECHO"))
                .andExpect(jsonPath("$.fases[0].pasos[0].nota").value(NOTA))
                .andExpect(jsonPath("$.fases[0].pasos[1].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.fases[1].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.equipo", hasSize(1)))
                .andExpect(jsonPath("$.equipo[0].rol").value("Pruebas"));

        // El Product Owner del equipo ve lo mismo y puede registrar; el administrador también lo ve
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(a.po()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.puedeRegistrar").value(true));
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(sesionDe(a.admin()))).andExpect(status().isOk());
    }

    @Test
    void unaCorreccionAdmiteUnaNotaDeHasta500CaracteresYUnPasoDelPlanSoloHasta400() throws Exception {
        Aprobado a = aprobado();
        UUID fase = jdbc.queryForObject("""
                SELECT f.id FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id WHERE p.codigo = ? AND f.orden = 1
                """, UUID.class, a.proyecto().codigo());
        UUID correccion = jdbc.queryForObject("""
                INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion, origen)
                VALUES (?, 3, 'CORRECCION', 'Mostrar el costo del envío', 'Se verá el costo antes de pagar.', 'Lo pidió el cliente') RETURNING id
                """, UUID.class, fase);
        String nota401 = "a".repeat(401);
        String nota450 = "b".repeat(450);

        mvc.perform(json(patch("/api/v1/pasos/" + pasos(a.proyecto().codigo()).get(0)).cookie(a.po()), hecho(nota401)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("nota"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe máximo 400 caracteres."));
        mvc.perform(json(patch("/api/v1/pasos/" + correccion).cookie(a.po()), hecho(nota450))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------ HU-03: excepciones

    @Test
    void laNotaDebeTenerDe20A400CaracteresYElEstadoSoloPuedeSerHecho() throws Exception {
        Aprobado a = aprobado();
        UUID paso = pasos(a.proyecto().codigo()).get(0);

        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), "{\"estado\":\"HECHO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("nota"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe la nota para que Usuario sepa qué cambió."));
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho("Muy corta")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe al menos 20 caracteres."));
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), "{\"estado\":\"QUITADO\",\"nota\":\"" + NOTA + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
        assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, paso)).isEqualTo("PENDIENTE");
    }

    @Test
    void soloElProductOwnerDelEquipoRegistraYElIntentoAjenoQuedaEnLaAuditoria() throws Exception {
        Aprobado a = aprobado();
        UUID paso = pasos(a.proyecto().codigo()).get(0);

        Equipo otro = equipo();
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(sesionDe(otro.email())), hecho(NOTA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("Solo el desarrollador (Product Owner) del equipo puede registrar trabajo en este proyecto"));
        assertThat(auditoriaDe(a.proyecto().codigo(), "Intentó registrar trabajo en un proyecto de otro equipo")).isEqualTo(1);

        // El cliente y el administrador no tienen el permiso de registrar
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.cliente()), hecho(NOTA))).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(sesionDe(a.admin())), hecho(NOTA))).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/pasos/" + paso), hecho(NOTA))).andExpect(status().isUnauthorized());
        mvc.perform(json(patch("/api/v1/pasos/" + UUID.randomUUID()).cookie(a.po()), hecho(NOTA))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, paso)).isEqualTo("PENDIENTE");
    }

    @Test
    void unPasoDeOtraFaseOYaHechoRespondeConflictoYUnProyectoPausadoNoAdmiteRegistros() throws Exception {
        Aprobado a = aprobado();
        List<UUID> pasos = pasos(a.proyecto().codigo());

        // Un paso de la fase 2 (todavía pendiente) no es de la fase actual
        mvc.perform(json(patch("/api/v1/pasos/" + pasos.get(2)).cookie(a.po()), hecho(NOTA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este paso ya está registrado o no es de la fase actual"));

        mvc.perform(json(patch("/api/v1/pasos/" + pasos.get(0)).cookie(a.po()), hecho(NOTA))).andExpect(status().isOk());
        mvc.perform(json(patch("/api/v1/pasos/" + pasos.get(0)).cookie(a.po()), hecho(NOTA + " Otra vez.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este paso ya está registrado o no es de la fase actual"));
        assertThat(jdbc.queryForObject("SELECT nota FROM pasos WHERE id = ?", String.class, pasos.get(0))).isEqualTo(NOTA);

        mvc.perform(json(patch("/api/v1/proyectos/" + a.proyecto().codigo() + "/estado").cookie(sesionDe(a.admin())),
                "{\"estado\":\"PAUSADO\",\"motivo\":\"El cliente pidió una pausa mientras consigue el presupuesto.\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(patch("/api/v1/pasos/" + pasos.get(1)).cookie(a.po()), hecho(NOTA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El proyecto no admite registros en su estado actual"));
    }

    @Test
    void loPublicadoNoSeEditaNiSeBorraNiPorLaBaseDeDatos() throws Exception {
        Aprobado a = aprobado();
        UUID paso = pasos(a.proyecto().codigo()).get(0);
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho(NOTA))).andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("UPDATE pasos SET nota = 'Otra nota distinta para el cliente.' WHERE id = ?", paso))
                .hasMessageContaining("no se edita ni se borra");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM pasos WHERE id = ?", paso)).hasMessageContaining("no se edita ni se borra");
        assertThat(jdbc.queryForObject("SELECT nota FROM pasos WHERE id = ?", String.class, paso)).isEqualTo(NOTA);
    }

    // ------------------------------------------------------------------------------------------ HU-03: adjuntos

    @Test
    void adjuntaBocetosYElClienteLosDescargaPorLaApiConControlDeAcceso() throws Exception {
        Aprobado a = aprobado();
        UUID paso = pasos(a.proyecto().codigo()).get(0);
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho(NOTA))).andExpect(status().isOk());

        String respuesta = mvc.perform(subir(paso, a.po(), archivo("Página de producto.png", PNG), archivo("Propuesta.pdf", PDF),
                        archivo("boceto_carrito.svg", SVG.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].titulo").value("Página de producto"))
                .andExpect(jsonPath("$[0].tipoMime").value("image/png"))
                .andExpect(jsonPath("$[1].tipoMime").value("application/pdf"))
                .andExpect(jsonPath("$[2].titulo").value("boceto carrito"))
                .andExpect(jsonPath("$[2].tipoMime").value("image/svg+xml"))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).contains("/api/v1/adjuntos/");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM adjuntos_paso WHERE paso_id = ?", Integer.class, paso)).isEqualTo(3);

        UUID png = jdbc.queryForObject("SELECT id FROM adjuntos_paso WHERE paso_id = ? AND tipo_mime = 'image/png'", UUID.class, paso);
        UUID svg = jdbc.queryForObject("SELECT id FROM adjuntos_paso WHERE paso_id = ? AND tipo_mime = 'image/svg+xml'", UUID.class, paso);

        // El detalle los lista y el cliente dueño los descarga; el nombre guardado no es el del usuario
        mvc.perform(get("/api/v1/proyectos/" + a.proyecto().codigo()).cookie(a.cliente()))
                .andExpect(jsonPath("$.fases[0].pasos[0].adjuntos", hasSize(3)))
                .andExpect(jsonPath("$.fases[0].pasos[0].adjuntos[0].url").value("/api/v1/adjuntos/" + png));
        assertThat(jdbc.queryForObject("SELECT url FROM adjuntos_paso WHERE id = ?", String.class, png)).doesNotContain("Página").endsWith(".png");
        byte[] descargado = mvc.perform(get("/api/v1/adjuntos/" + png).cookie(a.cliente()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(descargado).isEqualTo(PNG);
        mvc.perform(get("/api/v1/adjuntos/" + svg).cookie(a.po()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("sandbox")));

        // Otro cliente: 404, como si no existiera, y queda en la auditoría
        String ajeno = crearUsuario("CLIENTE");
        mvc.perform(get("/api/v1/adjuntos/" + png).cookie(sesionDe(ajeno))).andExpect(status().isNotFound());
        assertThat(auditoriaDe(a.proyecto().codigo(), "Intentó descargar un archivo de un proyecto ajeno")).isEqualTo(1);
        mvc.perform(get("/api/v1/adjuntos/" + png)).andExpect(status().isUnauthorized());
    }

    @Test
    void unExeRenombradoAPngSeRechazaPorSuContenido() throws Exception {
        Aprobado a = aprobado();
        UUID paso = pasos(a.proyecto().codigo()).get(0);
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho(NOTA))).andExpect(status().isOk());

        byte[] exe = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 };
        mvc.perform(subir(paso, a.po(), archivo("virus.png", exe)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("adjuntos"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("«virus.png» no es un PNG, JPG, SVG o PDF válido."));

        // Un SVG con un script tampoco se acepta; si falla uno, no se guarda ninguno
        String conScript = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
        mvc.perform(subir(paso, a.po(), archivo("bueno.png", PNG), archivo("malo.svg", conScript.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableEntity());
        String conEvento = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>";
        mvc.perform(subir(paso, a.po(), archivo("malo.svg", conEvento.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM adjuntos_paso WHERE paso_id = ?", Integer.class, paso)).isZero();
    }

    @Test
    void losAdjuntosTienenUnTopeDeCincoArchivosDeCincoMegasYSoloVanEnUnPasoHechoDeSuEquipo() throws Exception {
        Aprobado a = aprobado();
        List<UUID> pasos = pasos(a.proyecto().codigo());

        // Un paso que todavía no está hecho no recibe archivos
        mvc.perform(subir(pasos.get(0), a.po(), archivo("a.png", PNG))).andExpect(status().isConflict());
        mvc.perform(json(patch("/api/v1/pasos/" + pasos.get(0)).cookie(a.po()), hecho(NOTA))).andExpect(status().isOk());

        // Sin archivos, y más de 5
        mvc.perform(subir(pasos.get(0), a.po())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("adjuntos"));
        MockMultipartFile[] seis = new MockMultipartFile[6];
        for (int i = 0; i < seis.length; i++) {
            seis[i] = archivo("boceto-" + i + ".png", PNG);
        }
        mvc.perform(subir(pasos.get(0), a.po(), seis)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Un paso admite máximo 5 archivos."));

        // Más de 5 MB
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        mvc.perform(subir(pasos.get(0), a.po(), archivo("enorme.png", grande))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("«enorme.png» pesa más de 5 MB."));

        // Cuatro más llegan a 5 en total (con 1 ya guardado); uno más pasa del tope
        mvc.perform(subir(pasos.get(0), a.po(), archivo("uno.png", PNG))).andExpect(status().isCreated());
        mvc.perform(subir(pasos.get(0), a.po(), archivo("dos.png", PNG), archivo("tres.png", PNG), archivo("cuatro.png", PNG),
                archivo("cinco.png", PNG))).andExpect(status().isCreated());
        mvc.perform(subir(pasos.get(0), a.po(), archivo("seis.png", PNG))).andExpect(status().isUnprocessableEntity());

        // Otro equipo, el cliente y sin sesión
        mvc.perform(subir(pasos.get(0), sesionDe(equipo().email()), archivo("x.png", PNG))).andExpect(status().isForbidden());
        mvc.perform(subir(pasos.get(0), a.cliente(), archivo("x.png", PNG))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/pasos/" + pasos.get(0) + "/adjuntos").file(archivo("x.png", PNG))).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------------------------------ HU-04: consultar

    @Test
    void unProyectoAjenoNoExisteParaElClienteYQuedaEnLaAuditoria() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        Cookie otroCliente = sesionDe(crearUsuario("CLIENTE"));

        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(otroCliente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Proyecto no encontrado"));
        assertThat(auditoriaDe(codigo, "Intentó consultar un proyecto ajeno")).isEqualTo(1);

        // Un código que no existe o mal escrito responde igual
        mvc.perform(get("/api/v1/proyectos/P-9999").cookie(otroCliente)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/proyectos/XYZ").cookie(a.cliente())).andExpect(status().isNotFound());

        // Un Product Owner de otro equipo recibe 403 y queda en la auditoría; sin sesión, 401
        mvc.perform(get("/api/v1/proyectos/" + codigo).cookie(sesionDe(equipo().email()))).andExpect(status().isForbidden());
        assertThat(auditoriaDe(codigo, "Intentó consultar un proyecto de otro equipo")).isEqualTo(1);
        mvc.perform(get("/api/v1/proyectos/" + codigo)).andExpect(status().isUnauthorized());
    }

    @Test
    void conElPlanEnBorradorElClienteAunNoVeFases() throws Exception {
        String admin = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(admin);

        mvc.perform(get("/api/v1/proyectos/" + p.codigo()).cookie(sesionDe(p.clienteEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlan").value("BORRADOR"))
                .andExpect(jsonPath("$.fases", hasSize(0)))
                .andExpect(jsonPath("$.faseActual").doesNotExist());
        mvc.perform(get("/api/v1/proyectos/" + p.codigo()).cookie(sesionDe(p.equipo().email())))
                .andExpect(jsonPath("$.fases", hasSize(3)));
    }
}
