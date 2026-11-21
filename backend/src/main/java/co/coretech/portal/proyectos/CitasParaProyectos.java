package co.coretech.portal.proyectos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que proyectos necesita saber de las citas para crear un proyecto desde una cita atendida (HU-02, RN-04).
 * La implementa el módulo agenda, dueño de la tabla citas (así proyectos no entra a sus repositorios).
 */
public interface CitasParaProyectos {

    /**
     * @param estado   estado de la cita tal como se guarda (ATENDIDA, PENDIENTE…)
     * @param equipoId equipo que la atendió; nulo si nunca se asignó
     */
    record CitaOrigen(UUID citaId, String codigo, String estado, UUID clienteId, UUID servicioId, Instant fechaHora,
            UUID equipoId) {

        public boolean atendida() {
            return "ATENDIDA".equals(estado);
        }
    }

    Optional<CitaOrigen> buscar(UUID citaId);

    /** Citas ATENDIDA, las más recientes primero. */
    List<CitaOrigen> atendidas();
}
