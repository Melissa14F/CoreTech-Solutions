package co.coretech.portal.comunicacion;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el módulo comunicación necesita saber del proyecto sobre el que se escriben el cliente y el desarrollador
 * (Product Owner), y del equipo que lo atiende. Lo define comunicación y lo implementa proyectos
 * ({@code ProyectosParaComunicacionJdbc}): proyectos y especialidad ya dependen de comunicación para publicar avisos, así que
 * al revés habría un ciclo (CLAUDE.md, "Datos de otro módulo sin crear ciclos").
 */
public interface ProyectosParaComunicacion {

    record Proyecto(UUID id, String codigo, String nombre, UUID clienteId, UUID equipoId, String estado, String estadoPlan) {
    }

    /** Una fase del plan del proyecto. */
    record Fase(UUID id, int orden, String nombre) {
    }

    /** El desarrollador (Product Owner) del equipo: es el único del equipo con cuenta. */
    record Persona(UUID usuarioId, String nombre, String email) {
    }

    Optional<Proyecto> porCodigo(String codigo);

    Optional<Proyecto> porId(UUID proyectoId);

    /** Bloquea la fila del proyecto hasta el final de la transacción: dos solicitudes a la vez no se pisan. */
    void bloquear(UUID proyectoId);

    /** El Product Owner del equipo, o vacío si el equipo no tiene cuenta activa. */
    Optional<Persona> productOwnerDe(UUID equipoId);

    /** El equipo del que es Product Owner el usuario, o vacío si no lo es de ninguno. */
    Optional<UUID> equipoDelProductOwner(UUID usuarioId);

    /** La fase que el equipo está trabajando o que espera la respuesta del cliente: la primera que aún no está aprobada. */
    Optional<Fase> faseEnCurso(UUID proyectoId);

    /**
     * Si el equipo ya tiene una cita o una reunión programada que se cruza con la hora de una hora que empieza en
     * {@code inicio}. {@code excluirSolicitud} deja fuera la solicitud que se está programando.
     */
    boolean horarioOcupado(UUID equipoId, Instant inicio, UUID excluirSolicitud);
}
