package co.coretech.portal.seguimiento.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.coretech.portal.compartido.service.AlmacenArchivos;
import co.coretech.portal.compartido.service.AuditoriaAccesoDenegado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.controller.ConfiguracionErroresPrueba;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.AdjuntoSubido;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.ArchivoSubido;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.DetalleProyecto;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.Descarga;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.FaseActual;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.PasoRegistrado;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.Persona;

/**
 * Pruebas de INTEGRACIÓN de la API de seguimiento con {@code @WebMvcTest} + MockMvc (HU-03 y HU-04): el detalle del proyecto,
 * marcar un paso como hecho, adjuntar archivos (multipart) y descargarlos con sus encabezados de seguridad. El servicio va
 * simulado; sus reglas están en {@code ServicioSeguimientoUnitarioTest} y los permisos en {@code InventarioEndpointsTest}.
 */
@WebMvcTest(controllers = ControladorPasos.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorPasosWebMvcTest {

    static final UUID PASO = UUID.fromString("00000000-0000-4000-8000-000000000051");
    static final UUID ADJUNTO = UUID.fromString("00000000-0000-4000-8000-000000000091");
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioSeguimiento servicio;

    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    private static DetalleProyecto detalle() {
        return new DetalleProyecto("P-0031", "Inventario del salón", "Sistema de inventario", "C-0045", "ACTIVO", "APROBADO", new BigDecimal("25.00"),
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 4, 15), null, new FaseActual(2, "Construcción"), 3, null, "Laura Gómez", "laura@correo.com",
                "Andrés Pérez", "andres@coretechsolutions.co", List.of(new Persona("Camila Ruiz", "Diseño de pantallas")), true, List.of());
    }

    @Test
    @DisplayName("GET /proyectos/{codigo}: 200 con el avance, la fase actual, el equipo y si quien mira puede registrar; las fechas en ISO 8601")
    void detalleDelProyecto() throws Exception {
        when(servicio.detalle("P-0031")).thenReturn(detalle());

        mvc.perform(get("/api/v1/proyectos/P-0031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("P-0031"))
                .andExpect(jsonPath("$.avancePorcentaje").value(25.00))
                .andExpect(jsonPath("$.faseActual.nombre").value("Construcción"))
                .andExpect(jsonPath("$.fechaEstimadaEntrega").value("2027-04-15"))
                .andExpect(jsonPath("$.garantiaHasta").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.equipo[0].rol").value("Diseño de pantallas"))
                .andExpect(jsonPath("$.puedeRegistrar").value(true));
    }

    @Test
    @DisplayName("Un proyecto ajeno es 404 y el de otro equipo 403, con el mensaje estándar")
    void detalleSinAcceso() throws Exception {
        when(servicio.detalle("P-0099")).thenThrow(new NoEncontradoException("Proyecto no encontrado"));
        mvc.perform(get("/api/v1/proyectos/P-0099")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errores[0].mensaje").value("Proyecto no encontrado"));

        when(servicio.detalle("P-0050")).thenThrow(new ProhibidoException("Este proyecto no es de tu equipo"));
        mvc.perform(get("/api/v1/proyectos/P-0050")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /pasos/{id}: 200 con la hora y «4 de 5»; pasa el estado y la nota tal cual")
    void marcaHecho() throws Exception {
        when(servicio.marcarHecho(eq(PASO), any(), any())).thenReturn(new PasoRegistrado(PASO, Instant.parse("2027-03-02T15:00:00Z"), "4 de 5"));

        mvc.perform(patch("/api/v1/pasos/" + PASO).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"HECHO\",\"nota\":\"Se dejó listo el módulo y se probó.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pasoId").value(PASO.toString()))
                .andExpect(jsonPath("$.hechoAt").value("2027-03-02T15:00:00Z"))
                .andExpect(jsonPath("$.pasosHechos").value("4 de 5"));
        verify(servicio).marcarHecho(PASO, "HECHO", "Se dejó listo el módulo y se probó.");
    }

    @Test
    @DisplayName("Marcar hecho: nota inválida 422 con su campo, paso ya registrado 409, otro equipo 403 y paso inexistente 404")
    void marcaHechoConErrores() throws Exception {
        String cuerpo = "{\"estado\":\"HECHO\",\"nota\":\"corta\"}";
        when(servicio.marcarHecho(any(), any(), any())).thenThrow(new ValidacionException(List.of(new ValidacionException.Falla("nota", "Escribe al menos 20 caracteres."))));
        mvc.perform(patch("/api/v1/pasos/" + PASO).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("nota"));

        doThrow(new ConflictoException("Este paso ya está registrado o no es de la fase actual")).when(servicio).marcarHecho(any(), any(), any());
        mvc.perform(patch("/api/v1/pasos/" + PASO).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isConflict());

        doThrow(new ProhibidoException("Solo el desarrollador (Product Owner) del equipo puede registrar trabajo en este proyecto")).when(servicio).marcarHecho(any(), any(), any());
        mvc.perform(patch("/api/v1/pasos/" + PASO).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());

        doThrow(new NoEncontradoException("Paso no encontrado")).when(servicio).marcarHecho(any(), any(), any());
        mvc.perform(patch("/api/v1/pasos/" + PASO).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un id de paso que no es UUID o un cuerpo que no es JSON: 422 y no llega al servicio")
    void entradaMalFormada() throws Exception {
        mvc.perform(patch("/api/v1/pasos/abc").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("pasoId"));
        mvc.perform(patch("/api/v1/pasos/" + PASO).contentType(MediaType.APPLICATION_JSON).content("no es json")).andExpect(status().isUnprocessableEntity());

        verify(servicio, never()).marcarHecho(any(), any(), any());
    }

    @Test
    @DisplayName("POST /pasos/{id}/adjuntos (multipart): 201 con los adjuntos y sus URL; el servicio recibe el nombre original y los bytes de cada archivo")
    void adjunta() throws Exception {
        when(servicio.adjuntar(eq(PASO), any())).thenReturn(List.of(new AdjuntoSubido(ADJUNTO, "boceto", "image/png", PNG.length, "/api/v1/adjuntos/" + ADJUNTO)));

        mvc.perform(multipart("/api/v1/pasos/" + PASO + "/adjuntos")
                .file(new MockMultipartFile("archivos", "boceto.png", "image/png", PNG))
                .file(new MockMultipartFile("archivos", "plan.pdf", "application/pdf", "%PDF-1.7".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].url").value("/api/v1/adjuntos/" + ADJUNTO))
                .andExpect(jsonPath("$[0].tipoMime").value("image/png"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArchivoSubido>> subidos = ArgumentCaptor.forClass(List.class);
        verify(servicio).adjuntar(eq(PASO), subidos.capture());
        assertEquals(2, subidos.getValue().size());
        assertEquals("boceto.png", subidos.getValue().get(0).nombre());
        assertTrue(java.util.Arrays.equals(PNG, subidos.getValue().get(0).contenido()));
    }

    @Test
    @DisplayName("Sin archivos en la petición el servicio recibe una lista vacía y responde 422 «Elige al menos un archivo»")
    void sinArchivos() throws Exception {
        when(servicio.adjuntar(eq(PASO), any())).thenThrow(new ValidacionException(List.of(new ValidacionException.Falla("adjuntos", "Elige al menos un archivo."))));

        mvc.perform(multipart("/api/v1/pasos/" + PASO + "/adjuntos"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("adjuntos"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArchivoSubido>> subidos = ArgumentCaptor.forClass(List.class);
        verify(servicio).adjuntar(eq(PASO), subidos.capture());
        assertTrue(subidos.getValue().isEmpty());
    }

    @Test
    @DisplayName("Adjuntar a un paso que no está hecho da 409, y a un proyecto de otro equipo, 403")
    void adjuntarConErrores() throws Exception {
        when(servicio.adjuntar(any(), any())).thenThrow(new ConflictoException("Primero marca el paso como hecho y luego adjunta sus archivos"));
        mvc.perform(multipart("/api/v1/pasos/" + PASO + "/adjuntos").file(new MockMultipartFile("archivos", "a.png", "image/png", PNG)))
                .andExpect(status().isConflict());

        doThrow(new ProhibidoException("Solo el desarrollador (Product Owner) del equipo puede registrar trabajo en este proyecto")).when(servicio).adjuntar(any(), any());
        mvc.perform(multipart("/api/v1/pasos/" + PASO + "/adjuntos").file(new MockMultipartFile("archivos", "a.png", "image/png", PNG)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Adjuntar solo acepta multipart: un JSON recibe un error del cliente y no llega al servicio")
    void soloMultipart() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/pasos/" + PASO + "/adjuntos")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is4xxClientError());

        verify(servicio, never()).adjuntar(any(), any());
    }

    @Test
    @DisplayName("GET /adjuntos/{id}: sirve los bytes con su tipo y los encabezados de seguridad (nosniff, CSP con sandbox, caché privada de 1 hora)")
    void descarga() throws Exception {
        when(servicio.descargar(ADJUNTO)).thenReturn(new Descarga(new AlmacenArchivos.Archivo(PNG, "image/png"), "boceto", "image/png"));

        mvc.perform(get("/api/v1/adjuntos/" + ADJUNTO))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")));
    }

    @Test
    @DisplayName("Un adjunto ajeno o que ya no está disponible es 404; un id que no es UUID, 422")
    void descargaConErrores() throws Exception {
        when(servicio.descargar(ADJUNTO)).thenThrow(new NoEncontradoException("Archivo no encontrado"));

        mvc.perform(get("/api/v1/adjuntos/" + ADJUNTO)).andExpect(status().isNotFound()).andExpect(jsonPath("$.errores[0].mensaje").value("Archivo no encontrado"));
        mvc.perform(get("/api/v1/adjuntos/no-es-uuid")).andExpect(status().isUnprocessableEntity());
    }
}
