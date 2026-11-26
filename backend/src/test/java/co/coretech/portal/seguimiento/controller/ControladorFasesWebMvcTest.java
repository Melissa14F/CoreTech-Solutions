package co.coretech.portal.seguimiento.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.coretech.portal.compartido.service.AuditoriaAccesoDenegado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.controller.ConfiguracionErroresPrueba;
import co.coretech.portal.seguimiento.service.ServicioFases;
import co.coretech.portal.seguimiento.service.ServicioFases.Aprobada;
import co.coretech.portal.seguimiento.service.ServicioFases.CambioPedido;
import co.coretech.portal.seguimiento.service.ServicioFases.CorreccionRegistrada;
import co.coretech.portal.seguimiento.service.ServicioFases.Entregada;

/**
 * Pruebas de INTEGRACIÓN de la API de fases con {@code @WebMvcTest} + MockMvc (HU-11, HU-12, HU-13): rutas, códigos de éxito
 * (200 o 201), formato del JSON y el mapeo de cada excepción del servicio a su código HTTP por el manejador global REAL. El
 * servicio es simulado. Los permisos ({@code @PreAuthorize}) y las reglas se prueban con la seguridad y la base reales en
 * {@code FasesTest} e {@code InventarioEndpointsTest}.
 */
@WebMvcTest(controllers = ControladorFases.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorFasesWebMvcTest {

    static final UUID FASE = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    static final String RUTA = "/api/v1/fases/" + FASE;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioFases servicio;

    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    @Test
    @DisplayName("POST /fases/{id}/entregar: 200 y el estado, el plazo, el avance y el estado del proyecto")
    void entrega() throws Exception {
        when(servicio.entregar(FASE)).thenReturn(new Entregada("EN_REVISION", LocalDate.of(2031, 3, 7), new BigDecimal("40.00"), "ACTIVO", null));

        mvc.perform(post(RUTA + "/entregar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.plazoAprobacion").value("2031-03-07"))
                .andExpect(jsonPath("$.avancePorcentaje").value(40.00))
                .andExpect(jsonPath("$.estadoProyecto").value("ACTIVO"))
                .andExpect(jsonPath("$.garantiaHasta").doesNotExist());
    }

    @Test
    @DisplayName("Cerrar la última fase devuelve la garantía de 30 días en ISO 8601")
    void cierraYGarantia() throws Exception {
        when(servicio.entregar(FASE)).thenReturn(new Entregada("APROBADA", null, new BigDecimal("100.00"), "EN_GARANTIA", LocalDate.of(2031, 4, 3)));

        mvc.perform(post(RUTA + "/entregar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoProyecto").value("EN_GARANTIA"))
                .andExpect(jsonPath("$.garantiaHasta").value("2031-04-03"));
    }

    @Test
    @DisplayName("POST /fases/{id}/aprobar: 200 con el avance nuevo")
    void aprueba() throws Exception {
        when(servicio.aprobar(FASE)).thenReturn(new Aprobada("APROBADA", new BigDecimal("60.00"), "ACTIVO", null));

        mvc.perform(post(RUTA + "/aprobar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.avancePorcentaje").value(60.00));
    }

    @Test
    @DisplayName("POST /fases/{id}/solicitudes-cambio: 201 y pasa tipo y descripción tal cual")
    void pideCambio() throws Exception {
        UUID solicitud = UUID.randomUUID();
        UUID paso = UUID.randomUUID();
        when(servicio.pedirCambio(eq(FASE), eq("AJUSTE"), any())).thenReturn(new CambioPedido(solicitud, "AJUSTE", "ABIERTO", 1, "EN_CURSO", paso));

        mvc.perform(post(RUTA + "/solicitudes-cambio").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"AJUSTE\",\"descripcion\":\"Quiero que el reporte muestre el total por semana.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.solicitudId").value(solicitud.toString()))
                .andExpect(jsonPath("$.rondasUsadas").value(1))
                .andExpect(jsonPath("$.estadoFase").value("EN_CURSO"));
        verify(servicio).pedirCambio(FASE, "AJUSTE", "Quiero que el reporte muestre el total por semana.");
    }

    @Test
    @DisplayName("POST /fases/{id}/pasos (corrección): 201 y pasa origen, título y explicación")
    void registraCorreccion() throws Exception {
        UUID paso = UUID.randomUUID();
        when(servicio.registrarCorreccion(eq(FASE), any(), any(), any())).thenReturn(new CorreccionRegistrada(paso, "EN_CORRECCION", new BigDecimal("80.00")));

        mvc.perform(post(RUTA + "/pasos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"origen\":\"La encontramos en pruebas\",\"titulo\":\"Total del reporte\",\"explicacion\":\"El total salía mal con devoluciones.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pasoId").value(paso.toString()))
                .andExpect(jsonPath("$.estadoFase").value("EN_CORRECCION"))
                .andExpect(jsonPath("$.avancePorcentaje").value(80.00));
        verify(servicio).registrarCorreccion(FASE, "La encontramos en pruebas", "Total del reporte", "El total salía mal con devoluciones.");
    }

    @Test
    @DisplayName("Cada excepción del servicio sale con su código: 404, 403, 409 y 422 con un error por campo")
    void mapeoDeErrores() throws Exception {
        when(servicio.aprobar(FASE)).thenThrow(new NoEncontradoException("Proyecto no encontrado"));
        mvc.perform(post(RUTA + "/aprobar")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errores[0].mensaje").value("Proyecto no encontrado"));

        when(servicio.entregar(FASE)).thenThrow(new ProhibidoException("Solo el desarrollador (Product Owner) del equipo puede registrar trabajo en este proyecto"));
        mvc.perform(post(RUTA + "/entregar")).andExpect(status().isForbidden());

        org.mockito.Mockito.doThrow(new ConflictoException("Falta 1 paso por terminar")).when(servicio).entregar(FASE);
        mvc.perform(post(RUTA + "/entregar")).andExpect(status().isConflict()).andExpect(jsonPath("$.errores[0].mensaje").value("Falta 1 paso por terminar"));

        when(servicio.pedirCambio(any(), any(), any())).thenThrow(new ValidacionException(List.of(
                new ValidacionException.Falla("tipo", "Elige si es un ajuste o algo nuevo."),
                new ValidacionException.Falla("descripcion", "Cuéntanos qué quieres cambiar."))));
        mvc.perform(post(RUTA + "/solicitudes-cambio").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[1].campo").value("descripcion"));
    }

    @Test
    @DisplayName("Un id de fase que no es un UUID da 422 en el campo faseId y no llega al servicio")
    void idMalFormado() throws Exception {
        mvc.perform(post("/api/v1/fases/no-es-un-uuid/entregar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("faseId"));

        verify(servicio, never()).entregar(any());
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON en solicitudes-cambio o pasos da 422 con el mensaje estándar")
    void cuerpoInvalido() throws Exception {
        mvc.perform(post(RUTA + "/solicitudes-cambio").contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Los datos enviados no tienen el formato esperado"));
        mvc.perform(post(RUTA + "/pasos").contentType(MediaType.APPLICATION_JSON).content("[1,2]")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Un método HTTP que la ruta no admite (GET /entregar) no llega al servicio")
    void metodoNoAdmitido() throws Exception {
        mvc.perform(get(RUTA + "/entregar")).andExpect(status().is4xxClientError());

        verify(servicio, never()).entregar(any());
    }
}
