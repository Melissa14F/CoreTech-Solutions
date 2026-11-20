package co.coretech.portal.compartido.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.compartido.aplicacion.ConflictoException;
import co.coretech.portal.compartido.aplicacion.CuentaBloqueadaException;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;
import co.coretech.portal.compartido.aplicacion.ProhibidoException;
import co.coretech.portal.compartido.aplicacion.ReglaNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Respuestas del manejador global de errores con un controlador de prueba. Los filtros de seguridad
 * se desactivan: aquí solo importa la forma de la respuesta ({@code { "errores": [...] }}).
 */
@WebMvcTest(controllers = ManejadorGlobalErroresTest.ControladorDePrueba.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ ManejadorGlobalErroresTest.ControladorDePrueba.class, TraductorErroresBaseDatos.class })
class ManejadorGlobalErroresTest {

    @Autowired
    MockMvc mvc;

    @RestController
    static class ControladorDePrueba {

        record Formulario(
                @NotBlank(message = "Escribe tu nombre") String nombre,
                @Size(min = 20, max = 500, message = "Cuéntanos un poco más (mínimo 20 caracteres)") String descripcion) {
        }

        @PostMapping("/prueba/formulario")
        void formulario(@Valid @RequestBody Formulario formulario) {
        }

        @GetMapping("/prueba/negocio")
        void negocio(@RequestParam String tipo) {
            switch (tipo) {
                case "no-encontrado" -> throw new NoEncontradoException("Proyecto no encontrado");
                case "prohibido" -> throw new ProhibidoException("Solo el desarrollador (Product Owner) del equipo puede registrar trabajo en este proyecto");
                case "conflicto" -> throw new ConflictoException("Termina las correcciones antes de entregar");
                case "regla" -> throw new ReglaNegocioException("fechaEstimadaEntrega", "La entrega debe ser al menos 2 semanas después del inicio");
                case "bloqueada" -> throw new CuentaBloqueadaException("Cuenta bloqueada 15 minutos por seguridad");
                default -> throw new IllegalStateException("Detalle interno que no debe ver el usuario");
            }
        }

        /** Simula el error que PostgreSQL devuelve por una restricción o por el RAISE de un disparador. */
        @GetMapping("/prueba/base")
        void base(@RequestParam String sqlState, @RequestParam(required = false) String restriccion,
                @RequestParam(required = false) String contexto, @RequestParam(required = false) String columna,
                @RequestParam String mensaje) {
            StringBuilder campos = new StringBuilder("SERROR\0C").append(sqlState).append("\0M").append(mensaje).append('\0');
            if (restriccion != null) {
                campos.append('n').append(restriccion).append('\0');
            }
            if (contexto != null) {
                campos.append('W').append(contexto).append('\0');
            }
            if (columna != null) {
                campos.append('c').append(columna).append('\0');
            }
            throw new DataIntegrityViolationException("could not execute statement",
                    new PSQLException(new ServerErrorMessage(campos.toString())));
        }
    }

    // ---- Validación → 422 con un error por campo

    @Test
    void unFormularioInvalidoDevuelve422ConUnErrorPorCampo() throws Exception {
        mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"descripcion\":\"corta\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[?(@.campo == 'nombre')].mensaje").value("Escribe tu nombre"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'descripcion')].mensaje")
                        .value("Cuéntanos un poco más (mínimo 20 caracteres)"));
    }

    @Test
    void unJsonMalFormadoDevuelve422() throws Exception {
        mvc.perform(post("/prueba/formulario").contentType(MediaType.APPLICATION_JSON).content("{nombre"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Los datos enviados no tienen el formato esperado"));
    }

    @Test
    void unParametroFaltanteDevuelve422ConElCampo() throws Exception {
        mvc.perform(get("/prueba/negocio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("tipo"));
    }

    // ---- Excepciones de negocio

    @Test
    void noEncontradoDevuelve404() throws Exception {
        mvc.perform(get("/prueba/negocio").param("tipo", "no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Proyecto no encontrado"))
                .andExpect(jsonPath("$.errores[0].campo").doesNotExist());
    }

    @Test
    void prohibidoDevuelve403() throws Exception {
        mvc.perform(get("/prueba/negocio").param("tipo", "prohibido"))
                .andExpect(status().isForbidden());
    }

    @Test
    void conflictoDevuelve409() throws Exception {
        mvc.perform(get("/prueba/negocio").param("tipo", "conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Termina las correcciones antes de entregar"));
    }

    @Test
    void reglaDeNegocioDevuelve422ConSuCampo() throws Exception {
        mvc.perform(get("/prueba/negocio").param("tipo", "regla"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaEstimadaEntrega"));
    }

    @Test
    void cuentaBloqueadaDevuelve423() throws Exception {
        mvc.perform(get("/prueba/negocio").param("tipo", "bloqueada"))
                .andExpect(status().isLocked());
    }

    @Test
    void unErrorInesperadoDevuelve500SinElDetalleInterno() throws Exception {
        mvc.perform(get("/prueba/negocio").param("tipo", "otro"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errores[0].mensaje").value(ManejadorGlobalErrores.MENSAJE_INESPERADO))
                .andExpect(content().string(not(containsString("Detalle interno"))));
    }

    @Test
    void unaRutaQueNoExisteDevuelve404() throws Exception {
        mvc.perform(get("/prueba/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0].mensaje").value("No encontramos lo que buscas"));
    }

    // ---- Errores de la base de datos: tabla de docs/04-api.md

    @Test
    void franjaOcupadaDevuelve409() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "23505").param("restriccion", "ux_cita_franja")
                        .param("mensaje", "duplicate key value violates unique constraint \"ux_cita_franja\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHora"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La hora que elegiste ya no está disponible, elige otra"));
    }

    @Test
    void faseRepetidaEnLaPlantillaDevuelve422AunqueSeaUnicidad() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "23505").param("restriccion", "ux_plantilla_fase_nombre")
                        .param("mensaje", "duplicate key"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ya hay una fase con ese nombre en este servicio"));
    }

    @Test
    void unDisparadorDeDatosInvalidosDevuelve422() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_validar_cambio_plan() line 22 at RAISE")
                        .param("mensaje", "La fase debe conservar al menos un paso (RN-28)"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La fase debe conservar al menos un paso"))
                .andExpect(content().string(not(containsString("RN-28"))));
    }

    @Test
    void elMismoDisparadorConUnEstadoIncompatibleDevuelve409() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_validar_cambio_plan() line 4 at RAISE")
                        .param("mensaje", "Solo se cambia el plan de un proyecto activo con el plan aprobado (RN-30)"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Solo se cambia el plan de un proyecto activo con el plan aprobado"));
    }

    @Test
    void cuentaLaFuncionQueHizoElRaiseAunqueLaLlameOtra() throws Exception {
        String contexto = """
                PL/pgSQL function fn_revisar_fases(integer,integer,integer,text) line 3 at RAISE
                SQL statement "SELECT fn_revisar_fases(n, a, sp, 'El plan')"
                PL/pgSQL function fn_validar_estado_plan() line 11 at PERFORM""";
        mvc.perform(get("/prueba/base").param("sqlState", "P0001").param("contexto", contexto)
                        .param("mensaje", "El plan debe tener de 2 a 8 fases (tiene 1) (RN-28)"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El plan necesita al menos 2 fases"));
    }

    @Test
    void faseRepetidaEnElPlanDevuelve422AunqueSeaUnicidad() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "23505").param("restriccion", "ux_pf_nombre")
                        .param("mensaje", "duplicate key value violates unique constraint \"ux_pf_nombre\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ya hay una fase con ese nombre en el plan"));
    }

    @Test
    void unRolQueNoCorrespondeALaEspecialidadDevuelve422() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_validar_integrante() line 12 at RAISE")
                        .param("mensaje", "Con la especialidad QA no puede tener el rol BACKEND"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("rol"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El rol no corresponde a la especialidad del desarrollador"));
    }

    @Test
    void unIntegranteInactivoDevuelve422() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_validar_integrante() line 6 at RAISE")
                        .param("mensaje", "El desarrollador Diego Ramírez está inactivo"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("desarrolladorId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este desarrollador está inactivo"));
    }

    @Test
    void cambiarLaEntregaALaMismaFechaDevuelve422() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_validar_cambio_plan() line 7 at RAISE")
                        .param("mensaje", "La nueva fecha de entrega debe ser distinta de la actual (RN-30)"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("nuevaEntrega"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige una fecha distinta a la entrega actual"));
    }

    @Test
    void quitarUnPasoSinUnCambioAceptadoDevuelve409ConLaSalida() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_proteger_pasos() line 11 at RAISE")
                        .param("mensaje", "Quitar un paso del plan aprobado requiere un cambio autorizado por el administrador "
                                + "(y aceptado por el cliente si la fase la aprueba él) (RN-30)"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Para quitar un paso, propón un cambio de plan"))
                .andExpect(content().string(not(containsString("RN-30"))));
    }

    @Test
    void editarUnPasoPublicadoDevuelve409Generico() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_proteger_pasos() line 5 at RAISE")
                        .param("mensaje", "El paso \"Boceto\" ya se publicó al cliente: no se edita ni se borra (RN-09)"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("Estos datos cambiaron y la acción ya no se puede hacer. Recarga para ver lo último."));
    }

    @Test
    void unDisparadorQueNoEstaEnLaTablaDevuelve409Generico() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "P0001")
                        .param("contexto", "PL/pgSQL function fn_proteger_fases() line 9 at RAISE")
                        .param("mensaje", "La fase \"Diseño\" ya fue aprobada: no cambia (RN-30)"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("RN-30"))));
    }

    @Test
    void unCheckQueNoEstaEnLaTablaDevuelve422Generico() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "23514").param("restriccion", "ck_otra_regla")
                        .param("mensaje", "new row violates check constraint"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Hay un dato con un valor no permitido. Revisa el formulario."));
    }

    @Test
    void unDatoObligatorioFaltanteDevuelve422ConElCampoEnCamelCase() throws Exception {
        mvc.perform(get("/prueba/base").param("sqlState", "23502").param("columna", "fecha_hora")
                        .param("mensaje", "null value in column \"fecha_hora\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHora"));
    }
}
