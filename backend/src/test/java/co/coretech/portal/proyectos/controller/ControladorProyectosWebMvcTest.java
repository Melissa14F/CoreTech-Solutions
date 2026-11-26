package co.coretech.portal.proyectos.controller;

import static org.hamcrest.Matchers.hasSize;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.controller.ConfiguracionErroresPrueba;
import co.coretech.portal.especialidad.EquipoConsultas.Equipo;
import co.coretech.portal.proyectos.service.ServicioProyectos;
import co.coretech.portal.proyectos.service.ServicioProyectos.CambioFecha;
import co.coretech.portal.proyectos.service.ServicioProyectos.CitaAtendida;
import co.coretech.portal.proyectos.service.ServicioProyectos.Creacion;
import co.coretech.portal.proyectos.service.ServicioProyectos.Creado;
import co.coretech.portal.proyectos.service.ServicioProyectos.Filtros;
import co.coretech.portal.proyectos.service.ServicioProyectos.Listado;
import co.coretech.portal.proyectos.service.ServicioProyectos.ProyectoPropio;
import co.coretech.portal.proyectos.service.ServicioProyectos.ResultadoEdicion;
import co.coretech.portal.proyectos.service.ServicioProyectos.VistaProyecto;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Pruebas de INTEGRACIÓN de la API de proyectos con {@code @WebMvcTest} + MockMvc (HU-02 y HU-17): crear (201), citas atendidas,
 * equipos con su cupo, «Mis proyectos», la lista con filtros, editar y cambiar el estado. Comprueba rutas, parámetros de
 * consulta, la forma del JSON (anidados, fechas ISO 8601, nulos) y el mapeo de errores por el manejador global real. El servicio va
 * simulado; sus reglas están en {@code ServicioProyectosUnitarioTest}.
 */
@WebMvcTest(controllers = ControladorProyectos.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionErroresPrueba.class)
class ControladorProyectosWebMvcTest {

    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID CITA = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID SERVICIO = UUID.fromString("00000000-0000-4000-8000-0000000000a9");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioProyectos servicio;

    @MockitoBean
    AuditoriaAccesoDenegado auditoriaDenegados;

    private static VistaProyecto vista(String riesgo, Integer faseActual) {
        ProyectoFila f = new ProyectoFila(PROYECTO, "P-0031", "Inventario del salón", "Un sistema para llevar el inventario.", CITA, CLIENTE, SERVICIO, EQUIPO,
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 15), "ACTIVO", "APROBADO", new BigDecimal("5000000"), null, null, new BigDecimal("40.00"),
                null, null, null, AHORA, AHORA, 5, faseActual, faseActual == null ? null : "Construcción", LocalDate.of(2027, 3, 2), riesgo);
        return new VistaProyecto(f, "Laura Gómez", "laura@correo.com", "Sistema de inventario", PO, "Andrés Pérez", 2, 3);
    }

    @Test
    @DisplayName("POST /proyectos: 201 con el código, el plan en BORRADOR y cuántas fases se copiaron; pasa los datos tal cual")
    void crea() throws Exception {
        when(servicio.crear(any(Creacion.class))).thenReturn(new Creado(PROYECTO, "P-0032", "BORRADOR", 5));

        mvc.perform(post("/api/v1/proyectos").contentType(MediaType.APPLICATION_JSON).content("""
                {"citaId":"%s","nombre":"Inventario del salón","alcance":"Un sistema para llevar el inventario.",
                 "fechaInicio":"2027-03-03","fechaEstimadaEntrega":"2027-04-20","equipoId":"%s"}
                """.formatted(CITA, EQUIPO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("P-0032"))
                .andExpect(jsonPath("$.estadoPlan").value("BORRADOR"))
                .andExpect(jsonPath("$.fases").value(5));

        verify(servicio).crear(new Creacion(CITA, "Inventario del salón", "Un sistema para llevar el inventario.", "2027-03-03", "2027-04-20", EQUIPO));
    }

    @Test
    @DisplayName("Crear con datos inválidos (422 con un error por campo), cita sin atender (409) o cita que no existe (404)")
    void crearConErrores() throws Exception {
        when(servicio.crear(any())).thenThrow(new ValidacionException(List.of(
                new ValidacionException.Falla("nombre", "Escribe el nombre que verá el cliente."),
                new ValidacionException.Falla("equipoId", "Elige el equipo responsable."))));
        mvc.perform(post("/api/v1/proyectos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[1].campo").value("equipoId"));

        doThrow(new ConflictoException("citaId", "Esta cita ya se convirtió en el proyecto P-0030")).when(servicio).crear(any());
        mvc.perform(post("/api/v1/proyectos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].campo").value("citaId"));

        doThrow(new NoEncontradoException("No encontramos esa cita")).when(servicio).crear(any());
        mvc.perform(post("/api/v1/proyectos").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un citaId que no es un UUID o un cuerpo que no es JSON: 422 y no llega al servicio")
    void cuerpoInvalido() throws Exception {
        mvc.perform(post("/api/v1/proyectos").contentType(MediaType.APPLICATION_JSON).content("{\"citaId\":\"no-es-uuid\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Los datos enviados no tienen el formato esperado"));
        mvc.perform(post("/api/v1/proyectos").contentType(MediaType.APPLICATION_JSON).content("no es json")).andExpect(status().isUnprocessableEntity());

        verify(servicio, never()).crear(any());
    }

    @Test
    @DisplayName("GET /proyectos/citas-atendidas: las citas con el código del proyecto si ya generaron uno")
    void citasAtendidas() throws Exception {
        when(servicio.citasAtendidas()).thenReturn(List.of(
                new CitaAtendida(CITA, "C-0045", "Laura Gómez", "laura@correo.com", SERVICIO, "Sistema de inventario", AHORA, EQUIPO, "Andrés Pérez", "P-0030"),
                new CitaAtendida(UUID.randomUUID(), "C-0046", "Tomás Ruiz", "tomas@correo.com", SERVICIO, "Sistema de inventario", AHORA, null, null, null)));

        mvc.perform(get("/api/v1/proyectos/citas-atendidas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].proyecto").value("P-0030"))
                .andExpect(jsonPath("$[0].fechaHora").value("2027-03-02T15:00:00Z"))
                .andExpect(jsonPath("$[1].proyecto").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].productOwner").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("GET /proyectos/equipos: calcula el cupo libre y nunca lo deja negativo (RN-05)")
    void equipos() throws Exception {
        when(servicio.equiposParaElegir()).thenReturn(List.of(
                new Equipo(EQUIPO, PO, "Andrés Pérez", 3, 2, 3),
                new Equipo(UUID.randomUUID(), UUID.randomUUID(), "Camila Ruiz", 2, 3, 3),
                new Equipo(UUID.randomUUID(), UUID.randomUUID(), "Sebastián Mora", 2, 4, 3)));

        mvc.perform(get("/api/v1/proyectos/equipos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cupoLibre").value(1))
                .andExpect(jsonPath("$[1].cupoLibre").value(0))
                // con más proyectos activos que el cupo (4 de 3) no baja de 0
                .andExpect(jsonPath("$[2].cupoLibre").value(0));
    }

    @Test
    @DisplayName("GET /proyectos/propios: la lista de quien tiene la sesión, con lo que le toca hacer")
    void propios() throws Exception {
        when(servicio.propios()).thenReturn(List.of(new ProyectoPropio("P-0031", "Inventario del salón", "Sistema de inventario", "ACTIVO", "ENVIADO", "Laura Gómez",
                "Andrés Pérez", LocalDate.of(2027, 4, 15), new BigDecimal("5000000"), new BigDecimal("0"), 5, 1, "Diseño", LocalDate.of(2027, 3, 5), LocalDate.of(2027, 3, 16), "REVISAR_PROPUESTA")));

        mvc.perform(get("/api/v1/proyectos/propios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].porHacer").value("REVISAR_PROPUESTA"))
                .andExpect(jsonPath("$[0].fechaEstimadaEntrega").value("2027-04-15"));
    }

    @Test
    @DisplayName("GET /proyectos: pasa los filtros de la consulta y devuelve los proyectos, los contadores y la paginación")
    void lista() throws Exception {
        Map<String, Integer> porFiltro = new LinkedHashMap<>();
        porFiltro.put("todas", 1);
        porFiltro.put("riesgo", 1);
        when(servicio.listar(any(Filtros.class))).thenReturn(new Listado(List.of(vista("ENTREGA_VENCIDA", 2)), porFiltro, 1, 10, 11, 2));

        mvc.perform(get("/api/v1/proyectos").param("estado", "riesgo").param("equipoId", EQUIPO.toString()).param("q", "salón").param("pagina", "1").param("tamano", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyectos", hasSize(1)))
                .andExpect(jsonPath("$.proyectos[0].codigo").value("P-0031"))
                .andExpect(jsonPath("$.proyectos[0].cliente.nombre").value("Laura Gómez"))
                .andExpect(jsonPath("$.proyectos[0].equipo.maxProyectos").value(3))
                .andExpect(jsonPath("$.proyectos[0].faseActual.nombre").value("Construcción"))
                .andExpect(jsonPath("$.proyectos[0].enRiesgo").value(true))
                .andExpect(jsonPath("$.proyectos[0].riesgo").value("ENTREGA_VENCIDA"))
                .andExpect(jsonPath("$.porFiltro.riesgo").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        verify(servicio).listar(new Filtros("riesgo", EQUIPO, "salón", 1, 10));
    }

    @Test
    @DisplayName("GET /proyectos sin parámetros: los filtros llegan nulos (el servicio aplica los valores por omisión); un proyecto sin riesgo ni fase actual lo muestra nulo")
    void listaSinParametros() throws Exception {
        when(servicio.listar(any(Filtros.class))).thenReturn(new Listado(List.of(vista(null, null)), Map.of(), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/proyectos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyectos[0].enRiesgo").value(false))
                .andExpect(jsonPath("$.proyectos[0].faseActual").value(org.hamcrest.Matchers.nullValue()));

        verify(servicio).listar(new Filtros(null, null, null, null, null));
    }

    @Test
    @DisplayName("Un filtro inválido da 422 en el campo estado; un equipoId o una página que no son del tipo esperado, 422 con el nombre del parámetro")
    void listaConParametrosInvalidos() throws Exception {
        when(servicio.listar(any())).thenThrow(new ValidacionException(List.of(new ValidacionException.Falla("estado", "Elige un estado de la lista."))));
        mvc.perform(get("/api/v1/proyectos").param("estado", "inventado"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));

        mvc.perform(get("/api/v1/proyectos").param("equipoId", "no-es-uuid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("equipoId"));
        mvc.perform(get("/api/v1/proyectos").param("pagina", "abc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("pagina"));
    }

    @Test
    @DisplayName("PATCH /proyectos/{codigo}: 200 con el proyecto y, solo con el plan aprobado, el cambio de fecha ya autorizado")
    void edita() throws Exception {
        UUID cambio = UUID.randomUUID();
        when(servicio.editar(eq("P-0031"), any())).thenReturn(new ResultadoEdicion(vista(null, 1), new CambioFecha(cambio, "AUTORIZADO", LocalDate.of(2027, 4, 22))));

        mvc.perform(patch("/api/v1/proyectos/P-0031").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fechaEstimadaEntrega\":\"2027-04-22\",\"motivo\":\"El cliente necesita la entrega una semana después.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyecto.codigo").value("P-0031"))
                .andExpect(jsonPath("$.cambioFecha.estado").value("AUTORIZADO"))
                .andExpect(jsonPath("$.cambioFecha.nuevaFecha").value("2027-04-22"));

        ArgumentCaptor<ServicioProyectos.Edicion> pedida = ArgumentCaptor.forClass(ServicioProyectos.Edicion.class);
        verify(servicio).editar(eq("P-0031"), pedida.capture());
        org.junit.jupiter.api.Assertions.assertNull(pedida.getValue().nombre(), "lo que no se envía queda nulo: solo se cambia lo enviado");
        org.junit.jupiter.api.Assertions.assertEquals("2027-04-22", pedida.getValue().fechaEstimadaEntrega());
    }

    @Test
    @DisplayName("Editar sin cambio de fecha: cambioFecha viene nulo; con el plan enviado, 409; sin cambios, 422")
    void editaConErrores() throws Exception {
        when(servicio.editar(eq("P-0031"), any())).thenReturn(new ResultadoEdicion(vista(null, 1), null));
        mvc.perform(patch("/api/v1/proyectos/P-0031").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Otro nombre\",\"motivo\":\"Corregir el nombre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambioFecha").value(org.hamcrest.Matchers.nullValue()));

        doThrow(new ConflictoException("fechaEstimadaEntrega", "El plan está enviado al cliente; espera su respuesta para cambiar la entrega")).when(servicio).editar(eq("P-0031"), any());
        mvc.perform(patch("/api/v1/proyectos/P-0031").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());

        doThrow(new co.coretech.portal.compartido.exception.ReglaNegocioException("No hay cambios para guardar")).when(servicio).editar(eq("P-0031"), any());
        mvc.perform(patch("/api/v1/proyectos/P-0031").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("No hay cambios para guardar"));
    }

    @Test
    @DisplayName("PATCH /proyectos/{codigo}/estado: pasa estado, motivo y equipo al servicio y devuelve el proyecto; un proyecto que no existe es 404")
    void cambiaElEstado() throws Exception {
        when(servicio.cambiarEstado("P-0031", "ACTIVO", "El cliente retomó el proyecto.", EQUIPO)).thenReturn(vista(null, 1));

        mvc.perform(patch("/api/v1/proyectos/P-0031/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ACTIVO\",\"motivo\":\"El cliente retomó el proyecto.\",\"equipoId\":\"%s\"}".formatted(EQUIPO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        when(servicio.cambiarEstado(eq("P-9999"), any(), any(), any())).thenThrow(new NoEncontradoException("Proyecto no encontrado"));
        mvc.perform(patch("/api/v1/proyectos/P-9999/estado").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PAUSADO\",\"motivo\":\"Pausa por falta de pago.\"}"))
                .andExpect(status().isNotFound());
    }
}
