package co.coretech.portal.acceso.aplicacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Una cuenta tal como la ve el administrador en Usuarios (HU-20).
 *
 * @param bloqueadaHasta fin del bloqueo por intentos fallidos, solo si sigue vigente
 * @param actividad      lo que tiene en la plataforma (columna "Actividad")
 */
public record UsuarioGestion(UUID usuarioId, String nombre, String email, String celular, String empresa, String rol,
        boolean activo, Instant bloqueadaHasta, boolean debeCambiarPassword, Instant ultimoAcceso, boolean anonimizada,
        Instant creadaEn, Actividad actividad) {

    /**
     * @param equipo                 equipo que lidera un Product Owner; null si no lidera ninguno
     * @param proyectosEnCurso       del cliente: activos, pausados o en garantía
     * @param proyectosFinalizados   del cliente
     */
    public record Actividad(Equipo equipo, int proyectosEnCurso, int proyectosFinalizados, int citasPendientes,
            int citasConfirmadas, int citasAtendidas, int citasCanceladas) {

        static final Actividad NINGUNA = new Actividad(null, 0, 0, 0, 0, 0, 0);
    }

    /** "Equipo de Andrés Pérez": n de m proyectos activos (RN-05). */
    public record Equipo(UUID equipoId, int proyectosActivos, int maxProyectos) {
    }
}
