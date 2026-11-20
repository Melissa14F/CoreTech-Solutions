package co.coretech.portal.acceso.datos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Matriz de permisos (tablas permisos, rol_permisos y rol_permisos_vetados; HU-21). Lectura y cambios de
 * una casilla; las reglas (RN-19) las revisa el servicio y, como última barrera, tg_proteger_rol_permisos.
 */
@Repository
public class PermisosJdbc {

    private final JdbcTemplate jdbc;

    PermisosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Permiso(short id, String codigo, String modulo, String nombre, String ayuda, String regla) {
    }

    /** Permiso que tiene un rol; {@code fijo} = bloqueado por una regla del negocio. */
    public record Asignacion(short rolId, short permisoId, boolean fijo) {
    }

    /** Permiso que una regla del negocio prohíbe a un rol. */
    public record Veto(short rolId, short permisoId, String motivo) {
    }

    public List<Permiso> permisos() {
        return jdbc.query("SELECT id, codigo, modulo, nombre, ayuda, regla FROM permisos ORDER BY orden",
                (f, n) -> new Permiso(f.getShort(1), f.getString(2), f.getString(3), f.getString(4), f.getString(5),
                        f.getString(6)));
    }

    public List<Asignacion> asignaciones() {
        return jdbc.query("SELECT rol_id, permiso_id, bloqueado FROM rol_permisos",
                (f, n) -> new Asignacion(f.getShort(1), f.getShort(2), f.getBoolean(3)));
    }

    public List<Veto> vetos() {
        return jdbc.query("SELECT rol_id, permiso_id, motivo FROM rol_permisos_vetados",
                (f, n) -> new Veto(f.getShort(1), f.getShort(2), f.getString(3)));
    }

    /** Cuentas activas por rol (el resumen de cada rol). */
    public Map<Short, Integer> usuariosActivosPorRol() {
        Map<Short, Integer> conteo = new HashMap<>();
        jdbc.query("SELECT rol_id, count(*) FROM usuarios WHERE activo GROUP BY rol_id",
                f -> {
                    conteo.put(f.getShort(1), f.getInt(2));
                });
        return conteo;
    }

    public void dar(short rolId, short permisoId) {
        jdbc.update("INSERT INTO rol_permisos (rol_id, permiso_id, bloqueado) VALUES (?, ?, false)", rolId, permisoId);
    }

    public void quitar(short rolId, short permisoId) {
        jdbc.update("DELETE FROM rol_permisos WHERE rol_id = ? AND permiso_id = ?", rolId, permisoId);
    }

    /** Bloquea la matriz mientras se guarda un cambio, para que dos administradores no se pisen. */
    public void bloquearMatriz() {
        jdbc.execute("LOCK TABLE rol_permisos IN SHARE ROW EXCLUSIVE MODE");
    }
}
