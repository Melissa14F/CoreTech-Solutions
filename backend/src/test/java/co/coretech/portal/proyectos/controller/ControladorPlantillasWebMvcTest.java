package co.coretech.portal.proyectos.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.controller.ConfiguracionErroresPrueba;
import co.coretech.portal.proyectos.service.ServicioPlantillas;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;

/**
 * Pruebas de INTEGRACIÓN de la API de plantillas con {@code @WebMvcTest} + MockMvc (HU-27, RN-27, RN-28): forma del JSON al
 * leer y al guardar, cómo el controlador arma las fases que entrega al servicio (el orden lo pone el servidor) y el mapeo de
 * errores. El servicio va simulado; las reglas están en {@code ServicioPlantillasUnitarioTest} y {@code PlantillasTest}.
 */
@WebMvcTest(controllers = ControladorPlantillas.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorPlantillasWebMvcTest {

    static final UUID SERVICIO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final String RUTA = "/api/v1/servicios/" + SERVICIO + "/plantilla";
    static final String CUERPO = """
            {"fases":[
              {"nombre":"Diseño","queEs":"Se define qué se construye","apruebaCliente":false,
               "pasos":[{"titulo":"Reunir requisitos","explicacion":"Hablamos contigo"}]},
              {"nombre":"Entrega","apruebaCliente":true,"pasos":[{"titulo":"Capacitar al equipo"}]}
            ]}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioPlantillas servicio;

    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    private static List<FasePlantilla> plantilla() {
        return List.of(
                new FasePlantilla(1, "Diseño", "Se define qué se construye", false, List.of(new PasoPlantilla(1, "Reunir requisitos", "Hablamos contigo"))),
                new FasePlantilla(2, "Entrega", null, true, List.of(new PasoPlantilla(1, "Capacitar al equipo", null))));
    }

    @Test
    @DisplayName("GET /servicios/{id}/plantilla: 200 con las fases, sus pasos y el orden que fijó el servidor")
    void obtiene() throws Exception {
        when(servicio.obtener(SERVICIO)).thenReturn(plantilla());

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases", hasSize(2)))
                .andExpect(jsonPath("$.fases[0].orden").value(1))
                .andExpect(jsonPath("$.fases[0].nombre").value("Diseño"))
                .andExpect(jsonPath("$.fases[0].apruebaCliente").value(false))
                .andExpect(jsonPath("$.fases[0].pasos[0].titulo").value("Reunir requisitos"))
                .andExpect(jsonPath("$.fases[1].apruebaCliente").value(true))
                .andExpect(jsonPath("$.fases[1].queEs").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("PUT: responde 200 con lo guardado y entrega al servicio las fases sin el orden del cliente")
    void guarda() throws Exception {
        when(servicio.guardar(eq(SERVICIO), any())).thenReturn(plantilla());

        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases", hasSize(2)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FasePlantilla>> fases = ArgumentCaptor.forClass(List.class);
        verify(servicio).guardar(eq(SERVICIO), fases.capture());
        assertEquals(2, fases.getValue().size());
        FasePlantilla primera = fases.getValue().get(0);
        assertEquals("Diseño", primera.nombre());
        assertEquals(0, primera.orden(), "el orden lo pone el servidor, no el cliente");
        assertEquals(false, primera.apruebaCliente());
        assertEquals("Hablamos contigo", primera.pasos().get(0).explicacion());
        assertEquals(true, fases.getValue().get(1).apruebaCliente());
    }

    @Test
    @DisplayName("Si falta «apruebaCliente» se toma como falso, no como error")
    void apruebaClienteAusenteEsFalso() throws Exception {
        when(servicio.guardar(eq(SERVICIO), any())).thenReturn(plantilla());

        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"fases\":[{\"nombre\":\"Diseño\",\"pasos\":[{\"titulo\":\"Reunir\"}]}]}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FasePlantilla>> fases = ArgumentCaptor.forClass(List.class);
        verify(servicio).guardar(eq(SERVICIO), fases.capture());
        assertEquals(false, fases.getValue().get(0).apruebaCliente());
    }

    @Test
    @DisplayName("Sin la lista de fases el controlador pasa nulo y deja que el servicio responda 422 (no hay NullPointerException)")
    void sinFases() throws Exception {
        when(servicio.guardar(eq(SERVICIO), any())).thenThrow(new ValidacionException(List.of(
                new ValidacionException.Falla("fases", "El plan necesita al menos 2 fases"))));

        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fases"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El plan necesita al menos 2 fases"));
    }

    @Test
    @DisplayName("Un servicio que no existe: 404 con «Este servicio ya no está disponible»")
    void servicioInexistente() throws Exception {
        when(servicio.obtener(any())).thenThrow(new NoEncontradoException("Este servicio ya no está disponible"));

        mvc.perform(get(RUTA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este servicio ya no está disponible"));
    }

    @Test
    @DisplayName("Un id de servicio que no es UUID: 422 en el campo servicioId")
    void idMalFormado() throws Exception {
        mvc.perform(get("/api/v1/servicios/abc/plantilla"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("servicioId"));
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON: 422 con el mensaje estándar")
    void cuerpoInvalido() throws Exception {
        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Los datos enviados no tienen el formato esperado"));
    }

    @Test
    @DisplayName("Una fase o un paso nulo dentro de la lista llega al servicio como nulo (él lo rechaza con 422)")
    void elementosNulos() throws Exception {
        when(servicio.guardar(eq(SERVICIO), any())).thenReturn(plantilla());

        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"fases\":[null,{\"nombre\":\"Entrega\",\"pasos\":[null]}]}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FasePlantilla>> fases = ArgumentCaptor.forClass(List.class);
        verify(servicio).guardar(eq(SERVICIO), fases.capture());
        assertEquals(null, fases.getValue().get(0));
        assertEquals(null, fases.getValue().get(1).pasos().get(0));
    }
}
