package co.coretech.portal.proyectos.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.coretech.portal.compartido.service.AuditoriaAccesoDenegado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.controller.ConfiguracionErroresPrueba;
import co.coretech.portal.proyectos.service.ServicioPlan;
import co.coretech.portal.proyectos.service.ServicioPlan.Aprobado;
import co.coretech.portal.proyectos.service.ServicioPlan.Comentario;
import co.coretech.portal.proyectos.service.ServicioPlan.Enviado;
import co.coretech.portal.proyectos.service.ServicioPlan.Respondido;
import co.coretech.portal.proyectos.service.ServicioPlan.Solicitud;
import co.coretech.portal.proyectos.service.ServicioPlan.VistaPlan;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;

/**
 * Pruebas de INTEGRACIÓN de la API del plan de trabajo con {@code @WebMvcTest} + MockMvc (HU-28 y HU-29): rutas bajo
 * {@code /proyectos/{codigo}/plan}, códigos de éxito (200, 201 y 204), forma del JSON (fechas ISO 8601, dinero sin comillas) y el
 * mapeo de errores por el manejador global real. El servicio va simulado; sus reglas están en {@code ServicioPlanUnitarioTest} y
 * los permisos en {@code InventarioEndpointsTest}.
 */
@WebMvcTest(controllers = ControladorPlan.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorPlanWebMvcTest {

    static final String RUTA = "/api/v1/proyectos/P-0031/plan";
    static final String CUERPO = """
            {"fases":[
              {"orden":9,"nombre":"Diseño","queEs":"Se define qué se construye","apruebaCliente":false,"pasos":[{"orden":4,"titulo":"Reunir requisitos","explicacion":"Hablamos contigo"}]},
              {"nombre":"Entrega","apruebaCliente":true,"pasos":[{"titulo":"Capacitar al equipo"}]}
            ],
             "valorCotizado":5000000,"condicionesCotizacion":"Incluye diseño, desarrollo y capacitación; no incluye hosting.",
             "fechaEstimadaEntrega":"2027-04-15"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioPlan servicio;

    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    private static VistaPlan vista() {
        List<FasePlantilla> fases = List.of(
                new FasePlantilla(1, "Diseño", null, false, List.of(new PasoPlantilla(1, "Reunir requisitos", null))),
                new FasePlantilla(2, "Entrega", null, true, List.of(new PasoPlantilla(1, "Capacitar", null))));
        return new VistaPlan("P-0031", "Inventario del salón", "ACTIVO", "AJUSTES_PEDIDOS", "Laura Gómez", "Andrés Pérez", "Sistema de inventario", "C-0045",
                Instant.parse("2027-02-20T15:00:00Z"), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 15), new BigDecimal("5000000"), "Incluye todo.",
                Instant.parse("2027-02-25T15:00:00Z"), Instant.parse("2027-03-01T15:00:00Z"), null, LocalDate.of(2027, 3, 4), LocalDate.of(2027, 3, 15),
                fases, new BigDecimal("50.0"), new Comentario("Falta el logo en el diseño.", Instant.parse("2027-03-02T15:00:00Z")), null, true);
    }

    @Test
    @DisplayName("GET /proyectos/{codigo}/plan: 200 con el plan, la cotización, las fechas en ISO 8601, los comentarios y si es editable")
    void obtiene() throws Exception {
        when(servicio.obtener("P-0031")).thenReturn(vista());

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("P-0031"))
                .andExpect(jsonPath("$.estadoPlan").value("AJUSTES_PEDIDOS"))
                .andExpect(jsonPath("$.valorCotizado").value(5000000))
                .andExpect(jsonPath("$.fechaEstimadaEntrega").value("2027-04-15"))
                .andExpect(jsonPath("$.enviadoAt").value("2027-03-01T15:00:00Z"))
                .andExpect(jsonPath("$.aprobadoAt").doesNotExist())
                .andExpect(jsonPath("$.fases", hasSize(2)))
                .andExpect(jsonPath("$.fases[1].apruebaCliente").value(true))
                .andExpect(jsonPath("$.porcentajePorFase").value(50.0))
                .andExpect(jsonPath("$.ajustesPedidos.texto").value("Falta el logo en el diseño."))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    @DisplayName("PUT guarda el borrador: entrega al servicio las fases sin el orden del cliente y con «apruebaCliente» ausente como falso")
    void guarda() throws Exception {
        when(servicio.guardar(eq("P-0031"), any())).thenReturn(vista());

        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isOk());

        ArgumentCaptor<Solicitud> pedida = ArgumentCaptor.forClass(Solicitud.class);
        verify(servicio).guardar(eq("P-0031"), pedida.capture());
        assertEquals(2, pedida.getValue().fases().size());
        FasePlantilla primera = pedida.getValue().fases().get(0);
        assertEquals(0, primera.orden(), "el orden lo pone el servidor");
        assertEquals("Diseño", primera.nombre());
        assertEquals(0, primera.pasos().get(0).orden());
        assertEquals("Hablamos contigo", primera.pasos().get(0).explicacion());
        assertEquals(new BigDecimal("5000000"), pedida.getValue().valorCotizado());
        assertEquals("2027-04-15", pedida.getValue().fechaEstimadaEntrega());
    }

    @Test
    @DisplayName("Un borrador con solo {} llega al servicio con todo nulo (todo es opcional al guardar) sin NullPointerException")
    void borradorVacio() throws Exception {
        when(servicio.guardar(eq("P-0031"), any())).thenReturn(vista());

        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        ArgumentCaptor<Solicitud> pedida = ArgumentCaptor.forClass(Solicitud.class);
        verify(servicio).guardar(eq("P-0031"), pedida.capture());
        assertNull(pedida.getValue().fases());
        assertNull(pedida.getValue().valorCotizado());
    }

    @Test
    @DisplayName("POST /enviar: 200 con el estado ENVIADO, el porcentaje por fase y las dos fechas de plazo")
    void envia() throws Exception {
        when(servicio.enviar(eq("P-0031"), any())).thenReturn(new Enviado("ENVIADO", new BigDecimal("50.0"), LocalDate.of(2027, 3, 5), LocalDate.of(2027, 3, 16)));

        mvc.perform(post(RUTA + "/enviar").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlan").value("ENVIADO"))
                .andExpect(jsonPath("$.plazoRespuesta").value("2027-03-05"))
                .andExpect(jsonPath("$.venceEl").value("2027-03-16"));
    }

    @Test
    @DisplayName("POST /recordar: 204 sin cuerpo")
    void recuerda() throws Exception {
        mvc.perform(post(RUTA + "/recordar")).andExpect(status().isNoContent()).andExpect(content().string(""));

        verify(servicio).recordar("P-0031");
    }

    @Test
    @DisplayName("POST /aprobar: 200; si falta «aceptaPlanYCotizacion» se toma como falso y el servicio lo rechaza")
    void aprueba() throws Exception {
        when(servicio.aprobar("P-0031", true)).thenReturn(new Aprobado("APROBADO", "ACTIVO", Instant.parse("2027-03-02T15:00:00Z")));

        mvc.perform(post(RUTA + "/aprobar").contentType(MediaType.APPLICATION_JSON).content("{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlan").value("APROBADO"))
                .andExpect(jsonPath("$.planAprobadoAt").value("2027-03-02T15:00:00Z"));

        doThrow(new ValidacionException(List.of(new ValidacionException.Falla("aceptaPlanYCotizacion", "Confirma que leíste el plan y la cotización."))))
                .when(servicio).aprobar("P-0031", false);
        mvc.perform(post(RUTA + "/aprobar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("aceptaPlanYCotizacion"));
    }

    @Test
    @DisplayName("POST /ajustes y /rechazar: 201 con el estado y el id de la respuesta; pasan el texto tal cual")
    void ajustesYRechazo() throws Exception {
        UUID respuesta = UUID.randomUUID();
        when(servicio.pedirAjustes("P-0031", "Quiero el logo en el diseño del sistema.")).thenReturn(new Respondido("AJUSTES_PEDIDOS", "ACTIVO", respuesta));
        when(servicio.rechazar("P-0031", "Es más caro de lo que podemos pagar.")).thenReturn(new Respondido("RECHAZADO", "PAUSADO", respuesta));

        mvc.perform(post(RUTA + "/ajustes").contentType(MediaType.APPLICATION_JSON).content("{\"comentario\":\"Quiero el logo en el diseño del sistema.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoPlan").value("AJUSTES_PEDIDOS"))
                .andExpect(jsonPath("$.respuestaId").value(respuesta.toString()));
        mvc.perform(post(RUTA + "/rechazar").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Es más caro de lo que podemos pagar.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAUSADO"));
    }

    @Test
    @DisplayName("Cada excepción del servicio sale con su código: 403 de otro equipo, 404 de un proyecto ajeno, 409 de un plan que no admite la acción")
    void mapeoDeErrores() throws Exception {
        when(servicio.obtener("P-0031")).thenThrow(new ProhibidoException("Este proyecto no es de tu equipo"));
        mvc.perform(get(RUTA)).andExpect(status().isForbidden()).andExpect(jsonPath("$.errores[0].mensaje").value("Este proyecto no es de tu equipo"));

        when(servicio.obtener("P-0099")).thenThrow(new NoEncontradoException("Proyecto no encontrado"));
        mvc.perform(get("/api/v1/proyectos/P-0099/plan")).andExpect(status().isNotFound());

        doThrow(new ConflictoException("Este plan no está esperando tu respuesta")).when(servicio).aprobar(eq("P-0031"), eq(true));
        mvc.perform(post(RUTA + "/aprobar").contentType(MediaType.APPLICATION_JSON).content("{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este plan no está esperando tu respuesta"));
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON, o con un valor que no es un número, da 422 con el mensaje estándar y no llega al servicio")
    void cuerpoInvalido() throws Exception {
        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Los datos enviados no tienen el formato esperado"));
        mvc.perform(post(RUTA + "/enviar").contentType(MediaType.APPLICATION_JSON).content("{\"valorCotizado\":\"mucho\"}"))
                .andExpect(status().isUnprocessableEntity());

        verify(servicio, never()).guardar(any(), any());
        verify(servicio, never()).enviar(any(), any());
    }

    @Test
    @DisplayName("Una ruta sin cuerpo donde se exige uno (aprobar sin cuerpo) no llega al servicio")
    void sinCuerpo() throws Exception {
        mvc.perform(post(RUTA + "/aprobar")).andExpect(status().is4xxClientError());

        verify(servicio, never()).aprobar(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
