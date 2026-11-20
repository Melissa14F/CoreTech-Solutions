package co.coretech.portal.especialidad.dominio;

import java.util.UUID;

/** Equipo de trabajo; se identifica por su Product Owner (HU-26). Cupo de 1 a 3 proyectos activos (RN-05). */
public record Equipo(UUID id, int maxProyectos, boolean activo) {
}
