package co.coretech.portal.proyectos.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;

/**
 * Plan de trabajo de un proyecto (HU-28, HU-29, RN-27 a RN-29): lectura del plan y de la cotización, reemplazo de las fases y
 * los pasos, inicio de la primera fase y respuestas del cliente. Con SQL, porque las fases y los pasos se reemplazan en bloque
 * respetando índices únicos que no se pueden diferir. Los cambios de estado y la cotización del proyecto los hace
 * {@code ProyectoRepositorio} (JPA). Cada método de escritura debe llamarse dentro de la transacción del servicio.
 */
@Repository
public class PlanesJdbc {

    /** La cotización y las marcas de tiempo del plan; {@code valor} y {@code condiciones} son nulos en un borrador nuevo. */
    public record Cotizacion(BigDecimal valor, String condiciones, LocalDate entrega, Instant enviadoAt, Instant aprobadoAt) {
    }

    /** Una respuesta del cliente (es un historial: no se edita). */
    public record Respuesta(UUID id, String tipo, String comentario, Instant creadaEn) {
    }

    /** Un proyecto con el plan enviado, vencido o rechazado: lo que revisa la tarea diaria. */
    public record PlanPendiente(UUID id, String codigo, String nombre, UUID clienteId, UUID equipoId, String estadoPlan,
            LocalDate plazoRespuesta, LocalDate venceEl, Instant rechazadoEn, boolean recordado, boolean cancelacionAvisada) {
    }

    private final JdbcTemplate jdbc;

    PlanesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------- Lectura

    public Cotizacion cotizacion(UUID proyectoId) {
        return jdbc.queryForObject("""
                SELECT valor_cotizado, condiciones_cotizacion, fecha_estimada_entrega, plan_enviado_at, plan_aprobado_at
                  FROM proyectos WHERE id = ?
                """, (r, n) -> new Cotizacion(r.getBigDecimal(1), r.getString(2), r.getObject(3, LocalDate.class),
                instante(r.getTimestamp(4)), instante(r.getTimestamp(5))), proyectoId);
    }

    /** Las fases del plan (sin las quitadas) con sus pasos, en orden. */
    public List<FasePlantilla> fases(UUID proyectoId) {
        Map<UUID, FasePlantilla> fases = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, orden, nombre, que_es, aprueba_cliente FROM proyecto_fases
                 WHERE proyecto_id = ? AND estado <> 'QUITADA' ORDER BY orden
                """, r -> {
                    fases.put(r.getObject("id", UUID.class), new FasePlantilla(r.getInt("orden"), r.getString("nombre"),
                            r.getString("que_es"), r.getBoolean("aprueba_cliente"), new ArrayList<>()));
                }, proyectoId);
        jdbc.query("""
                SELECT s.proyecto_fase_id, s.orden, s.titulo, s.explicacion
                  FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                 WHERE f.proyecto_id = ? AND f.estado <> 'QUITADA' AND s.tipo = 'PASO' AND s.estado <> 'QUITADO'
                 ORDER BY s.orden
                """, r -> {
                    FasePlantilla fase = fases.get(r.getObject("proyecto_fase_id", UUID.class));
                    if (fase != null) {
                        fase.pasos().add(new PasoPlantilla(r.getInt("orden"), r.getString("titulo"), r.getString("explicacion")));
                    }
                }, proyectoId);
        return new ArrayList<>(fases.values());
    }

    /** La respuesta más reciente del cliente de un tipo dado (por ejemplo AJUSTES_PEDIDOS o RECHAZADO). */
    public Optional<Respuesta> ultimaRespuesta(UUID proyectoId, String tipo) {
        return jdbc.query("""
                SELECT id, respuesta::text, comentario, created_at FROM respuestas_plan
                 WHERE proyecto_id = ? AND respuesta = ?::tipo_respuesta_plan ORDER BY created_at DESC LIMIT 1
                """, (r, n) -> new Respuesta(r.getObject(1, UUID.class), r.getString(2), r.getString(3),
                r.getTimestamp(4).toInstant()), proyectoId, tipo).stream().findFirst();
    }

    // ------------------------------------------------------------------------------------------- Edición del borrador

    /**
     * Reemplaza todas las fases y pasos del plan (RN-27). Se borra y se vuelve a insertar: los índices únicos de orden y
     * de nombre no son diferibles. Solo con el plan en BORRADOR o AJUSTES_PEDIDOS (la base lo exige con fn_plan_en_revision).
     */
    public void reemplazarFases(UUID proyectoId, List<FasePlantilla> fases) {
        jdbc.update("DELETE FROM proyecto_fases WHERE proyecto_id = ? AND estado <> 'QUITADA'", proyectoId);
        int orden = 0;
        for (FasePlantilla fase : fases) {
            UUID faseId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO proyecto_fases (id, proyecto_id, orden, nombre, que_es, aprueba_cliente)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, faseId, proyectoId, ++orden, fase.nombre(), fase.queEs(), fase.apruebaCliente());
            int numero = 0;
            for (PasoPlantilla paso : fase.pasos()) {
                jdbc.update("INSERT INTO pasos (proyecto_fase_id, orden, titulo, explicacion) VALUES (?, ?, ?, ?)",
                        faseId, ++numero, paso.titulo(), paso.explicacion());
            }
        }
    }

    // ------------------------------------------------------------------------------------------- Cambios de estado del plan

    /** La primera fase del plan pasa a EN_CURSO: el trabajo empieza por ella (RN-06). */
    public void iniciarPrimeraFase(UUID proyectoId) {
        jdbc.update("""
                UPDATE proyecto_fases SET estado = 'EN_CURSO', iniciada_at = now()
                 WHERE proyecto_id = ? AND estado = 'PENDIENTE'
                   AND orden = (SELECT min(orden) FROM proyecto_fases WHERE proyecto_id = ? AND estado <> 'QUITADA')
                """, proyectoId, proyectoId);
    }

    public UUID insertarRespuesta(UUID proyectoId, UUID clienteId, String tipo, String comentario) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO respuestas_plan (id, proyecto_id, cliente_id, respuesta, comentario)
                VALUES (?, ?, ?, ?::tipo_respuesta_plan, ?)
                """, id, proyectoId, clienteId, tipo, comentario);
        return id;
    }

    // ------------------------------------------------------------------------------------------- Tarea diaria

    /** Planes enviados de proyectos activos y planes vencidos o rechazados de proyectos pausados (RN-29). */
    public List<PlanPendiente> pendientes() {
        return jdbc.query("""
                SELECT p.id, p.codigo, p.nombre, p.cliente_id, p.equipo_id, p.estado_plan::text, p.plan_plazo_respuesta,
                       p.plan_vence_el,
                       (SELECT max(r.created_at) FROM respuestas_plan r
                         WHERE r.proyecto_id = p.id AND r.respuesta = 'RECHAZADO') AS rechazado_en,
                       p.plan_recordado_at IS NOT NULL AS recordado, p.plan_cancelacion_avisada_at IS NOT NULL AS avisada
                  FROM proyectos p
                 WHERE (p.estado = 'ACTIVO' AND p.estado_plan = 'ENVIADO')
                    OR (p.estado = 'PAUSADO' AND p.estado_plan IN ('VENCIDO', 'RECHAZADO'))
                 ORDER BY p.codigo
                """, (r, n) -> new PlanPendiente(r.getObject(1, UUID.class), r.getString(2), r.getString(3),
                r.getObject(4, UUID.class), r.getObject(5, UUID.class), r.getString(6), r.getObject(7, LocalDate.class),
                r.getObject(8, LocalDate.class), instante(r.getTimestamp(9)), r.getBoolean(10), r.getBoolean(11)));
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
