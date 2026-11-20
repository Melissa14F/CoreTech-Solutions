package co.coretech.portal.acceso;

import java.util.Map;
import java.util.UUID;

/**
 * Proyectos de cada cliente y de cada equipo, para Usuarios (HU-20): la actividad de la lista y la regla
 * "un desarrollador (Product Owner) con proyectos activos no se desactiva" (RN-26, D-34).
 * La define acceso y la implementa proyectos.
 */
public interface ProyectosDeUsuarios {

    /** @param enCurso activos, pausados o en garantía; @param finalizados terminados (los cancelados no cuentan) */
    record Conteo(int enCurso, int finalizados) {
    }

    /** Cliente → sus proyectos (solo los clientes que tienen alguno). */
    Map<UUID, Conteo> porCliente();

    /** Equipo → proyectos en estado ACTIVO, los que ocupan cupo (RN-05). */
    Map<UUID, Integer> activosPorEquipo();
}
