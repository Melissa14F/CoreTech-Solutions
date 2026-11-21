package co.coretech.portal.proyectos.datos;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabla proyectos y la copia de la plantilla como plan en borrador (HU-02, HU-17). Todo con SQL: los estados de
 * PostgreSQL se escriben con su cast y las columnas que calcula la base (avance) solo se leen.
 */
@Repository
public class ProyectosJdbc {

    /**
     * Un proyecto con lo que la lista del administrador muestra.
     *
     * @param riesgo        por qué está "en riesgo" (HU-17): ENTREGA_VENCIDA, POCO_AVANCE, SIN_AVANCES o PLAN_SIN_RESPUESTA;
     *                      nulo si no lo está. Se calcula en la consulta, no se guarda.
     * @param ultimoAvance  fecha del último paso hecho o, si no hay ninguno, la de la aprobación del plan
     */
    public record Fila(UUID id, String codigo, String nombre, String alcance, UUID citaId, UUID clienteId, UUID servicioId,
            UUID equipoId, LocalDate fechaInicio, LocalDate fechaEntrega, String estado, String estadoPlan,
            BigDecimal valorCotizado, LocalDate planPlazoRespuesta, LocalDate planVenceEl, BigDecimal avance,
            String motivoEstado, LocalDate garantiaHasta, Instant finalizadoAt, Instant creadoAt, Instant actualizadoAt,
            int fases, Integer faseActualOrden, String faseActualNombre, LocalDate ultimoAvance, String riesgo) {
    }

    private static final String LISTA = """
            SELECT p.id, p.codigo, p.nombre, p.alcance, p.cita_id, p.cliente_id, p.servicio_id, p.equipo_id,
                   p.fecha_inicio, p.fecha_estimada_entrega, p.estado::text, p.estado_plan::text, p.valor_cotizado,
                   p.plan_plazo_respuesta, p.plan_vence_el, p.avance_porcentaje, p.motivo_estado, p.garantia_hasta,
                   p.finalizado_at, p.created_at, p.updated_at,
                   (SELECT count(*) FROM proyecto_fases f WHERE f.proyecto_id = p.id AND f.estado <> 'QUITADA')::int AS fases,
                   fa.orden AS fase_orden, fa.nombre AS fase_nombre, av.ultimo AS ultimo_avance,
                   CASE WHEN p.estado = 'ACTIVO' THEN
                     CASE
                       WHEN p.fecha_estimada_entrega < :hoy THEN 'ENTREGA_VENCIDA'
                       WHEN fn_dias_habiles_entre(:hoy, p.fecha_estimada_entrega) <= 5 AND p.avance_porcentaje < 80 THEN 'POCO_AVANCE'
                       WHEN p.estado_plan = 'APROBADO' AND fn_dias_habiles_entre(av.ultimo, :hoy) >= 7 THEN 'SIN_AVANCES'
                       WHEN p.estado_plan = 'ENVIADO' AND p.plan_plazo_respuesta < :hoy THEN 'PLAN_SIN_RESPUESTA'
                     END
                   END AS riesgo
              FROM proyectos p
              LEFT JOIN LATERAL (
                   SELECT f.orden, f.nombre FROM proyecto_fases f
                    WHERE f.proyecto_id = p.id AND f.estado NOT IN ('APROBADA', 'QUITADA')
                    ORDER BY f.orden LIMIT 1) fa ON true
              LEFT JOIN LATERAL (
                   SELECT coalesce((max(s.hecho_at) AT TIME ZONE 'America/Bogota')::date,
                                   (p.plan_aprobado_at AT TIME ZONE 'America/Bogota')::date) AS ultimo
                     FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                    WHERE f.proyecto_id = p.id AND s.estado = 'HECHO') av ON true
            """;

    private static final String ORDEN = """
             ORDER BY CASE p.estado WHEN 'ACTIVO' THEN 0 WHEN 'PAUSADO' THEN 1 WHEN 'EN_GARANTIA' THEN 2
                                    WHEN 'FINALIZADO' THEN 3 ELSE 4 END,
                      p.fecha_estimada_entrega, p.codigo
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate nombrado;

    ProyectosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.nombrado = new NamedParameterJdbcTemplate(jdbc);
    }

    private static Fila fila(ResultSet r, int n) throws SQLException {
        Timestamp finalizado = r.getTimestamp("finalizado_at");
        return new Fila(r.getObject("id", UUID.class), r.getString("codigo"), r.getString("nombre"), r.getString("alcance"),
                r.getObject("cita_id", UUID.class), r.getObject("cliente_id", UUID.class), r.getObject("servicio_id", UUID.class),
                r.getObject("equipo_id", UUID.class), r.getObject("fecha_inicio", LocalDate.class),
                r.getObject("fecha_estimada_entrega", LocalDate.class), r.getString("estado"), r.getString("estado_plan"),
                r.getBigDecimal("valor_cotizado"), r.getObject("plan_plazo_respuesta", LocalDate.class),
                r.getObject("plan_vence_el", LocalDate.class), r.getBigDecimal("avance_porcentaje"),
                r.getString("motivo_estado"), r.getObject("garantia_hasta", LocalDate.class),
                finalizado == null ? null : finalizado.toInstant(), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant(), r.getInt("fases"), r.getObject("fase_orden", Integer.class),
                r.getString("fase_nombre"), r.getObject("ultimo_avance", LocalDate.class), r.getString("riesgo"));
    }

    /** Todos los proyectos, con "en riesgo" calculado a la fecha {@code hoy}: los activos primero, luego pausados, garantía… */
    public List<Fila> todos(LocalDate hoy) {
        return nombrado.query(LISTA + ORDEN, new MapSqlParameterSource("hoy", hoy), ProyectosJdbc::fila);
    }

    public Optional<Fila> porCodigo(String codigo, LocalDate hoy) {
        return nombrado.query(LISTA + " WHERE p.codigo = :codigo", new MapSqlParameterSource("hoy", hoy).addValue("codigo", codigo),
                ProyectosJdbc::fila).stream().findFirst();
    }

    /** Bloquea la fila del proyecto hasta el final de la transacción: dos ediciones a la vez no se pisan. */
    public void bloquear(String codigo) {
        jdbc.query("SELECT id FROM proyectos WHERE codigo = ? FOR UPDATE", (r, n) -> r.getObject(1, UUID.class), codigo);
    }

    /** Códigos de proyecto por cita: una cita genera como máximo un proyecto (RN-04). */
    public Map<UUID, String> codigosPorCita() {
        Map<UUID, String> codigos = new java.util.HashMap<>();
        jdbc.query("SELECT cita_id, codigo FROM proyectos", r -> {
            codigos.put(r.getObject(1, UUID.class), r.getString(2));
        });
        return codigos;
    }

    public Optional<String> codigoDeCita(UUID citaId) {
        return jdbc.query("SELECT codigo FROM proyectos WHERE cita_id = ?", (r, n) -> r.getString(1), citaId).stream().findFirst();
    }

    public String siguienteCodigo() {
        return jdbc.queryForObject("SELECT fn_siguiente_codigo_proyecto()", String.class);
    }

    public UUID insertar(String codigo, String nombre, String alcance, UUID citaId, UUID clienteId, UUID servicioId,
            UUID equipoId, UUID creadoPor, LocalDate inicio, LocalDate entrega) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO proyectos (id, codigo, nombre, alcance, cita_id, cliente_id, servicio_id, equipo_id, creado_por,
                                       fecha_inicio, fecha_estimada_entrega)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, codigo, nombre, alcance, citaId, clienteId, servicioId, equipoId, creadoPor, inicio, entrega);
        return id;
    }

    /**
     * Copia la plantilla de fases del servicio como el plan del proyecto (RN-06, RN-27): las fases quedan
     * PENDIENTES (la fase 1 empieza cuando el cliente aprueba el plan) y sus pasos, PENDIENTES. Devuelve cuántas fases.
     */
    public int copiarPlantilla(UUID proyectoId, UUID servicioId) {
        int fases = jdbc.update("""
                INSERT INTO proyecto_fases (id, proyecto_id, orden, nombre, que_es, aprueba_cliente)
                SELECT gen_random_uuid(), ?, orden, nombre, que_es, aprueba_cliente
                  FROM plantilla_fases WHERE servicio_id = ?
                """, proyectoId, servicioId);
        jdbc.update("""
                INSERT INTO pasos (proyecto_fase_id, orden, titulo, explicacion)
                SELECT pf.id, pp.orden, pp.titulo, pp.explicacion
                  FROM plantilla_pasos pp
                  JOIN plantilla_fases tf ON tf.id = pp.plantilla_fase_id
                  JOIN proyecto_fases pf ON pf.proyecto_id = ? AND pf.orden = tf.orden
                 WHERE tf.servicio_id = ?
                """, proyectoId, servicioId);
        return fases;
    }

    public void actualizarDatos(UUID id, String nombre, String alcance, LocalDate entrega) {
        jdbc.update("""
                UPDATE proyectos SET nombre = ?, alcance = ?, fecha_estimada_entrega = ?, updated_at = now(), version = version + 1
                 WHERE id = ?
                """, nombre, alcance, entrega, id);
    }

    public void cambiarEquipo(UUID id, UUID equipoId) {
        jdbc.update("UPDATE proyectos SET equipo_id = ?, updated_at = now(), version = version + 1 WHERE id = ?", equipoId, id);
    }

    /** Pausa o cancela con su motivo (se muestra en el panel del cliente y del equipo). */
    public void cambiarEstado(UUID id, String estado, String motivo) {
        jdbc.update("""
                UPDATE proyectos SET estado = ?::estado_proyecto, motivo_estado = ?, updated_at = now(), version = version + 1
                 WHERE id = ?
                """, estado, motivo, id);
    }

    /**
     * Reanuda (RN-29): el proyecto vuelve a ACTIVO y, si el plan estaba RECHAZADO o VENCIDO, vuelve a BORRADOR en la misma
     * sentencia (así lo exige el disparador fn_validar_estado_plan) con el equipo elegido.
     */
    public void reanudar(UUID id, UUID equipoId, boolean planVuelveABorrador) {
        jdbc.update("""
                UPDATE proyectos SET estado = 'ACTIVO', motivo_estado = NULL, equipo_id = ?,
                       estado_plan = CASE WHEN ? THEN 'BORRADOR'::estado_plan ELSE estado_plan END,
                       plan_recordado_at = NULL, plan_cancelacion_avisada_at = NULL,
                       updated_at = now(), version = version + 1
                 WHERE id = ?
                """, equipoId, planVuelveABorrador, id);
    }

    public boolean hayCambioDePlanAbierto(UUID proyectoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cambios_plan WHERE proyecto_id = ? AND estado IN ('PROPUESTO', 'AUTORIZADO'))",
                Boolean.class, proyectoId));
    }

    /** El cambio de fecha que el administrador crea al editar un plan aprobado: nace AUTORIZADO y lo acepta el cliente (D-33). */
    public UUID insertarCambioDeFecha(UUID proyectoId, LocalDate nuevaFecha, String motivo, UUID administradorId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cambios_plan (id, proyecto_id, tipo, nueva_fecha_entrega, motivo, estado, propuesto_por, autorizado_por, autorizado_at)
                VALUES (?, ?, 'CAMBIAR_FECHA', ?, ?, 'AUTORIZADO', ?, ?, now())
                """, id, proyectoId, nuevaFecha, motivo, administradorId, administradorId);
        return id;
    }
}
