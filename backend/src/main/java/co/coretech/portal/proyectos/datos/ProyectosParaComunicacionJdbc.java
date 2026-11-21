package co.coretech.portal.proyectos.datos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.comunicacion.ProyectosParaComunicacion;

/** El proyecto, su equipo y su fase actual, para los mensajes y las reuniones de seguimiento (HU-14, HU-15, HU-16). */
@Repository
class ProyectosParaComunicacionJdbc implements ProyectosParaComunicacion {

    private static final String BASE = """
            SELECT id, codigo, nombre, cliente_id, equipo_id, estado::text, estado_plan::text FROM proyectos
            """;

    private final JdbcTemplate jdbc;

    ProyectosParaComunicacionJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Proyecto fila(ResultSet r, int n) throws SQLException {
        return new Proyecto(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getObject(4, UUID.class),
                r.getObject(5, UUID.class), r.getString(6), r.getString(7));
    }

    @Override
    public Optional<Proyecto> porCodigo(String codigo) {
        return jdbc.query(BASE + " WHERE codigo = ?", ProyectosParaComunicacionJdbc::fila, codigo).stream().findFirst();
    }

    @Override
    public Optional<Proyecto> porId(UUID proyectoId) {
        return jdbc.query(BASE + " WHERE id = ?", ProyectosParaComunicacionJdbc::fila, proyectoId).stream().findFirst();
    }

    @Override
    public void bloquear(UUID proyectoId) {
        jdbc.query("SELECT id FROM proyectos WHERE id = ? FOR UPDATE", (r, n) -> r.getObject(1, UUID.class), proyectoId);
    }

    @Override
    public Optional<Persona> productOwnerDe(UUID equipoId) {
        return jdbc.query("""
                SELECT d.usuario_id, d.nombre, d.correo
                  FROM equipos e
                  JOIN integrantes_equipo i ON i.equipo_id = e.id AND i.rol = 'PRODUCT_OWNER'
                  JOIN desarrolladores d ON d.id = i.desarrollador_id
                 WHERE e.id = ? AND d.usuario_id IS NOT NULL
                """, (r, n) -> new Persona(r.getObject(1, UUID.class), r.getString(2), r.getString(3)), equipoId)
                .stream().findFirst();
    }

    @Override
    public Optional<UUID> equipoDelProductOwner(UUID usuarioId) {
        return jdbc.query("""
                SELECT e.id
                  FROM equipos e
                  JOIN integrantes_equipo i ON i.equipo_id = e.id AND i.rol = 'PRODUCT_OWNER'
                  JOIN desarrolladores d ON d.id = i.desarrollador_id
                 WHERE d.usuario_id = ? AND e.activo
                """, (r, n) -> r.getObject(1, UUID.class), usuarioId).stream().findFirst();
    }

    @Override
    public Optional<Fase> faseEnCurso(UUID proyectoId) {
        return jdbc.query("""
                SELECT id, orden, nombre FROM proyecto_fases
                 WHERE proyecto_id = ? AND estado NOT IN ('APROBADA', 'QUITADA') ORDER BY orden LIMIT 1
                """, (r, n) -> new Fase(r.getObject(1, UUID.class), r.getInt(2), r.getString(3)), proyectoId).stream().findFirst();
    }

    @Override
    public boolean horarioOcupado(UUID equipoId, Instant inicio, UUID excluirSolicitud) {
        Timestamp t = Timestamp.from(inicio);
        // Las citas y las reuniones duran una hora: se cruzan si empiezan con menos de una hora de diferencia
        Integer cruces = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM citas c
                         WHERE c.equipo_id = ? AND c.estado IN ('PENDIENTE', 'CONFIRMADA')
                           AND abs(extract(epoch FROM (c.fecha_hora - ?::timestamptz))) < 3600)
                     + (SELECT count(*) FROM solicitudes_reunion s JOIN proyectos p ON p.id = s.proyecto_id
                         WHERE p.equipo_id = ? AND s.estado = 'PROGRAMADA' AND s.id IS DISTINCT FROM ?
                           AND abs(extract(epoch FROM (s.fecha_programada - ?::timestamptz))) < 3600)
                """, Integer.class, equipoId, t, equipoId, excluirSolicitud, t);
        return cruces != null && cruces > 0;
    }
}
