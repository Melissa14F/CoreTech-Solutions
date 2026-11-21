package co.coretech.portal.seguimiento.datos;

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
 * Fases, pasos y adjuntos de un proyecto (HU-03, HU-04). Todo con SQL: los estados de PostgreSQL se leen con
 * {@code ::text} y se escriben con su cast. Lo que ya se publicó (un paso HECHO) lo protege el disparador
 * {@code tg_proteger_pasos}: aquí solo se pasa de PENDIENTE o EN_CURSO a HECHO.
 */
@Repository
public class SeguimientoJdbc {

    public record FaseFila(UUID id, int orden, String nombre, String queEs, boolean apruebaCliente, String estado,
            Instant iniciadaAt, Instant entregadaAt, Instant aprobadaAt, int rondasAjusteUsadas, boolean aprobacionAutomatica) {
    }

    public record PasoFila(UUID id, UUID faseId, int orden, String tipo, String titulo, String explicacion, String origen,
            String estado, String nota, Instant hechoAt, Instant creadoAt) {
    }

    public record AdjuntoFila(UUID id, UUID pasoId, String titulo, String archivo, String tipoMime, int tamanoBytes,
            boolean eliminado) {
    }

    /** Un paso con la fase y el proyecto a los que pertenece. */
    public record PasoContexto(UUID pasoId, String tipo, String titulo, String estado, UUID faseId, int faseOrden,
            String faseNombre, String faseEstado, UUID proyectoId) {
    }

    /** Un adjunto con el proyecto al que pertenece, para controlar el acceso a su descarga. */
    public record AdjuntoContexto(AdjuntoFila adjunto, UUID proyectoId) {
    }

    private final JdbcTemplate jdbc;

    SeguimientoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instante(ResultSet r, String columna) throws SQLException {
        Timestamp t = r.getTimestamp(columna);
        return t == null ? null : t.toInstant();
    }

    /** Las fases del plan en orden, sin las que se quitaron. */
    public List<FaseFila> fases(UUID proyectoId) {
        return jdbc.query("""
                SELECT id, orden, nombre, que_es, aprueba_cliente, estado::text, iniciada_at, entregada_at, aprobada_at,
                       rondas_ajuste_usadas, aprobacion_automatica
                  FROM proyecto_fases WHERE proyecto_id = ? AND estado <> 'QUITADA' ORDER BY orden
                """, (r, n) -> new FaseFila(r.getObject("id", UUID.class), r.getInt("orden"), r.getString("nombre"),
                r.getString("que_es"), r.getBoolean("aprueba_cliente"), r.getString("estado"), instante(r, "iniciada_at"),
                instante(r, "entregada_at"), instante(r, "aprobada_at"), r.getInt("rondas_ajuste_usadas"),
                r.getBoolean("aprobacion_automatica")), proyectoId);
    }

    /** Los pasos de todas las fases (sin los quitados), por fase y orden. */
    public List<PasoFila> pasos(UUID proyectoId) {
        return jdbc.query("""
                SELECT s.id, s.proyecto_fase_id, s.orden, s.tipo::text, s.titulo, s.explicacion, s.origen, s.estado::text,
                       s.nota, s.hecho_at, s.created_at
                  FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                 WHERE f.proyecto_id = ? AND f.estado <> 'QUITADA' AND s.estado <> 'QUITADO'
                 ORDER BY f.orden, s.orden
                """, (r, n) -> new PasoFila(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getInt(3), r.getString(4),
                r.getString(5), r.getString(6), r.getString(7), r.getString(8), r.getString(9), instante(r, "hecho_at"),
                instante(r, "created_at")), proyectoId);
    }

    public List<AdjuntoFila> adjuntos(UUID proyectoId) {
        return jdbc.query("""
                SELECT a.id, a.paso_id, a.titulo, a.url, a.tipo_mime, a.tamano_bytes, a.archivo_eliminado_at IS NOT NULL
                  FROM adjuntos_paso a JOIN pasos s ON s.id = a.paso_id JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                 WHERE f.proyecto_id = ? ORDER BY a.created_at, a.titulo
                """, ADJUNTO, proyectoId);
    }

    private static final org.springframework.jdbc.core.RowMapper<AdjuntoFila> ADJUNTO = (r, n) -> new AdjuntoFila(
            r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3), r.getString(4), r.getString(5), r.getInt(6),
            r.getBoolean(7));

    public Optional<PasoContexto> paso(UUID pasoId) {
        return jdbc.query("""
                SELECT s.id, s.tipo::text, s.titulo, s.estado::text, f.id, f.orden, f.nombre, f.estado::text, f.proyecto_id
                  FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id WHERE s.id = ?
                """, (r, n) -> new PasoContexto(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getString(4),
                r.getObject(5, UUID.class), r.getInt(6), r.getString(7), r.getString(8), r.getObject(9, UUID.class)), pasoId)
                .stream().findFirst();
    }

    /**
     * Marca el paso como hecho con su nota, autor y fecha. Devuelve {@code false} si ya no estaba pendiente (otra pestaña
     * se adelantó): la fila no cambia y el servicio responde 409.
     */
    public boolean marcarHecho(UUID pasoId, String nota, UUID autor, Instant cuando) {
        return jdbc.update("""
                UPDATE pasos SET estado = 'HECHO', nota = ?, hecho_at = ?, registrado_por = ?
                 WHERE id = ? AND estado IN ('PENDIENTE', 'EN_CURSO')
                """, nota, Timestamp.from(cuando), autor, pasoId) == 1;
    }

    /** Pasos hechos y pasos en total de una fase, sin contar los quitados. */
    public int[] conteo(UUID faseId) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE estado = 'HECHO'), count(*) FILTER (WHERE estado <> 'QUITADO')
                  FROM pasos WHERE proyecto_fase_id = ?
                """, (r, n) -> new int[] { r.getInt(1), r.getInt(2) }, faseId);
    }

    public int contarAdjuntos(UUID pasoId) {
        return jdbc.queryForObject("SELECT count(*) FROM adjuntos_paso WHERE paso_id = ?", Integer.class, pasoId);
    }

    /**
     * Guarda el adjunto. {@code created_at} usa {@code clock_timestamp()} y no el {@code now()} de la columna: así los
     * archivos de una misma subida conservan el orden en que se enviaron (now() es igual en toda la transacción).
     * {@code archivo} es el nombre que le puso el almacén; la columna se llama {@code url} por el esquema.
     */
    public UUID insertarAdjunto(UUID pasoId, String titulo, String archivo, String tipoMime, int tamanoBytes) {
        return jdbc.queryForObject("""
                INSERT INTO adjuntos_paso (paso_id, titulo, url, tipo_mime, tamano_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, clock_timestamp()) RETURNING id
                """, UUID.class, pasoId, titulo, archivo, tipoMime, tamanoBytes);
    }

    public Optional<AdjuntoContexto> adjunto(UUID adjuntoId) {
        return jdbc.query("""
                SELECT a.id, a.paso_id, a.titulo, a.url, a.tipo_mime, a.tamano_bytes, a.archivo_eliminado_at IS NOT NULL, f.proyecto_id
                  FROM adjuntos_paso a JOIN pasos s ON s.id = a.paso_id JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                 WHERE a.id = ?
                """, (r, n) -> new AdjuntoContexto(ADJUNTO.mapRow(r, n), r.getObject(8, UUID.class)), adjuntoId)
                .stream().findFirst();
    }
}
