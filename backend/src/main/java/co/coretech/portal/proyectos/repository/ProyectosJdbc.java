package co.coretech.portal.proyectos.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Consultas de LECTURA de proyectos con SQL (HU-02, HU-17): la lista y el detalle con el criterio «en riesgo» (funciones y
 * agregados de PostgreSQL) y el bloqueo de la fila. Las escrituras de la tabla proyectos (alta, estado, equipo, plan) las hace
 * {@code ProyectoRepositorio} con JPA (ADR-004); las columnas que calcula la base (avance) solo se leen.
 */
@Repository
public class ProyectosJdbc {

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

    private static ProyectoFila fila(ResultSet r, int n) throws SQLException {
        Timestamp finalizado = r.getTimestamp("finalizado_at");
        return new ProyectoFila(r.getObject("id", UUID.class), r.getString("codigo"), r.getString("nombre"), r.getString("alcance"),
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
    public List<ProyectoFila> todos(LocalDate hoy) {
        return nombrado.query(LISTA + ORDEN, new MapSqlParameterSource("hoy", hoy), ProyectosJdbc::fila);
    }

    public Optional<ProyectoFila> porCodigo(String codigo, LocalDate hoy) {
        return nombrado.query(LISTA + " WHERE p.codigo = :codigo", new MapSqlParameterSource("hoy", hoy).addValue("codigo", codigo),
                ProyectosJdbc::fila).stream().findFirst();
    }

    /** Bloquea la fila del proyecto hasta el final de la transacción: dos ediciones a la vez no se pisan. */
    public void bloquear(String codigo) {
        jdbc.query("SELECT id FROM proyectos WHERE codigo = ? FOR UPDATE", (r, n) -> r.getObject(1, UUID.class), codigo);
    }

}
