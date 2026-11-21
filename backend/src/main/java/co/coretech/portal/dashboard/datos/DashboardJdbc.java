package co.coretech.portal.dashboard.datos;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de solo lectura del dashboard (HU-23). Cada una se resuelve en la base con agregados: no se traen entidades a
 * memoria. Las fechas «de hoy» las pone el servicio con el {@code Clock}, nunca la base, para poder probar con un día fijo.
 */
@Repository
public class DashboardJdbc {

    /** Lo que se cuenta de un vistazo (HU-23, paso 2). */
    public record Conteos(int citasSemana, int citasSinAsignar, int planesPorAprobar, int cambiosPorAutorizar, int fasesPorAprobar,
            int fasesVencidas, int proyectosEnGarantia, int garantiasCumplidas, int solicitudesPendientes, int proyectosActivos,
            int capacidad) {
    }

    public record CitasPorMes(LocalDate mes, int citas) {
    }

    public record Equipo(UUID id, String productOwner, int activos, int maximo) {
    }

    public record ProyectosEnFase(String fase, int proyectos) {
    }

    public record CitasPorServicio(String servicio, int citas) {
    }

    public record CitasPorEstado(int pendientes, int confirmadas, int atendidas, int canceladas) {
    }

    /** Un proyecto activo con un riesgo (el mismo criterio de «en riesgo» de la lista de proyectos, HU-17). */
    public record ProyectoEnRiesgo(String codigo, String nombre, String riesgo, String fase, LocalDate entrega, BigDecimal avance,
            String productOwner, Integer diasSinAvance) {
    }

    public record CambioPorAutorizar(String codigo, String proyecto, String tipo, String productOwner, BigDecimal valorAdicional,
            LocalDate nuevaEntrega, String fasePropuesta) {
    }

    public record FaseVencida(String codigo, String proyecto, String fase, LocalDate plazo, String productOwner) {
    }

    public record CitaEnAlerta(String codigo, String cliente, String servicio, Instant fechaHora, String modalidad, String productOwner,
            int diasHabiles) {
    }

    public record GarantiaCumplida(String codigo, String proyecto, LocalDate garantiaHasta, int correccionesAbiertas) {
    }

    public record Actividad(Instant fecha, String usuario, String categoria, String accion, String entidad, String entidadId) {
    }

    private static final String PO = """
            (SELECT d.nombre FROM integrantes_equipo i JOIN desarrolladores d ON d.id = i.desarrollador_id
              WHERE i.equipo_id = p.equipo_id AND i.rol = 'PRODUCT_OWNER' LIMIT 1)""";

    private static final String ZONA = "America/Bogota";

    private final NamedParameterJdbcTemplate jdbc;

    DashboardJdbc(JdbcTemplate plantilla) {
        this.jdbc = new NamedParameterJdbcTemplate(plantilla);
    }

    private static Timestamp instante(LocalDate dia, java.time.ZoneId zona) {
        return Timestamp.from(dia.atStartOfDay(zona).toInstant());
    }

    // ------------------------------------------------------------------------------------------- Conteos

    /**
     * Todos los conteos de una sola consulta. {@code semanaDesde} y {@code semanaHasta} (el lunes y el lunes siguiente) delimitan
     * «las citas de la semana»; {@code ahora} deja fuera lo que ya pasó.
     */
    public Conteos conteos(LocalDate hoy, LocalDate semanaDesde, LocalDate semanaHasta, java.time.ZoneId zona) {
        var p = new MapSqlParameterSource().addValue("hoy", hoy).addValue("desde", instante(semanaDesde, zona))
                .addValue("hasta", instante(semanaHasta, zona)).addValue("ahora", Timestamp.from(hoy.atStartOfDay(zona).toInstant()));
        return jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM citas WHERE estado IN ('PENDIENTE', 'CONFIRMADA') AND fecha_hora >= :desde AND fecha_hora < :hasta),
                  (SELECT count(*) FROM citas WHERE estado = 'PENDIENTE' AND equipo_id IS NULL AND fecha_hora >= :ahora),
                  (SELECT count(*) FROM proyectos WHERE estado = 'ACTIVO' AND estado_plan = 'ENVIADO'),
                  (SELECT count(*) FROM cambios_plan WHERE estado = 'PROPUESTO'),
                  (SELECT count(*) FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id
                    WHERE f.estado = 'EN_REVISION' AND p.estado = 'ACTIVO'),
                  (SELECT count(*) FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id
                    WHERE f.estado = 'EN_REVISION' AND p.estado = 'ACTIVO' AND f.plazo_aprobacion < :hoy),
                  (SELECT count(*) FROM proyectos WHERE estado = 'EN_GARANTIA'),
                  (SELECT count(*) FROM proyectos WHERE estado = 'EN_GARANTIA' AND garantia_hasta < :hoy),
                  (SELECT count(*) FROM solicitudes_reunion WHERE estado = 'PENDIENTE'),
                  (SELECT count(*) FROM proyectos WHERE estado = 'ACTIVO'),
                  (SELECT coalesce(sum(max_proyectos), 0) FROM equipos WHERE activo)
                """, p, (r, n) -> new Conteos(r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4), r.getInt(5), r.getInt(6), r.getInt(7),
                r.getInt(8), r.getInt(9), r.getInt(10), r.getInt(11)));
    }

    // ------------------------------------------------------------------------------------------- Citas

    /** Citas agendadas (por su fecha de creación, en hora de Colombia) entre dos días: {@code desde} incluido, {@code hasta} excluido. */
    public int citasAgendadas(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM citas
                 WHERE (created_at AT TIME ZONE 'America/Bogota')::date >= :desde AND (created_at AT TIME ZONE 'America/Bogota')::date < :hasta
                """, new MapSqlParameterSource("desde", desde).addValue("hasta", hasta), Integer.class);
    }

    /** Las citas de cada mes, desde {@code primerMes} (primer día) hasta {@code ultimoMes}, con los meses sin citas en 0. */
    public List<CitasPorMes> citasPorMes(LocalDate primerMes, LocalDate ultimoMes) {
        return jdbc.query("""
                SELECT m::date AS mes, count(c.id) AS citas
                  FROM generate_series(:primero::date, :ultimo::date, interval '1 month') m
                  LEFT JOIN citas c ON (c.created_at AT TIME ZONE 'America/Bogota')::date >= m::date
                                   AND (c.created_at AT TIME ZONE 'America/Bogota')::date < (m + interval '1 month')::date
                 GROUP BY m ORDER BY m
                """, new MapSqlParameterSource("primero", primerMes).addValue("ultimo", ultimoMes),
                (r, n) -> new CitasPorMes(r.getObject("mes", LocalDate.class), r.getInt("citas")));
    }

    public CitasPorEstado citasPorEstado(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE estado = 'PENDIENTE'), count(*) FILTER (WHERE estado = 'CONFIRMADA'),
                       count(*) FILTER (WHERE estado = 'ATENDIDA'), count(*) FILTER (WHERE estado = 'CANCELADA')
                  FROM citas
                 WHERE (created_at AT TIME ZONE 'America/Bogota')::date >= :desde AND (created_at AT TIME ZONE 'America/Bogota')::date < :hasta
                """, new MapSqlParameterSource("desde", desde).addValue("hasta", hasta),
                (r, n) -> new CitasPorEstado(r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4)));
    }

    /** Días hábiles promedio entre agendar y confirmar una cita, de las confirmadas en el periodo; nulo si no se confirmó ninguna. */
    public Double promedioDiasParaConfirmar(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT avg(fn_dias_habiles_entre((created_at AT TIME ZONE 'America/Bogota')::date, (confirmada_at AT TIME ZONE 'America/Bogota')::date))::float8
                  FROM citas
                 WHERE confirmada_at IS NOT NULL AND (confirmada_at AT TIME ZONE 'America/Bogota')::date >= :desde
                   AND (confirmada_at AT TIME ZONE 'America/Bogota')::date < :hasta AND confirmada_at >= created_at
                """, new MapSqlParameterSource("desde", desde).addValue("hasta", hasta), Double.class);
    }

    public List<CitasPorServicio> serviciosMasSolicitados(LocalDate desde, LocalDate hasta, int limite) {
        return jdbc.query("""
                SELECT s.nombre, count(*) AS citas FROM citas c JOIN servicios s ON s.id = c.servicio_id
                 WHERE (c.created_at AT TIME ZONE 'America/Bogota')::date >= :desde AND (c.created_at AT TIME ZONE 'America/Bogota')::date < :hasta
                 GROUP BY s.id, s.nombre ORDER BY count(*) DESC, s.nombre LIMIT :limite
                """, new MapSqlParameterSource("desde", desde).addValue("hasta", hasta).addValue("limite", limite),
                (r, n) -> new CitasPorServicio(r.getString(1), r.getInt(2)));
    }

    /** Cuántas citas de ese periodo terminaron en un proyecto (la conversión). */
    public int proyectosDeCitas(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM proyectos p JOIN citas c ON c.id = p.cita_id
                 WHERE (c.created_at AT TIME ZONE 'America/Bogota')::date >= :desde AND (c.created_at AT TIME ZONE 'America/Bogota')::date < :hasta
                """, new MapSqlParameterSource("desde", desde).addValue("hasta", hasta), Integer.class);
    }

    /** Citas pendientes con equipo que llevan {@code minimoDias} días hábiles o más sin confirmarse y aún no pasan. */
    public List<CitaEnAlerta> citasSinConfirmar(LocalDate hoy, int minimoDias, java.time.ZoneId zona) {
        return jdbc.query("""
                SELECT c.codigo, u.nombre, s.nombre, c.fecha_hora, c.modalidad::text,
                       (SELECT d.nombre FROM integrantes_equipo i JOIN desarrolladores d ON d.id = i.desarrollador_id
                         WHERE i.equipo_id = c.equipo_id AND i.rol = 'PRODUCT_OWNER' LIMIT 1),
                       fn_dias_habiles_entre((c.created_at AT TIME ZONE 'America/Bogota')::date, :hoy)
                  FROM citas c JOIN usuarios u ON u.id = c.cliente_id JOIN servicios s ON s.id = c.servicio_id
                 WHERE c.estado = 'PENDIENTE' AND c.equipo_id IS NOT NULL AND c.fecha_hora >= :ahora
                   AND fn_dias_habiles_entre((c.created_at AT TIME ZONE 'America/Bogota')::date, :hoy) >= :minimo
                 ORDER BY c.fecha_hora LIMIT 10
                """, new MapSqlParameterSource("hoy", hoy).addValue("minimo", minimoDias)
                .addValue("ahora", Timestamp.from(hoy.atStartOfDay(zona).toInstant())), DashboardJdbc::cita);
    }

    public List<CitaEnAlerta> citasSinEquipo(LocalDate hoy, java.time.ZoneId zona) {
        return jdbc.query("""
                SELECT c.codigo, u.nombre, s.nombre, c.fecha_hora, c.modalidad::text, NULL::text, 0
                  FROM citas c JOIN usuarios u ON u.id = c.cliente_id JOIN servicios s ON s.id = c.servicio_id
                 WHERE c.estado = 'PENDIENTE' AND c.equipo_id IS NULL AND c.fecha_hora >= :ahora
                 ORDER BY c.fecha_hora LIMIT 10
                """, new MapSqlParameterSource("ahora", Timestamp.from(hoy.atStartOfDay(zona).toInstant())), DashboardJdbc::cita);
    }

    private static CitaEnAlerta cita(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new CitaEnAlerta(r.getString(1), r.getString(2), r.getString(3), r.getTimestamp(4).toInstant(), r.getString(5), r.getString(6),
                r.getInt(7));
    }

    // ------------------------------------------------------------------------------------------- Proyectos

    /** Proyectos activos que ya tienen un riesgo: entrega vencida, poco avance, sin avances o plan sin respuesta (HU-17). */
    public List<ProyectoEnRiesgo> proyectosEnRiesgo(LocalDate hoy) {
        return jdbc.query("""
                SELECT * FROM (
                  SELECT p.codigo, p.nombre, p.fecha_estimada_entrega, p.avance_porcentaje, fa.nombre AS fase, """ + PO + """
                          AS po, av.ultimo,
                         CASE
                           WHEN p.fecha_estimada_entrega < :hoy THEN 'ENTREGA_VENCIDA'
                           WHEN fn_dias_habiles_entre(:hoy, p.fecha_estimada_entrega) <= 5 AND p.avance_porcentaje < 80 THEN 'POCO_AVANCE'
                           WHEN p.estado_plan = 'APROBADO' AND fn_dias_habiles_entre(av.ultimo, :hoy) >= 7 THEN 'SIN_AVANCES'
                           WHEN p.estado_plan = 'ENVIADO' AND p.plan_plazo_respuesta < :hoy THEN 'PLAN_SIN_RESPUESTA'
                         END AS riesgo,
                         CASE WHEN p.estado_plan = 'APROBADO' THEN fn_dias_habiles_entre(av.ultimo, :hoy) END AS dias
                    FROM proyectos p
                    LEFT JOIN LATERAL (SELECT f.nombre FROM proyecto_fases f WHERE f.proyecto_id = p.id AND f.estado NOT IN ('APROBADA', 'QUITADA')
                                        ORDER BY f.orden LIMIT 1) fa ON true
                    LEFT JOIN LATERAL (SELECT coalesce((max(s.hecho_at) AT TIME ZONE 'America/Bogota')::date,
                                                       (p.plan_aprobado_at AT TIME ZONE 'America/Bogota')::date) AS ultimo
                                         FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                                        WHERE f.proyecto_id = p.id AND s.estado = 'HECHO') av ON true
                   WHERE p.estado = 'ACTIVO') t
                 WHERE riesgo IS NOT NULL
                 ORDER BY CASE riesgo WHEN 'ENTREGA_VENCIDA' THEN 0 WHEN 'POCO_AVANCE' THEN 1 WHEN 'SIN_AVANCES' THEN 2 ELSE 3 END, fecha_estimada_entrega, codigo
                """, new MapSqlParameterSource("hoy", hoy), (r, n) -> new ProyectoEnRiesgo(r.getString("codigo"), r.getString("nombre"),
                r.getString("riesgo"), r.getString("fase"), r.getObject("fecha_estimada_entrega", LocalDate.class), r.getBigDecimal("avance_porcentaje"),
                r.getString("po"), r.getObject("dias", Integer.class)));
    }

    public List<CambioPorAutorizar> cambiosPorAutorizar() {
        return jdbc.query("""
                SELECT p.codigo, p.nombre, c.tipo::text, """ + PO + """
                        , c.valor_adicional, c.nueva_fecha_entrega, c.nombre_fase
                  FROM cambios_plan c JOIN proyectos p ON p.id = c.proyecto_id
                 WHERE c.estado = 'PROPUESTO' ORDER BY c.created_at LIMIT 10
                """, Map.<String, Object>of(), (r, n) -> new CambioPorAutorizar(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
                r.getBigDecimal(5), r.getObject(6, LocalDate.class), r.getString(7)));
    }

    public List<FaseVencida> fasesVencidas(LocalDate hoy) {
        return jdbc.query("""
                SELECT p.codigo, p.nombre, f.nombre, f.plazo_aprobacion, """ + PO + """
                  FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id
                 WHERE f.estado = 'EN_REVISION' AND p.estado = 'ACTIVO' AND f.plazo_aprobacion < :hoy
                 ORDER BY f.plazo_aprobacion LIMIT 10
                """, new MapSqlParameterSource("hoy", hoy), (r, n) -> new FaseVencida(r.getString(1), r.getString(2), r.getString(3),
                r.getObject(4, LocalDate.class), r.getString(5)));
    }

    /** Proyectos EN_GARANTIA cuya garantía ya se cumplió y siguen abiertos por correcciones (RN-31, D-30). */
    public List<GarantiaCumplida> garantiasCumplidas(LocalDate hoy) {
        return jdbc.query("""
                SELECT p.codigo, p.nombre, p.garantia_hasta,
                       (SELECT count(*) FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
                         WHERE f.proyecto_id = p.id AND s.tipo = 'CORRECCION' AND s.estado IN ('PENDIENTE', 'EN_CURSO'))
                  FROM proyectos p WHERE p.estado = 'EN_GARANTIA' AND p.garantia_hasta < :hoy ORDER BY p.garantia_hasta LIMIT 10
                """, new MapSqlParameterSource("hoy", hoy), (r, n) -> new GarantiaCumplida(r.getString(1), r.getString(2),
                r.getObject(3, LocalDate.class), r.getInt(4)));
    }

    /** Equipos activos con sus proyectos activos (RN-05: máximo 3 por equipo, o el cupo que tenga). */
    public List<Equipo> cargaDeEquipos() {
        return jdbc.query("""
                SELECT e.id, coalesce((SELECT d.nombre FROM integrantes_equipo i JOIN desarrolladores d ON d.id = i.desarrollador_id
                                        WHERE i.equipo_id = e.id AND i.rol = 'PRODUCT_OWNER' LIMIT 1), 'Sin Product Owner'),
                       (SELECT count(*) FROM proyectos p WHERE p.equipo_id = e.id AND p.estado = 'ACTIVO'), e.max_proyectos
                  FROM equipos e WHERE e.activo ORDER BY 3 DESC, 2
                """, Map.<String, Object>of(), (r, n) -> new Equipo(r.getObject(1, UUID.class), r.getString(2), r.getInt(3), r.getInt(4)));
    }

    /** Los proyectos activos según dónde van: «Plan por aprobar» primero y luego la fase actual, en el orden del plan. */
    public List<ProyectosEnFase> proyectosPorFase() {
        return jdbc.query("""
                SELECT CASE WHEN p.estado_plan <> 'APROBADO' THEN 'Plan por aprobar' ELSE coalesce(fa.nombre, 'Sin fase en curso') END AS nombre,
                       min(CASE WHEN p.estado_plan <> 'APROBADO' THEN 0 ELSE coalesce(fa.orden, 99) END) AS orden, count(*) AS total
                  FROM proyectos p
                  LEFT JOIN LATERAL (SELECT f.nombre, f.orden FROM proyecto_fases f WHERE f.proyecto_id = p.id
                                      AND f.estado NOT IN ('APROBADA', 'QUITADA') ORDER BY f.orden LIMIT 1) fa ON true
                 WHERE p.estado = 'ACTIVO' GROUP BY 1 ORDER BY 2, 1
                """, Map.<String, Object>of(), (r, n) -> new ProyectosEnFase(r.getString("nombre"), r.getInt("total")));
    }

    /** Proyectos entregados en un año (la garantía empieza al aprobar la última fase) y cuántos llegaron a la fecha de entrega vigente. */
    public int[] entregasATiempo(int anio) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE (garantia_hasta - 30) <= fecha_estimada_entrega), count(*) FROM proyectos
                 WHERE estado IN ('EN_GARANTIA', 'FINALIZADO') AND garantia_hasta IS NOT NULL AND extract(year FROM garantia_hasta - 30) = :anio
                """, new MapSqlParameterSource("anio", anio), (r, n) -> new int[] { r.getInt(1), r.getInt(2) });
    }

    // ------------------------------------------------------------------------------------------- Actividad

    public List<Actividad> actividadReciente(int limite) {
        return jdbc.query("""
                SELECT a.created_at, coalesce(u.nombre, 'Sin identificar'), a.categoria, a.accion, a.entidad, a.entidad_id
                  FROM auditoria a LEFT JOIN usuarios u ON u.id = a.usuario_id ORDER BY a.id DESC LIMIT :limite
                """, new MapSqlParameterSource("limite", limite), (r, n) -> new Actividad(r.getTimestamp(1).toInstant(), r.getString(2),
                r.getString(3), r.getString(4), r.getString(5), r.getString(6)));
    }
}
