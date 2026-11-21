package co.coretech.portal.seguimiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el módulo seguimiento necesita saber del proyecto al que pertenece una fase o un paso, y los cambios de estado del
 * proyecto que dependen de sus fases (garantía y cierre). Lo define seguimiento y lo implementa proyectos
 * ({@code ProyectosParaSeguimientoJdbc}): proyectos ya depende de seguimiento para los cambios de plan, así que al revés
 * habría un ciclo (CLAUDE.md, "Datos de otro módulo sin crear ciclos").
 */
public interface ProyectosParaSeguimiento {

    /**
     * @param avance         porcentaje que calcula la base (tg_recalcular_avance); se lee de nuevo después de cada cambio de fase
     * @param citaCodigo     código de la cita de origen (C-0045) o {@code null}
     * @param garantiaHasta  fin de la garantía (RN-31); nulo hasta que se completa la última fase
     */
    record Proyecto(UUID id, String codigo, String nombre, UUID clienteId, UUID equipoId, String servicio, String citaCodigo,
            String estado, String estadoPlan, BigDecimal avance, LocalDate fechaInicio, LocalDate fechaEntrega,
            LocalDate garantiaHasta) {
    }

    Optional<Proyecto> porCodigo(String codigo);

    Optional<Proyecto> porId(UUID proyectoId);

    /** Bloquea la fila del proyecto hasta el final de la transacción: dos registros a la vez no se pisan. */
    void bloquear(UUID proyectoId);

    /** ACTIVO → EN_GARANTIA con su fecha de fin (RN-13, RN-31). La base exige avance 100 % y fecha de garantía. */
    void iniciarGarantia(UUID proyectoId, LocalDate garantiaHasta);

    /** EN_GARANTIA → FINALIZADO (RN-13). La base exige que la fecha de cierre no sea anterior al fin de la garantía. */
    void finalizar(UUID proyectoId, Instant cuando);

    /** Proyectos EN_GARANTIA cuya garantía ya se cumplió el día {@code hoy} (RN-31). */
    List<UUID> garantiasCumplidas(LocalDate hoy);

    /** Proyectos FINALIZADO o CANCELADO desde antes de {@code limite} (conservación de datos, RNF-09). */
    List<UUID> cerradosAntesDe(Instant limite);
}
