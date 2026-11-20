package co.coretech.portal.especialidad.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import co.coretech.portal.especialidad.dominio.Especialidad;
import co.coretech.portal.especialidad.dominio.RolEquipo;

/** Lo que la API muestra de desarrolladores y equipos (HU-25, HU-26). Nunca contraseñas ni ids de cuenta. */
public final class Vistas {

    private Vistas() {
    }

    /** @param equipo equipo donde está (null si no tiene); @param rol su rol allí */
    public record DesarrolladorVista(UUID desarrolladorId, String nombre, String correo, String celular,
            Especialidad especialidad, boolean activo, LocalDate fechaIngreso, boolean tieneCuenta,
            EquipoResumen equipo, RolEquipo rol) {
    }

    public record EquipoResumen(UUID equipoId, String productOwner) {
    }

    public record MiembroVista(UUID desarrolladorId, String nombre, String correo, Especialidad especialidad, boolean activo,
            RolEquipo rol) {
    }

    /** El Product Owner va aparte; {@code integrantes} son los demás. */
    public record EquipoVista(UUID equipoId, MiembroVista productOwner, List<MiembroVista> integrantes,
            int proyectosActivos, int maxProyectos) {
    }
}
