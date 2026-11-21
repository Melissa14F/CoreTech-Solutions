package co.coretech.portal.seguimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el módulo seguimiento necesita saber del proyecto al que pertenece una fase o un paso. Lo define seguimiento
 * y lo implementa proyectos ({@code ProyectosParaSeguimientoJdbc}): proyectos ya depende de seguimiento para los
 * cambios de plan, así que al revés habría un ciclo (CLAUDE.md, "Datos de otro módulo sin crear ciclos").
 */
public interface ProyectosParaSeguimiento {

    /**
     * @param avance      porcentaje que calcula la base (tg_recalcular_avance); se lee de nuevo después de cada cambio de fase
     * @param citaCodigo  código de la cita de origen (C-0045) o {@code null}
     */
    record Proyecto(UUID id, String codigo, String nombre, UUID clienteId, UUID equipoId, String servicio, String citaCodigo,
            String estado, String estadoPlan, BigDecimal avance, LocalDate fechaInicio, LocalDate fechaEntrega) {
    }

    Optional<Proyecto> porCodigo(String codigo);

    Optional<Proyecto> porId(UUID proyectoId);

    /** Bloquea la fila del proyecto hasta el final de la transacción: dos registros a la vez no se pisan. */
    void bloquear(UUID proyectoId);
}
