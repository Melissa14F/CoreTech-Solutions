package co.coretech.portal.agenda.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.coretech.portal.agenda.service.ServicioCitas;
import co.coretech.portal.agenda.service.ServicioCitas.CitaConServicio;
import co.coretech.portal.agenda.service.ServicioCitas.DatosCita;
import co.coretech.portal.agenda.domain.Cita;
import co.coretech.portal.agenda.domain.Modalidad;
import co.coretech.portal.compartido.service.AuditoriaAccesoDenegado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.controller.ConfiguracionErroresPrueba;

/**
 * Pruebas de INTEGRACIÓN de la API de citas con {@code @WebMvcTest} + MockMvc: rutas HTTP, formato del JSON de respuesta y el
 * mapeo de excepciones del servicio a códigos HTTP por el manejador global REAL (esquema
 * {@code { "errores": [{ "campo", "mensaje" }] }}). El servicio es simulado (@MockitoBean): aquí no hay reglas ni base de datos.
 * <p>
 * Nota sobre el 400 de la guía: en este proyecto TODA validación de entrada responde <b>422</b> con el esquema estándar
 * (docs/04-api.md: «Validación (datos inválidos o regla fija) → 422»), también un cuerpo que ni siquiera es JSON. Un 400 solo lo
 * daría un parámetro de ruta mal formado. Por eso las pruebas de validación esperan 422, no 400.
 * Los permisos ({@code @PreAuthorize}) se prueban con la seguridad real en AccesoTest e InventarioEndpointsTest.
 */
@WebMvcTest(controllers = ControladorCitas.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorCitasWebMvcTest {

    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID SERVICIO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final String CUERPO_VALIDO = """
            {"servicioId":"%s","fechaHora":"2031-03-05T08:00:00-05:00","modalidad":"VIRTUAL",
             "descripcionNecesidad":"Necesito un sistema para llevar el inventario de mi salón de belleza.",
             "pais":"Colombia","zonaHoraria":"America/Bogota"}
            """.formatted(SERVICIO);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioCitas servicio;

    @MockitoBean
    SesionActual sesion;

    /** El manejador global registra los 403 en la auditoría; aquí no hay base de datos. */
    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    @BeforeEach
    void usuarioEnSesion() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(CLIENTE, "Laura Gómez", "laura@correo.com", "CLIENTE",
                Set.of("citas.agendar", "citas.cancelar_propia"), false, 0));
    }

    private static Cita cita() {
        return Cita.nueva("C-0099", CLIENTE, SERVICIO, Instant.parse("2031-03-05T13:00:00Z"), Modalidad.VIRTUAL,
                "Necesito un sistema para llevar el inventario.", "Colombia", "America/Bogota", Instant.parse("2031-03-04T13:00:00Z"));
    }

    @Test
    @DisplayName("POST /citas con datos válidos responde 201 y el JSON de la cita en las dos horas")
    void agendarResponde201ConElFormatoDelContrato() throws Exception {
        // Arrange
        when(servicio.agendar(eq(CLIENTE), any(DatosCita.class))).thenReturn(new CitaConServicio(cita(), "Sistema de inventario"));

        // Act + Assert
        mvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("C-0099"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.fechaHoraLocal").value("2031-03-05T08:00:00-05:00"))
                .andExpect(jsonPath("$.fechaHoraColombia").value("2031-03-05T08:00:00-05:00"))
                .andExpect(jsonPath("$.citaId").isNotEmpty());
    }

    @Test
    @DisplayName("el controlador pasa al servicio exactamente lo que envió el cliente y el id de la sesión")
    void agendarPasaLosDatosAlServicio() throws Exception {
        // Arrange
        when(servicio.agendar(eq(CLIENTE), any(DatosCita.class))).thenReturn(new CitaConServicio(cita(), "Sistema de inventario"));

        // Act
        mvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO)).andExpect(status().isCreated());

        // Assert
        verify(servicio).agendar(eq(CLIENTE), eq(new DatosCita(SERVICIO, "2031-03-05T08:00:00-05:00", "VIRTUAL",
                "Necesito un sistema para llevar el inventario de mi salón de belleza.", "Colombia", "America/Bogota")));
    }

    @Test
    @DisplayName("datos inválidos (campos obligatorios vacíos): 422 con un error por campo, esquema estándar")
    void validacionDeCamposDa422ConUnErrorPorCampo() throws Exception {
        // Arrange: el servicio detecta los campos obligatorios vacíos
        when(servicio.agendar(any(), any())).thenThrow(new ValidacionException(List.of(
                new ValidacionException.Falla("modalidad", "Elige si la reunión es presencial o virtual."),
                new ValidacionException.Falla("descripcionNecesidad", "Cuéntanos un poco más (mínimo 20 caracteres)"))));

        // Act + Assert
        mvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].campo").value("modalidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige si la reunión es presencial o virtual."))
                .andExpect(jsonPath("$.errores[1].campo").value("descripcionNecesidad"));
    }

    @Test
    @DisplayName("un cuerpo que no es JSON se rechaza con 422 y el mensaje estándar, antes de llegar al servicio")
    void cuerpoIlegibleDa422SinLlamarAlServicio() throws Exception {
        // Act + Assert
        mvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Los datos enviados no tienen el formato esperado"));
        verify(servicio, never()).agendar(any(), any());
    }

    @Test
    @DisplayName("franja ocupada: el ConflictoException del servicio sale como 409 con su campo y mensaje")
    void franjaOcupadaDa409() throws Exception {
        // Arrange
        when(servicio.agendar(any(), any())).thenThrow(new ConflictoException("fechaHora", "La hora que elegiste ya no está disponible, elige otra"));

        // Act + Assert
        mvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHora"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La hora que elegiste ya no está disponible, elige otra"));
    }

    @Test
    @DisplayName("una regla de negocio incumplida (ReglaNegocioException) sale como 422")
    void reglaDeNegocioDa422() throws Exception {
        // Arrange
        when(servicio.agendar(any(), any())).thenThrow(new ReglaNegocioException("fechaHora",
                "Las citas se agendan de lunes a viernes, con mínimo 24 horas y máximo 30 días de anticipación"));

        // Act + Assert
        mvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHora"));
    }

    @Test
    @DisplayName("servicio oculto o inexistente: NoEncontradoException sale como 404 con el esquema estándar")
    void servicioInexistenteDa404() throws Exception {
        // Arrange
        when(servicio.agendar(any(), any())).thenThrow(new NoEncontradoException("Este servicio ya no está disponible"));

        // Act + Assert
        mvc.perform(post("/api/v1/citas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este servicio ya no está disponible"));
    }

    @Test
    @DisplayName("PATCH /citas/{id}/cancelar: 200 con el estado CANCELADA y la hora de cancelación")
    void cancelarResponde200() throws Exception {
        // Arrange
        Cita cancelada = cita();
        cancelada.cancelar(CLIENTE, null, Instant.parse("2031-03-04T14:00:00Z"));
        when(servicio.cancelar(eq(CLIENTE), eq(cancelada.getId()), any())).thenReturn(cancelada);

        // Act + Assert
        mvc.perform(patch("/api/v1/citas/{id}/cancelar", cancelada.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Ya no necesito la cita, gracias.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.canceladaAt").value("2031-03-04T14:00:00Z"));
    }

    @Test
    @DisplayName("GET /citas devuelve la lista de «Mis citas» como un arreglo JSON")
    void misCitasDevuelveUnArreglo() throws Exception {
        // Arrange
        when(servicio.deCliente(CLIENTE)).thenReturn(List.of(new CitaConServicio(cita(), "Sistema de inventario")));

        // Act + Assert
        mvc.perform(get("/api/v1/citas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value("C-0099"))
                .andExpect(jsonPath("$[0].servicio").value("Sistema de inventario"))
                .andExpect(jsonPath("$[0].puedeCancelar").value(true));
    }
}
