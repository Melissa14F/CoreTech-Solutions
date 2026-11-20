package co.coretech.portal.especialidad.dominio;

import java.time.LocalDate;
import java.util.UUID;

/** Persona del personal técnico (HU-25). Solo el Product Owner de un equipo tiene {@code usuarioId} (cuenta). */
public record Desarrollador(UUID id, String nombre, String correo, String celular, Especialidad especialidad,
        UUID usuarioId, boolean activo, LocalDate fechaIngreso) {
}
