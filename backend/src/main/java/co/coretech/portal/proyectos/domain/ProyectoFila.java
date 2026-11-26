package co.coretech.portal.proyectos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un proyecto con lo que la lista del administrador muestra.
 *
 * @param riesgo        por qué está "en riesgo" (HU-17): ENTREGA_VENCIDA, POCO_AVANCE, SIN_AVANCES o PLAN_SIN_RESPUESTA;
 *                      nulo si no lo está. Se calcula en la consulta, no se guarda.
 * @param ultimoAvance  fecha del último paso hecho o, si no hay ninguno, la de la aprobación del plan
 */
public record ProyectoFila(UUID id, String codigo, String nombre, String alcance, UUID citaId, UUID clienteId, UUID servicioId,
        UUID equipoId, LocalDate fechaInicio, LocalDate fechaEntrega, String estado, String estadoPlan,
        BigDecimal valorCotizado, LocalDate planPlazoRespuesta, LocalDate planVenceEl, BigDecimal avance,
        String motivoEstado, LocalDate garantiaHasta, Instant finalizadoAt, Instant creadoAt, Instant actualizadoAt,
        int fases, Integer faseActualOrden, String faseActualNombre, LocalDate ultimoAvance, String riesgo) {
}
