package co.coretech.portal.comunicacion.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Las solicitudes de reunión de seguimiento (HU-15, HU-16) y el resumen que se escribe al atenderlas (D-42). Una sola
 * solicitud PENDIENTE por proyecto (RN-15) la garantiza el índice {@code ux_solicitud_abierta}; el resumen es inmutable.
 */
@Repository
public class SolicitudesReunionJdbc {

    /** El resumen de una reunión atendida. */
    public record ResumenFila(String resumen, String quePidio, String siguientePaso, Instant registradoAt) {
    }

    /** Una solicitud con los datos del proyecto, del cliente y del equipo que la muestran las pantallas. */
    public record SolicitudFila(UUID id, String codigo, UUID proyectoId, String proyectoCodigo, String proyectoNombre,
            UUID clienteId, String cliente, UUID equipoId, String responsable, UUID faseId, String fase, String motivo,
            String razon, String modalidad, Instant opcion1, Instant opcion2, String estado, Instant fechaProgramada,
            String enlace, String motivoRechazo, Instant enviadaAt, ResumenFila resumen) {
    }

    private static final String BASE = """
            SELECT s.id, s.codigo, p.id, p.codigo, p.nombre, s.cliente_id, u.nombre, p.equipo_id,
                   (SELECT d.nombre FROM integrantes_equipo i JOIN desarrolladores d ON d.id = i.desarrollador_id
                     WHERE i.equipo_id = p.equipo_id AND i.rol = 'PRODUCT_OWNER' LIMIT 1),
                   f.id, f.nombre, s.motivo, s.razon, s.modalidad::text, s.opcion_1, s.opcion_2, s.estado::text,
                   s.fecha_programada, s.enlace_reunion, s.motivo_rechazo, s.created_at,
                   r.resumen, r.que_pidio, r.siguiente_paso, r.created_at
              FROM solicitudes_reunion s
              JOIN proyectos p ON p.id = s.proyecto_id
              JOIN usuarios u ON u.id = s.cliente_id
              JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
              LEFT JOIN resumenes_reunion r ON r.solicitud_id = s.id
            """;

    private final JdbcTemplate jdbc;

    SolicitudesReunionJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instante(ResultSet r, int columna) throws SQLException {
        Timestamp t = r.getTimestamp(columna);
        return t == null ? null : t.toInstant();
    }

    private static SolicitudFila fila(ResultSet r, int n) throws SQLException {
        ResumenFila resumen = r.getString(22) == null ? null : new ResumenFila(r.getString(22), r.getString(23), r.getString(24), instante(r, 25));
        return new SolicitudFila(r.getObject(1, UUID.class), r.getString(2), r.getObject(3, UUID.class), r.getString(4), r.getString(5),
                r.getObject(6, UUID.class), r.getString(7), r.getObject(8, UUID.class), r.getString(9), r.getObject(10, UUID.class),
                r.getString(11), r.getString(12), r.getString(13), r.getString(14), instante(r, 15), instante(r, 16), r.getString(17),
                instante(r, 18), r.getString(19), r.getString(20), instante(r, 21), resumen);
    }

    public Optional<SolicitudFila> porId(UUID id) {
        return jdbc.query(BASE + " WHERE s.id = ?", SolicitudesReunionJdbc::fila, id).stream().findFirst();
    }

    /** Las solicitudes de un proyecto, la más reciente primero. */
    public List<SolicitudFila> deProyecto(UUID proyectoId) {
        return jdbc.query(BASE + " WHERE s.proyecto_id = ? ORDER BY s.created_at DESC, s.codigo DESC",
                SolicitudesReunionJdbc::fila, proyectoId);
    }

    /** Todas las solicitudes (administrador) o las de los proyectos de un equipo, la más reciente primero. */
    public List<SolicitudFila> listar(UUID equipoId) {
        if (equipoId == null) {
            return jdbc.query(BASE + " ORDER BY s.created_at DESC, s.codigo DESC", SolicitudesReunionJdbc::fila);
        }
        return jdbc.query(BASE + " WHERE p.equipo_id = ? ORDER BY s.created_at DESC, s.codigo DESC", SolicitudesReunionJdbc::fila,
                equipoId);
    }

    public UUID insertar(UUID proyectoId, UUID clienteId, UUID faseId, String motivo, String razon, String modalidad,
            Instant opcion1, Instant opcion2, Instant ahora) {
        return jdbc.queryForObject("""
                INSERT INTO solicitudes_reunion (codigo, proyecto_id, cliente_id, proyecto_fase_id, motivo, razon, modalidad,
                                                 opcion_1, opcion_2, created_at)
                VALUES (fn_siguiente_codigo_solicitud(), ?, ?, ?, ?, ?, ?::modalidad, ?, ?, ?) RETURNING id
                """, UUID.class, proyectoId, clienteId, faseId, motivo, razon, modalidad, Timestamp.from(opcion1),
                opcion2 == null ? null : Timestamp.from(opcion2), Timestamp.from(ahora));
    }

    /** PENDIENTE → PROGRAMADA. Falso si otra persona la cambió antes (el UPDATE condicionado decide la carrera). */
    public boolean programar(UUID id, Instant fecha, String enlace, UUID gestionadaPor) {
        return jdbc.update("""
                UPDATE solicitudes_reunion SET estado = 'PROGRAMADA'::estado_solicitud, fecha_programada = ?, enlace_reunion = ?,
                       gestionada_por = ?
                 WHERE id = ? AND estado = 'PENDIENTE'
                """, Timestamp.from(fecha), enlace, gestionadaPor, id) == 1;
    }

    /** PENDIENTE → RECHAZADA con su motivo. */
    public boolean rechazar(UUID id, String motivo, UUID gestionadaPor) {
        return jdbc.update("""
                UPDATE solicitudes_reunion SET estado = 'RECHAZADA'::estado_solicitud, motivo_rechazo = ?, gestionada_por = ?
                 WHERE id = ? AND estado = 'PENDIENTE'
                """, motivo, gestionadaPor, id) == 1;
    }

    /** PROGRAMADA → CANCELADA con su motivo (lo cancela el administrador). */
    public boolean cancelar(UUID id, String motivo, UUID gestionadaPor) {
        return jdbc.update("""
                UPDATE solicitudes_reunion SET estado = 'CANCELADA'::estado_solicitud, motivo_rechazo = ?, gestionada_por = ?
                 WHERE id = ? AND estado = 'PROGRAMADA'
                """, motivo, gestionadaPor, id) == 1;
    }

    /** PROGRAMADA → ATENDIDA; falso si ya no estaba programada (otra pestaña la cerró antes). */
    public boolean marcarAtendida(UUID id) {
        return jdbc.update("""
                UPDATE solicitudes_reunion SET estado = 'ATENDIDA'::estado_solicitud WHERE id = ? AND estado = 'PROGRAMADA'
                """, id) == 1;
    }

    public void insertarResumen(UUID solicitudId, String resumen, String quePidio, String siguientePaso, UUID registradoPor,
            Instant ahora) {
        jdbc.update("""
                INSERT INTO resumenes_reunion (solicitud_id, resumen, que_pidio, siguiente_paso, registrado_por, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, solicitudId, resumen, quePidio, siguientePaso, registradoPor, Timestamp.from(ahora));
    }
}
