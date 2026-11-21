package co.coretech.portal.comunicacion.datos;

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
 * Los mensajes entre el cliente y el desarrollador (Product Owner) de un proyecto (HU-14). Los mensajes no se borran y el
 * autor los edita solo durante 5 minutos: lo garantiza también la base con {@code tg_proteger_mensajes}, que además llena
 * {@code editado_at}; aquí solo se escribe el tema y el contenido.
 */
@Repository
public class MensajesJdbc {

    /** Un mensaje del hilo con el nombre y el rol de quien lo escribió. */
    public record MensajeFila(UUID id, UUID autorId, String autor, String rol, String tema, String contenido, Instant creadoAt,
            Instant editadoAt) {
    }

    /** Lo que hace falta saber de un mensaje para decidir si se edita. */
    public record MensajeContexto(UUID id, UUID proyectoId, UUID autorId, String tema, String contenido, Instant creadoAt) {
    }

    private final JdbcTemplate jdbc;

    MensajesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instante(ResultSet r, int columna) throws SQLException {
        Timestamp t = r.getTimestamp(columna);
        return t == null ? null : t.toInstant();
    }

    /** El hilo completo del proyecto, del más antiguo al más reciente. */
    public List<MensajeFila> listar(UUID proyectoId) {
        return jdbc.query("""
                SELECT m.id, m.autor_id, u.nombre, ro.codigo, m.tema, m.contenido, m.created_at, m.editado_at
                  FROM mensajes m
                  JOIN usuarios u ON u.id = m.autor_id
                  JOIN roles ro ON ro.id = u.rol_id
                 WHERE m.proyecto_id = ?
                 ORDER BY m.created_at, m.id
                """, (r, n) -> new MensajeFila(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3), r.getString(4),
                r.getString(5), r.getString(6), instante(r, 7), instante(r, 8)), proyectoId);
    }

    public Optional<MensajeContexto> mensaje(UUID id) {
        return jdbc.query("SELECT id, proyecto_id, autor_id, tema, contenido, created_at FROM mensajes WHERE id = ?",
                (r, n) -> new MensajeContexto(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getObject(3, UUID.class),
                        r.getString(4), r.getString(5), instante(r, 6)), id).stream().findFirst();
    }

    public UUID insertar(UUID proyectoId, UUID autorId, String tema, String contenido, Instant ahora) {
        return jdbc.queryForObject("""
                INSERT INTO mensajes (proyecto_id, autor_id, tema, contenido, created_at) VALUES (?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, proyectoId, autorId, tema, contenido, Timestamp.from(ahora));
    }

    /** Cambia el tema y el contenido; la base marca {@code editado_at} y rechaza el cambio pasados los 5 minutos. */
    public void editar(UUID id, String tema, String contenido) {
        jdbc.update("UPDATE mensajes SET tema = ?, contenido = ? WHERE id = ?", tema, contenido, id);
    }
}
