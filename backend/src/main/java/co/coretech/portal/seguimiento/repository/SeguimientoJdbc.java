package co.coretech.portal.seguimiento.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
            Instant iniciadaAt, Instant entregadaAt, Instant aprobadaAt, int rondasAjusteUsadas, boolean aprobacionAutomatica,
            LocalDate plazoAprobacion) {
    }

    /** Una fase con el proyecto al que pertenece. */
    public record FaseDeProyecto(FaseFila fase, UUID proyectoId) {
    }

    /** Una fase que espera la respuesta del cliente, para la tarea diaria (RN-17). */
    public record FaseEnRevision(UUID faseId, UUID proyectoId, int orden, String nombre, Instant entregadaAt,
            LocalDate plazoAprobacion, boolean recordada) {
    }

    /** Un cambio que pidió el cliente al revisar una fase: un ajuste o algo nuevo (HU-11). */
    public record CambioPedido(UUID faseId, String tipo, String descripcion, String estado, Instant creadoAt) {
    }

    /** Un adjunto cuyo archivo hay que borrar del almacén (RNF-09). */
    public record AdjuntoPorBorrar(UUID id, String archivo) {
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

    private static final String FASES = """
            SELECT id, orden, nombre, que_es, aprueba_cliente, estado::text, iniciada_at, entregada_at, aprobada_at,
                   rondas_ajuste_usadas, aprobacion_automatica, plazo_aprobacion
              FROM proyecto_fases
            """;

    private static FaseFila fase(ResultSet r, int n) throws SQLException {
        return new FaseFila(r.getObject("id", UUID.class), r.getInt("orden"), r.getString("nombre"), r.getString("que_es"),
                r.getBoolean("aprueba_cliente"), r.getString("estado"), instante(r, "iniciada_at"), instante(r, "entregada_at"),
                instante(r, "aprobada_at"), r.getInt("rondas_ajuste_usadas"), r.getBoolean("aprobacion_automatica"),
                r.getObject("plazo_aprobacion", LocalDate.class));
    }

    /** Las fases del plan en orden, sin las que se quitaron. */
    public List<FaseFila> fases(UUID proyectoId) {
        return jdbc.query(FASES + " WHERE proyecto_id = ? AND estado <> 'QUITADA' ORDER BY orden", SeguimientoJdbc::fase, proyectoId);
    }

    public Optional<FaseDeProyecto> faseConProyecto(UUID faseId) {
        return jdbc.query(FASES.replace("SELECT id,", "SELECT id, proyecto_id,") + " WHERE id = ?",
                (r, n) -> new FaseDeProyecto(fase(r, n), r.getObject("proyecto_id", UUID.class)), faseId).stream().findFirst();
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

    // ------------------------------------------------------------------------------------------- Estados de la fase (HU-11, HU-12, HU-13)

    /** EN_CURSO → EN_REVISION: el cliente tiene hasta {@code plazo} para responder. {@code false} si la fase ya no estaba en curso. */
    public boolean entregarParaRevision(UUID faseId, Instant cuando, LocalDate plazo) {
        return jdbc.update("""
                UPDATE proyecto_fases SET estado = 'EN_REVISION'::estado_fase, entregada_at = ?, plazo_aprobacion = ?, plazo_recordado_at = NULL
                 WHERE id = ? AND estado = 'EN_CURSO'
                """, Timestamp.from(cuando), plazo, faseId) == 1;
    }

    /**
     * Aprueba la fase: el cliente (o nadie, si fue automática) cuando estaba EN_REVISION, o el desarrollador (Product Owner) al
     * cerrar una fase que no aprueba el cliente, que estaba EN_CURSO. {@code false} si ya no estaba en uno de esos estados.
     */
    public boolean aprobar(UUID faseId, Instant cuando, UUID aprobadaPor, boolean automatica) {
        return jdbc.update("""
                UPDATE proyecto_fases SET estado = 'APROBADA'::estado_fase, aprobada_at = ?, aprobada_por = ?, aprobacion_automatica = ?,
                       entregada_at = coalesce(entregada_at, ?), plazo_aprobacion = NULL
                 WHERE id = ? AND estado IN ('EN_CURSO', 'EN_REVISION')
                """, Timestamp.from(cuando), aprobadaPor, automatica, Timestamp.from(cuando), faseId) == 1;
    }

    /** La primera fase que todavía no empieza, en orden (RN-08: la siguiente empieza cuando la anterior se aprueba). */
    public Optional<FaseFila> siguientePendiente(UUID proyectoId) {
        return jdbc.query(FASES + " WHERE proyecto_id = ? AND estado = 'PENDIENTE' ORDER BY orden LIMIT 1", SeguimientoJdbc::fase,
                proyectoId).stream().findFirst();
    }

    public void iniciar(UUID faseId, Instant cuando) {
        jdbc.update("UPDATE proyecto_fases SET estado = 'EN_CURSO'::estado_fase, iniciada_at = ? WHERE id = ? AND estado = 'PENDIENTE'",
                Timestamp.from(cuando), faseId);
    }

    /** Un ajuste usa una ronda: EN_REVISION → EN_CURSO. {@code false} si ya no estaba en revisión. */
    public boolean devolverPorAjuste(UUID faseId) {
        return jdbc.update("""
                UPDATE proyecto_fases SET estado = 'EN_CURSO'::estado_fase, rondas_ajuste_usadas = rondas_ajuste_usadas + 1,
                       plazo_aprobacion = NULL, plazo_recordado_at = NULL
                 WHERE id = ? AND estado = 'EN_REVISION'
                """, faseId) == 1;
    }

    /** Una fase aprobada con una corrección abierta deja de sumar (RN-16). */
    public void ponerEnCorreccion(UUID faseId) {
        jdbc.update("UPDATE proyecto_fases SET estado = 'EN_CORRECCION'::estado_fase WHERE id = ? AND estado = 'APROBADA'", faseId);
    }

    /** Terminada la última corrección, la fase vuelve a sumar su porcentaje (RN-16). */
    public void volverAAprobada(UUID faseId) {
        jdbc.update("UPDATE proyecto_fases SET estado = 'APROBADA'::estado_fase WHERE id = ? AND estado = 'EN_CORRECCION'", faseId);
    }

    public int fasesSinAprobar(UUID proyectoId) {
        return jdbc.queryForObject("SELECT count(*) FROM proyecto_fases WHERE proyecto_id = ? AND estado NOT IN ('APROBADA', 'QUITADA')",
                Integer.class, proyectoId);
    }

    // ------------------------------------------------------------------------------------------- Pasos y correcciones

    /** {@code [correcciones abiertas, pasos por hacer]} de la fase; los quitados no cuentan (RN-16, HU-13). */
    public int[] pendientes(UUID faseId) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE tipo = 'CORRECCION' AND estado <> 'HECHO' AND estado <> 'QUITADO'),
                       count(*) FILTER (WHERE estado <> 'HECHO' AND estado <> 'QUITADO')
                  FROM pasos WHERE proyecto_fase_id = ?
                """, (r, n) -> new int[] { r.getInt(1), r.getInt(2) }, faseId);
    }

    public int correccionesAbiertasDelProyecto(UUID proyectoId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                 WHERE f.proyecto_id = ? AND s.tipo = 'CORRECCION' AND s.estado <> 'HECHO' AND s.estado <> 'QUITADO'
                """, Integer.class, proyectoId);
    }

    /** Agrega un paso de corrección, ajuste o alcance al final de la fase. */
    public UUID insertarPaso(UUID faseId, String tipo, String titulo, String explicacion, String origen) {
        return jdbc.queryForObject("""
                INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion, origen)
                VALUES (?, (SELECT coalesce(max(orden), 0) + 1 FROM pasos WHERE proyecto_fase_id = ?), ?::tipo_paso, ?, ?, ?)
                RETURNING id
                """, UUID.class, faseId, faseId, tipo, titulo, explicacion, origen);
    }

    // ------------------------------------------------------------------------------------------- Cambios que pide el cliente

    public UUID insertarSolicitud(UUID faseId, UUID clienteId, String tipo, String descripcion, String estado, UUID pasoId) {
        return jdbc.queryForObject("""
                INSERT INTO solicitudes_cambio (proyecto_fase_id, cliente_id, tipo, descripcion, estado, paso_id)
                VALUES (?, ?, ?::tipo_paso, ?, ?::estado_cambio, ?) RETURNING id
                """, UUID.class, faseId, clienteId, tipo, descripcion, estado, pasoId);
    }

    /** Terminado el paso de un ajuste, su solicitud queda resuelta. */
    public void resolverSolicitudDelPaso(UUID pasoId) {
        jdbc.update("UPDATE solicitudes_cambio SET estado = 'RESUELTO'::estado_cambio WHERE paso_id = ? AND estado IN ('ABIERTO', 'ACEPTADO')", pasoId);
    }

    public List<CambioPedido> cambiosPedidos(UUID proyectoId) {
        return jdbc.query("""
                SELECT c.proyecto_fase_id, c.tipo::text, c.descripcion, c.estado::text, c.created_at
                  FROM solicitudes_cambio c JOIN proyecto_fases f ON f.id = c.proyecto_fase_id
                 WHERE f.proyecto_id = ? ORDER BY c.created_at
                """, (r, n) -> new CambioPedido(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getString(4),
                instante(r, "created_at")), proyectoId);
    }

    // ------------------------------------------------------------------------------------------- Tareas programadas

    public List<FaseEnRevision> fasesEnRevision() {
        return jdbc.query("""
                SELECT id, proyecto_id, orden, nombre, entregada_at, plazo_aprobacion, plazo_recordado_at IS NOT NULL
                  FROM proyecto_fases WHERE estado = 'EN_REVISION' AND plazo_aprobacion IS NOT NULL ORDER BY entregada_at
                """, (r, n) -> new FaseEnRevision(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getInt(3), r.getString(4),
                instante(r, "entregada_at"), r.getObject(6, LocalDate.class), r.getBoolean(7)));
    }

    public void marcarRecordada(UUID faseId) {
        jdbc.update("UPDATE proyecto_fases SET plazo_recordado_at = now() WHERE id = ?", faseId);
    }

    /** Adjuntos de esos proyectos cuyo archivo todavía no se borró. */
    public List<AdjuntoPorBorrar> adjuntosPorBorrar(List<UUID> proyectos) {
        if (proyectos.isEmpty()) {
            return List.of();
        }
        String marcas = String.join(",", java.util.Collections.nCopies(proyectos.size(), "?"));
        return jdbc.query("""
                SELECT a.id, a.url FROM adjuntos_paso a JOIN pasos s ON s.id = a.paso_id JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                 WHERE a.archivo_eliminado_at IS NULL AND f.proyecto_id IN (""" + marcas + ")",
                (r, n) -> new AdjuntoPorBorrar(r.getObject(1, UUID.class), r.getString(2)), proyectos.toArray());
    }

    /** El archivo ya no está en el almacén; el registro se conserva (RNF-09). */
    public void marcarArchivoEliminado(UUID adjuntoId) {
        jdbc.update("UPDATE adjuntos_paso SET archivo_eliminado_at = now() WHERE id = ?", adjuntoId);
    }
}
