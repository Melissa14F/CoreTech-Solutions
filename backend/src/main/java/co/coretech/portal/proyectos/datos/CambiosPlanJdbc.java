package co.coretech.portal.proyectos.datos;

import java.math.BigDecimal;
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

import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.PasoPlantilla;

/**
 * Cambios de un plan aprobado (HU-30, RN-30, D-32): agregar o quitar una fase pendiente, cambiar la fecha, quitar un paso
 * pendiente o cotizar un cambio de alcance. El recorrido de estados (PROPUESTO → AUTORIZADO → ACEPTADO, o rechazado) lo
 * valida también la base con {@code tg_validar_estado_cambio_plan}; aquí cada cambio de estado es un {@code UPDATE}
 * condicionado, así que si dos personas responden a la vez solo una gana.
 */
@Repository
public class CambiosPlanJdbc {

    /** Un cambio con lo que las pantallas necesitan del proyecto, de quien lo propuso y de lo que cambia. */
    public record CambioFila(UUID id, UUID proyectoId, String proyectoCodigo, String proyecto, UUID clienteId, String cliente,
            UUID equipoId, LocalDate entregaActual, String tipo, String nombreFase, Integer posicion, boolean apruebaCliente,
            String pasosPropuestos, UUID faseId, String faseNombre, String motivo, UUID pasoId, String pasoTitulo, String pasoFase,
            boolean pasoFaseApruebaCliente, LocalDate nuevaEntrega, BigDecimal valorAdicional, String estado, UUID propuestoPor,
            String propuestoPorNombre, Instant creadoAt, Instant autorizadoAt, Instant respondidoAt, String motivoRechazo,
            UUID solicitudId, String tituloPaso, String explicacionPaso, String solicitudDescripcion) {
    }

    /** Una fase del plan (sin las quitadas) para armar el antes y el después y validar posiciones. */
    public record FaseDelPlan(UUID id, int orden, String nombre, String estado, boolean apruebaCliente) {
    }

    /** Una solicitud de «algo nuevo» que el cliente pidió y que falta cotizar (HU-11). */
    public record AlcancePorCotizar(UUID id, String fase, String descripcion, Instant creadaAt) {
    }

    /** Un paso del plan con el estado de su fase, para decidir si se puede quitar. */
    public record PasoDelPlan(UUID id, UUID proyectoId, UUID faseId, String faseNombre, String faseEstado, boolean faseApruebaCliente,
            String tipo, String estado, String titulo, int pasosQueQuedan) {
    }

    private static final String BASE = """
            SELECT c.id, p.id AS proyecto_id, p.codigo, p.nombre, p.cliente_id, uc.nombre AS cliente, p.equipo_id,
                   p.fecha_estimada_entrega, c.tipo::text AS tipo, c.nombre_fase, c.posicion, c.aprueba_cliente,
                   c.pasos_propuestos::text AS pasos, f.id AS fase_id, f.nombre AS fase_nombre, c.motivo, s.id AS paso_id,
                   s.titulo AS paso_titulo, sf.nombre AS paso_fase, coalesce(sf.aprueba_cliente, false) AS paso_fase_aprueba,
                   c.nueva_fecha_entrega, c.valor_adicional, c.estado::text AS estado, c.propuesto_por, up.nombre AS propuesto_nombre,
                   c.created_at, c.autorizado_at, c.respondido_at, c.motivo_rechazo, c.solicitud_cambio_id, c.titulo_paso,
                   c.explicacion_paso, sc.descripcion AS solicitud_descripcion
              FROM cambios_plan c
              JOIN proyectos p ON p.id = c.proyecto_id
              JOIN usuarios uc ON uc.id = p.cliente_id
              JOIN usuarios up ON up.id = c.propuesto_por
              LEFT JOIN proyecto_fases f ON f.id = c.proyecto_fase_id
              LEFT JOIN pasos s ON s.id = c.paso_id
              LEFT JOIN proyecto_fases sf ON sf.id = s.proyecto_fase_id
              LEFT JOIN solicitudes_cambio sc ON sc.id = c.solicitud_cambio_id
            """;

    private final JdbcTemplate jdbc;

    CambiosPlanJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instante(ResultSet r, String columna) throws SQLException {
        Timestamp t = r.getTimestamp(columna);
        return t == null ? null : t.toInstant();
    }

    private static CambioFila fila(ResultSet r, int n) throws SQLException {
        int posicion = r.getInt("posicion");
        Integer pos = r.wasNull() ? null : posicion;
        return new CambioFila(r.getObject("id", UUID.class), r.getObject("proyecto_id", UUID.class), r.getString("codigo"),
                r.getString("nombre"), r.getObject("cliente_id", UUID.class), r.getString("cliente"), r.getObject("equipo_id", UUID.class),
                r.getObject("fecha_estimada_entrega", LocalDate.class), r.getString("tipo"), r.getString("nombre_fase"), pos,
                r.getBoolean("aprueba_cliente"), r.getString("pasos"), r.getObject("fase_id", UUID.class), r.getString("fase_nombre"),
                r.getString("motivo"), r.getObject("paso_id", UUID.class), r.getString("paso_titulo"), r.getString("paso_fase"),
                r.getBoolean("paso_fase_aprueba"), r.getObject("nueva_fecha_entrega", LocalDate.class), r.getBigDecimal("valor_adicional"),
                r.getString("estado"), r.getObject("propuesto_por", UUID.class), r.getString("propuesto_nombre"), instante(r, "created_at"),
                instante(r, "autorizado_at"), instante(r, "respondido_at"), r.getString("motivo_rechazo"),
                r.getObject("solicitud_cambio_id", UUID.class), r.getString("titulo_paso"), r.getString("explicacion_paso"),
                r.getString("solicitud_descripcion"));
    }

    // ------------------------------------------------------------------------------------------- Consultas

    public Optional<CambioFila> porId(UUID id) {
        return jdbc.query(BASE + " WHERE c.id = ?", CambiosPlanJdbc::fila, id).stream().findFirst();
    }

    /** Los cambios de un proyecto, el más reciente primero. */
    public List<CambioFila> deProyecto(UUID proyectoId) {
        return jdbc.query(BASE + " WHERE c.proyecto_id = ? ORDER BY c.created_at DESC, c.id", CambiosPlanJdbc::fila, proyectoId);
    }

    /** Los cambios en un estado (por ejemplo los PROPUESTOS que el administrador debe autorizar), el más antiguo primero. */
    public List<CambioFila> porEstado(String estado) {
        return jdbc.query(BASE + " WHERE c.estado = ?::estado_cambio_plan ORDER BY c.created_at, c.id", CambiosPlanJdbc::fila, estado);
    }

    public List<FaseDelPlan> fases(UUID proyectoId) {
        return jdbc.query("""
                SELECT id, orden, nombre, estado::text, aprueba_cliente FROM proyecto_fases
                 WHERE proyecto_id = ? AND estado <> 'QUITADA' ORDER BY orden
                """, (r, n) -> new FaseDelPlan(r.getObject(1, UUID.class), r.getInt(2), r.getString(3), r.getString(4), r.getBoolean(5)),
                proyectoId);
    }

    /** La fase en curso: la primera que aún no está aprobada ni quitada. */
    public Optional<FaseDelPlan> faseActual(UUID proyectoId) {
        return fases(proyectoId).stream().filter(f -> !f.estado().equals("APROBADA")).findFirst();
    }

    public Optional<PasoDelPlan> paso(UUID pasoId) {
        return jdbc.query("""
                SELECT s.id, f.proyecto_id, f.id, f.nombre, f.estado::text, f.aprueba_cliente, s.tipo::text, s.estado::text, s.titulo,
                       (SELECT count(*) FROM pasos x WHERE x.proyecto_fase_id = f.id AND x.estado <> 'QUITADO' AND x.id <> s.id)
                  FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id WHERE s.id = ?
                """, (r, n) -> new PasoDelPlan(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getObject(3, UUID.class),
                r.getString(4), r.getString(5), r.getBoolean(6), r.getString(7), r.getString(8), r.getString(9), r.getInt(10)), pasoId)
                .stream().findFirst();
    }

    public List<AlcancePorCotizar> alcancesPorCotizar(UUID proyectoId) {
        return jdbc.query("""
                SELECT s.id, f.nombre, s.descripcion, s.created_at
                  FROM solicitudes_cambio s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                 WHERE f.proyecto_id = ? AND s.tipo = 'ALCANCE' AND s.estado = 'POR_COTIZAR' ORDER BY s.created_at
                """, (r, n) -> new AlcancePorCotizar(r.getObject(1, UUID.class), r.getString(2), r.getString(3), instante(r, "created_at")),
                proyectoId);
    }

    /** El valor adicional ya aceptado: la cotización inicial más esto es el valor total del proyecto. */
    public BigDecimal valorAdicionalAceptado(UUID proyectoId) {
        return jdbc.queryForObject("SELECT coalesce(sum(valor_adicional), 0) FROM cambios_plan WHERE proyecto_id = ? AND estado = 'ACEPTADO'",
                BigDecimal.class, proyectoId);
    }

    public BigDecimal valorCotizado(UUID proyectoId) {
        return jdbc.queryForObject("SELECT coalesce(valor_cotizado, 0) FROM proyectos WHERE id = ?", BigDecimal.class, proyectoId);
    }

    public BigDecimal avance(UUID proyectoId) {
        return jdbc.queryForObject("SELECT avance_porcentaje FROM proyectos WHERE id = ?", BigDecimal.class, proyectoId);
    }

    // ------------------------------------------------------------------------------------------- Proponer

    public UUID insertar(UUID proyectoId, String tipo, String nombreFase, Integer posicion, boolean apruebaCliente, String pasosJson,
            UUID faseId, UUID pasoId, UUID solicitudId, String tituloPaso, String explicacionPaso, String motivo, LocalDate nuevaEntrega,
            BigDecimal valorAdicional, UUID propuestoPor, Instant ahora) {
        return jdbc.queryForObject("""
                INSERT INTO cambios_plan (proyecto_id, tipo, nombre_fase, posicion, aprueba_cliente, pasos_propuestos, proyecto_fase_id,
                                          paso_id, solicitud_cambio_id, titulo_paso, explicacion_paso, motivo, nueva_fecha_entrega,
                                          valor_adicional, propuesto_por, created_at)
                VALUES (?, ?::tipo_cambio_plan, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, proyectoId, tipo, nombreFase, posicion, apruebaCliente, pasosJson, faseId, pasoId, solicitudId, tituloPaso,
                explicacionPaso, motivo, nuevaEntrega, valorAdicional, propuestoPor, Timestamp.from(ahora));
    }

    // ------------------------------------------------------------------------------------------- Responder

    /** PROPUESTO → AUTORIZADO, o → ACEPTADO si el cambio se aplica al autorizarlo (QUITAR_PASO en una fase del equipo). */
    public boolean autorizar(UUID id, String nuevoEstado, UUID administradorId, Instant ahora) {
        return jdbc.update("""
                UPDATE cambios_plan SET estado = ?::estado_cambio_plan, autorizado_por = ?, autorizado_at = ?
                 WHERE id = ? AND estado = 'PROPUESTO'
                """, nuevoEstado, administradorId, Timestamp.from(ahora), id) == 1;
    }

    public boolean rechazarComoAdministrador(UUID id, String motivo) {
        return jdbc.update("""
                UPDATE cambios_plan SET estado = 'RECHAZADO_ADMIN'::estado_cambio_plan, motivo_rechazo = ?
                 WHERE id = ? AND estado = 'PROPUESTO'
                """, motivo, id) == 1;
    }

    public boolean aceptar(UUID id, Instant ahora) {
        return jdbc.update("""
                UPDATE cambios_plan SET estado = 'ACEPTADO'::estado_cambio_plan, respondido_at = ?
                 WHERE id = ? AND estado = 'AUTORIZADO'
                """, Timestamp.from(ahora), id) == 1;
    }

    public boolean rechazarComoCliente(UUID id, String motivo, Instant ahora) {
        return jdbc.update("""
                UPDATE cambios_plan SET estado = 'RECHAZADO_CLIENTE'::estado_cambio_plan, motivo_rechazo = ?, respondido_at = ?
                 WHERE id = ? AND estado = 'AUTORIZADO'
                """, motivo, Timestamp.from(ahora), id) == 1;
    }

    // ------------------------------------------------------------------------------------------- Aplicar un cambio aceptado

    /** Agrega la fase en su posición: las que quedan de ahí en adelante bajan un lugar (de la última a la primera: el orden es único). */
    public void agregarFase(UUID proyectoId, UUID cambioId, int posicion, String nombre, boolean apruebaCliente, List<PasoPlantilla> pasos) {
        List<UUID> aMover = jdbc.queryForList("""
                SELECT id FROM proyecto_fases WHERE proyecto_id = ? AND estado <> 'QUITADA' AND orden >= ? ORDER BY orden DESC
                """, UUID.class, proyectoId, posicion);
        for (UUID id : aMover) {
            jdbc.update("UPDATE proyecto_fases SET orden = orden + 1 WHERE id = ?", id);
        }
        UUID faseId = jdbc.queryForObject("""
                INSERT INTO proyecto_fases (proyecto_id, orden, nombre, aprueba_cliente, cambio_plan_id) VALUES (?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, proyectoId, posicion, nombre, apruebaCliente, cambioId);
        int orden = 0;
        for (PasoPlantilla p : pasos) {
            jdbc.update("INSERT INTO pasos (proyecto_fase_id, orden, titulo, explicacion) VALUES (?, ?, ?, ?)", faseId, ++orden, p.titulo(),
                    p.explicacion());
        }
    }

    /** Marca la fase como quitada (no se borra) y sube un lugar las que venían después. */
    public void quitarFase(UUID proyectoId, UUID faseId) {
        int orden = jdbc.queryForObject("SELECT orden FROM proyecto_fases WHERE id = ?", Integer.class, faseId);
        jdbc.update("UPDATE proyecto_fases SET estado = 'QUITADA'::estado_fase WHERE id = ? AND estado = 'PENDIENTE'", faseId);
        List<UUID> aMover = jdbc.queryForList("""
                SELECT id FROM proyecto_fases WHERE proyecto_id = ? AND estado <> 'QUITADA' AND orden > ? ORDER BY orden
                """, UUID.class, proyectoId, orden);
        for (UUID id : aMover) {
            jdbc.update("UPDATE proyecto_fases SET orden = orden - 1 WHERE id = ?", id);
        }
    }

    /** El paso queda QUITADO, no se borra (RN-30). La base exige que el cambio ya esté ACEPTADO. */
    public void quitarPaso(UUID pasoId) {
        jdbc.update("UPDATE pasos SET estado = 'QUITADO'::estado_paso WHERE id = ? AND estado = 'PENDIENTE'", pasoId);
    }

    public void actualizarEntrega(UUID proyectoId, LocalDate entrega) {
        jdbc.update("UPDATE proyectos SET fecha_estimada_entrega = ?, updated_at = now(), version = version + 1 WHERE id = ?", entrega,
                proyectoId);
    }

    /** Crea el paso ALCANCE en la fase en curso y lo enlaza con la solicitud (1:1); no cuenta en el límite de 12 pasos. */
    public UUID agregarPasoDeAlcance(UUID faseId, String titulo, String explicacion) {
        return jdbc.queryForObject("""
                INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion, origen)
                VALUES (?, (SELECT coalesce(max(orden), 0) + 1 FROM pasos WHERE proyecto_fase_id = ?), 'ALCANCE'::tipo_paso, ?, ?, 'Lo pidió el cliente')
                RETURNING id
                """, UUID.class, faseId, faseId, titulo, explicacion);
    }

    // ------------------------------------------------------------------------------------------- La solicitud de alcance

    /** POR_COTIZAR → COTIZADO al autorizar la cotización, con su costo y los días que agrega. */
    public void cotizarSolicitud(UUID solicitudId, BigDecimal costo, int dias) {
        jdbc.update("""
                UPDATE solicitudes_cambio SET estado = 'COTIZADO'::estado_cambio, costo = ?, dias_adicionales = ? WHERE id = ?
                """, costo, dias, solicitudId);
    }

    /** COTIZADO → ACEPTADO con el paso que se creó. */
    public void aceptarSolicitud(UUID solicitudId, UUID pasoId) {
        jdbc.update("UPDATE solicitudes_cambio SET estado = 'ACEPTADO'::estado_cambio, paso_id = ? WHERE id = ?", pasoId, solicitudId);
    }

    /** COTIZADO → RECHAZADO cuando el cliente no acepta la cotización. */
    public void rechazarSolicitud(UUID solicitudId) {
        jdbc.update("UPDATE solicitudes_cambio SET estado = 'RECHAZADO'::estado_cambio WHERE id = ?", solicitudId);
    }
}
