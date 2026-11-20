package co.coretech.portal.especialidad.dominio;

import java.util.UUID;

/** Un desarrollador dentro de su equipo, con su rol. Un desarrollador está en un solo equipo (RN-26). */
public record Integrante(UUID equipoId, UUID desarrolladorId, RolEquipo rol) {
}
