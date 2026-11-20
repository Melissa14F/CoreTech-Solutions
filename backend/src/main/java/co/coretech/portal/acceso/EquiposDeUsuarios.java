package co.coretech.portal.acceso;

import java.util.Map;
import java.util.UUID;

/**
 * Equipo que lidera cada desarrollador (Product Owner) con cuenta, para Usuarios (HU-20). La define acceso
 * y la implementa especialidad, que también usará acceso para crear la cuenta del Product Owner (HU-26).
 */
public interface EquiposDeUsuarios {

    /** @param maxProyectos cupo del equipo (1 a 3, RN-05) */
    record Equipo(UUID equipoId, int maxProyectos) {
    }

    /** Usuario (Product Owner) → equipo activo que lidera. Quien no aparece ya no lidera un equipo. */
    Map<UUID, Equipo> porProductOwner();
}
