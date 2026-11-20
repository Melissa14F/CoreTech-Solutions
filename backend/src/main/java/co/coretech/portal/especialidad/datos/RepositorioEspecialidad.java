package co.coretech.portal.especialidad.datos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.especialidad.dominio.Desarrollador;
import co.coretech.portal.especialidad.dominio.Equipo;
import co.coretech.portal.especialidad.dominio.Especialidad;
import co.coretech.portal.especialidad.dominio.Integrante;
import co.coretech.portal.especialidad.dominio.RolEquipo;

/**
 * Acceso a desarrolladores, equipos e integrantes (HU-25, HU-26). JDBC con casts a los enums de PostgreSQL.
 * Los conteos de proyectos y citas son de solo lectura; el módulo no cambia esas tablas.
 */
@Repository
public class RepositorioEspecialidad {

    private static final String DESARROLLADOR = "SELECT id, nombre, correo, celular, especialidad::text, usuario_id, activo, fecha_ingreso FROM desarrolladores";
    private static final String INTEGRANTE = "SELECT equipo_id, desarrollador_id, rol::text FROM integrantes_equipo";

    private final JdbcTemplate jdbc;

    RepositorioEspecialidad(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Desarrollador desarrollador(ResultSet r, int n) throws SQLException {
        return new Desarrollador(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getString(4),
                Especialidad.valueOf(r.getString(5)), r.getObject(6, UUID.class), r.getBoolean(7), r.getObject(8, LocalDate.class));
    }

    private static Integrante integrante(ResultSet r, int n) throws SQLException {
        return new Integrante(r.getObject(1, UUID.class), r.getObject(2, UUID.class), RolEquipo.valueOf(r.getString(3)));
    }

    private static Equipo equipo(ResultSet r, int n) throws SQLException {
        return new Equipo(r.getObject(1, UUID.class), r.getInt(2), r.getBoolean(3));
    }

    // ------------------------------------------------------------------ Desarrolladores

    public List<Desarrollador> desarrolladores() {
        return jdbc.query(DESARROLLADOR + " ORDER BY created_at DESC, nombre", RepositorioEspecialidad::desarrollador);
    }

    public Optional<Desarrollador> desarrollador(UUID id) {
        return jdbc.query(DESARROLLADOR + " WHERE id = ?", RepositorioEspecialidad::desarrollador, id).stream().findFirst();
    }

    public Optional<Desarrollador> desarrolladorPorCorreo(String correo) {
        return jdbc.query(DESARROLLADOR + " WHERE lower(correo) = lower(?)", RepositorioEspecialidad::desarrollador, correo)
                .stream().findFirst();
    }

    public void insertar(Desarrollador d) {
        jdbc.update("INSERT INTO desarrolladores (id, nombre, correo, celular, especialidad, activo, fecha_ingreso) VALUES (?, ?, ?, ?, ?::especialidad, true, ?)",
                d.id(), d.nombre(), d.correo(), d.celular(), d.especialidad().name(), d.fechaIngreso());
    }

    public void actualizar(UUID id, String nombre, String correo, String celular, Especialidad especialidad) {
        jdbc.update("UPDATE desarrolladores SET nombre = ?, correo = ?, celular = ?, especialidad = ?::especialidad WHERE id = ?",
                nombre, correo, celular, especialidad.name(), id);
    }

    public void asignarCuenta(UUID desarrolladorId, UUID usuarioId) {
        jdbc.update("UPDATE desarrolladores SET usuario_id = ? WHERE id = ?", usuarioId, desarrolladorId);
    }

    public void cambiarActivo(UUID id, boolean activo) {
        jdbc.update("UPDATE desarrolladores SET activo = ? WHERE id = ?", activo, id);
    }

    // ------------------------------------------------------------------ Equipos e integrantes

    public List<Equipo> equipos() {
        return jdbc.query("SELECT id, max_proyectos, activo FROM equipos ORDER BY created_at, id", RepositorioEspecialidad::equipo);
    }

    /** Bloquea la fila del equipo mientras dure la transacción. */
    public Optional<Equipo> equipoBloqueado(UUID id) {
        return jdbc.query("SELECT id, max_proyectos, activo FROM equipos WHERE id = ? FOR UPDATE", RepositorioEspecialidad::equipo, id)
                .stream().findFirst();
    }

    public UUID insertarEquipo(int maxProyectos) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO equipos (id, max_proyectos, activo) VALUES (?, ?, true)", id, maxProyectos);
        return id;
    }

    public void cambiarCupo(UUID equipoId, int maxProyectos) {
        jdbc.update("UPDATE equipos SET max_proyectos = ? WHERE id = ?", maxProyectos, equipoId);
    }

    public List<Integrante> integrantes() {
        return jdbc.query(INTEGRANTE + " ORDER BY desde, id", RepositorioEspecialidad::integrante);
    }

    public Optional<Integrante> integranteDe(UUID desarrolladorId) {
        return jdbc.query(INTEGRANTE + " WHERE desarrollador_id = ?", RepositorioEspecialidad::integrante, desarrolladorId)
                .stream().findFirst();
    }

    public void insertarIntegrante(UUID equipoId, UUID desarrolladorId, RolEquipo rol) {
        jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, ?::rol_equipo)",
                equipoId, desarrolladorId, rol.name());
    }

    public void cambiarRol(UUID desarrolladorId, RolEquipo rol) {
        jdbc.update("UPDATE integrantes_equipo SET rol = ?::rol_equipo WHERE desarrollador_id = ?", rol.name(), desarrolladorId);
    }

    public void quitarIntegrante(UUID desarrolladorId) {
        jdbc.update("DELETE FROM integrantes_equipo WHERE desarrollador_id = ?", desarrolladorId);
    }

    // ------------------------------------------------------------------ Conteos de otros módulos (solo lectura)

    /** Proyectos ACTIVO de cada equipo: ocupan su cupo (RN-05). */
    public Map<UUID, Integer> proyectosActivosPorEquipo() {
        Map<UUID, Integer> conteo = new HashMap<>();
        jdbc.query("SELECT equipo_id, count(*) FROM proyectos WHERE estado = 'ACTIVO' GROUP BY equipo_id",
                fila -> {
                    conteo.put(fila.getObject(1, UUID.class), fila.getInt(2));
                });
        return conteo;
    }

    /** Citas PENDIENTE o CONFIRMADA del equipo: las que quedan sin quién las atienda si su Product Owner se desactiva. */
    public int citasPorAtender(UUID equipoId) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM citas WHERE equipo_id = ? AND estado IN ('PENDIENTE', 'CONFIRMADA')", Integer.class, equipoId);
        return total == null ? 0 : total;
    }
}
