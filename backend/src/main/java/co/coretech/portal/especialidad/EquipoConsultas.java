package co.coretech.portal.especialidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerta pública del módulo especialidad para los demás módulos (agenda, proyectos): quién lidera cada equipo
 * y cuánta carga tiene. Nadie más lee las tablas de equipos ni usa sus entidades. Incluye el cupo disponible y el Product Owner de un equipo (P14).
 */
public interface EquipoConsultas {

    /**
     * @param equipoId           id del equipo
     * @param productOwnerId     usuario (cuenta) del desarrollador (Product Owner) que lo lidera
     * @param productOwner       nombre del Product Owner: así se identifica el equipo
     * @param integrantes        personas del equipo
     * @param proyectosActivos   proyectos ACTIVO que atiende
     * @param maxProyectos       cupo (1 a 3, RN-05)
     */
    record Equipo(UUID equipoId, UUID productOwnerId, String productOwner, int integrantes, int proyectosActivos,
            int maxProyectos) {
    }

    /** Equipos activos con Product Owner con cuenta, de menor a mayor carga. */
    List<Equipo> activos();

    /** El equipo si existe y está activo. */
    Optional<Equipo> activo(UUID equipoId);

    /** Equipo activo que lidera ese usuario (Product Owner), si lidera alguno. */
    Optional<UUID> equipoDelProductOwner(UUID usuarioId);

    /** Product Owner (cuenta, nombre y correo) de un equipo activo, si tiene uno activo. */
    Optional<ProductOwner> productOwner(UUID equipoId);

    /** Proyectos que el equipo todavía puede recibir (cupo menos proyectos activos); 0 si no existe o no está activo. */
    int cupoDisponible(UUID equipoId);

    record ProductOwner(UUID usuarioId, String nombre, String email) {
    }

    /** Datos de varios equipos (también inactivos: una cita vieja conserva el suyo), por id. */
    java.util.Map<UUID, Equipo> porIds(java.util.Collection<UUID> equipoIds);
}
