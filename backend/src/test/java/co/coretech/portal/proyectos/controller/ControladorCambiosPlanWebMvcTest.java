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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import co.coretech.portal.proyectos.service.ServicioCambiosPlan;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Aceptado;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.CambiosDelProyecto;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Creado;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Propuesta;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Respondido;

/**
 * Pruebas de INTEGRACIÓN de la API de cambios de plan con {@code @WebMvcTest} + MockMvc (HU-30, RN-30): proponer, autorizar,
 * aceptar y rechazar un cambio, y consultar los de un proyecto o los que esperan al administrador. Comprueba rutas, códigos
 * (201 al proponer), el cuerpo de cada propuesta y el mapeo de errores por el manejador global real. El servicio va simulado;
 * las reglas están en {@code ServicioCambiosPlanUnitarioTest}.
 */
@WebMvcTest(controllers = ControladorCambiosPlan.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorCambiosPlanWebMvcTest {

    static final UUID CAMBIO = UUID.fromString("00000000-0000-4000-8000-000000000071");
    static final String RUTA = "/api/v1/proyectos/P-0031/cambios-plan";
    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioCambiosPlan servicio;

    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    @Test
    @DisplayName("GET /proyectos/{codigo}/cambios-plan: 200 con si se puede proponer, el valor cotizado, el adicional aceptado y el total")
    void deUnProyecto() throws Exception {
        when(servicio.deProyecto("P-0031")).thenReturn(new CambiosDelProyecto(true, false, List.of(), List.of(), new BigDecimal("5000000"), new BigDecimal("500000"), new BigDecimal("5500000")));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeProponer").value(true))
                .andExpect(jsonPath("$.hayCambioAbierto").value(false))
                .andExpect(jsonPath("$.valorCotizado").value(5000000))
                .andExpect(jsonPath("$.valorAdicionalAceptado").value(500000))
                .andExpect(jsonPath("$.valorTotal").value(5500000))
                .andExpect(jsonPath("$.cambios", hasSize(0)));
    }

    @Test
    @DisplayName("POST: 201 con el cambio PROPUESTO y las fases antes y después; el servicio recibe la propuesta completa (fase nueva con sus pasos)")
    void propone() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.proponer(eq("P-0031"), any())).thenReturn(new Creado(id, "PROPUESTO", 4, 5, new BigDecimal("20.00")));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"AGREGAR_FASE","nombreFase":"Pruebas finales","posicion":3,"apruebaCliente":true,
                 "pasos":[{"titulo":"Probar con el cliente","explicacion":"Revisamos todo juntos"}],
                 "motivo":"El cliente necesita una ronda de pruebas antes de entregar.","nuevaEntrega":"2027-05-20","valorAdicional":800000}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cambioId").value(id.toString()))
                .andExpect(jsonPath("$.estado").value("PROPUESTO"))
                .andExpect(jsonPath("$.fasesAntes").value(4))
                .andExpect(jsonPath("$.fasesDespues").value(5))
                .andExpect(jsonPath("$.avanceDespues").value(20.00));

        ArgumentCaptor<Propuesta> pedida = ArgumentCaptor.forClass(Propuesta.class);
        verify(servicio).proponer(eq("P-0031"), pedida.capture());
        assertEquals("AGREGAR_FASE", pedida.getValue().tipo());
        assertEquals(3, pedida.getValue().posicion());
        assertEquals(Boolean.TRUE, pedida.getValue().apruebaCliente());
        assertEquals("Probar con el cliente", pedida.getValue().pasos().get(0).titulo());
        assertEquals(new BigDecimal("800000"), pedida.getValue().valorAdicional());
        assertNull(pedida.getValue().faseId());
    }

    @Test
    @DisplayName("Proponer con errores: 422 por campo, 409 si ya hay un cambio abierto, 403 si no es del equipo y 404 si el proyecto es ajeno")
    void proponeConErrores() throws Exception {
        when(servicio.proponer(any(), any())).thenThrow(new ValidacionException(List.of(new ValidacionException.Falla("motivo", "Escribe al menos 20 caracteres."),
                new ValidacionException.Falla("valorAdicional", "Escribe el valor adicional (puede ser $0)."))));
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"CAMBIAR_FECHA\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].campo").value("motivo"));

        doThrow(new ConflictoException("Este proyecto ya tiene un cambio de plan esperando respuesta")).when(servicio).proponer(any(), any());
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());

        doThrow(new ProhibidoException("Este proyecto no es de tu equipo")).when(servicio).proponer(any(), any());
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        doThrow(new NoEncontradoException("Proyecto no encontrado")).when(servicio).proponer(any(), any());
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON, o con un número que no es número, da 422 y no llega al servicio")
    void cuerpoInvalido() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("no es json")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"valorAdicional\":\"mucho\"}")).andExpect(status().isUnprocessableEntity());

        verify(servicio, never()).proponer(any(), any());
    }

    @Test
    @DisplayName("GET /cambios-plan: la lista de los que esperan la autorización del administrador")
    void porAutorizar() throws Exception {
        when(servicio.porAutorizar()).thenReturn(List.of());

        mvc.perform(get("/api/v1/cambios-plan")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("PATCH /autorizar: 200 con el estado AUTORIZADO y la hora; quien no es administrador recibe 403 y un cambio ya respondido, 409")
    void autoriza() throws Exception {
        when(servicio.autorizar(CAMBIO)).thenReturn(new Respondido(CAMBIO, "AUTORIZADO", AHORA));

        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/autorizar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AUTORIZADO"))
                .andExpect(jsonPath("$.autorizadoAt").value("2027-03-02T15:00:00Z"));

        doThrow(new ProhibidoException("Solo la administración autoriza los cambios del plan")).when(servicio).autorizar(CAMBIO);
        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/autorizar")).andExpect(status().isForbidden());

        doThrow(new ConflictoException("Este cambio de plan ya fue respondido")).when(servicio).autorizar(CAMBIO);
        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/autorizar")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /aceptar: 200 con cómo quedó el plan (fases, avance, nueva entrega y valor total)")
    void acepta() throws Exception {
        when(servicio.aceptar(CAMBIO)).thenReturn(new Aceptado("ACEPTADO", 5, new BigDecimal("20.00"), LocalDate.of(2027, 5, 20), new BigDecimal("6300000")));

        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/aceptar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.fases").value(5))
                .andExpect(jsonPath("$.fechaEstimadaEntrega").value("2027-05-20"))
                .andExpect(jsonPath("$.valorTotal").value(6300000));
    }

    @Test
    @DisplayName("Aceptar: si el plan cambió desde la propuesta 409, y un cambio de otro cliente o inexistente 404")
    void aceptaConErrores() throws Exception {
        doThrow(new ConflictoException("El plan cambió desde que se propuso este cambio: pídele al equipo que lo proponga de nuevo")).when(servicio).aceptar(CAMBIO);
        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/aceptar")).andExpect(status().isConflict());

        doThrow(new NoEncontradoException("Cambio de plan no encontrado")).when(servicio).aceptar(CAMBIO);
        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/aceptar")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /rechazar: pasa el motivo al servicio y responde 200; un motivo corto es 422 en el campo motivo")
    void rechaza() throws Exception {
        when(servicio.rechazar(CAMBIO, "No hay presupuesto para este cambio ahora.")).thenReturn(new Respondido(CAMBIO, "RECHAZADO_ADMIN", null));

        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/rechazar").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"No hay presupuesto para este cambio ahora.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO_ADMIN"))
                .andExpect(jsonPath("$.autorizadoAt").value(org.hamcrest.Matchers.nullValue()));

        doThrow(new ValidacionException(List.of(new ValidacionException.Falla("motivo", "Escribe al menos 10 caracteres.")))).when(servicio).rechazar(any(), any());
        mvc.perform(patch("/api/v1/cambios-plan/" + CAMBIO + "/rechazar").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
    }

    @Test
    @DisplayName("Un id de cambio que no es UUID: 422 en el campo cambioId")
    void idMalFormado() throws Exception {
        mvc.perform(patch("/api/v1/cambios-plan/abc/aceptar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("cambioId"));
    }
}
