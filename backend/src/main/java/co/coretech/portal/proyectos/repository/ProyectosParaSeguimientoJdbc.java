package co.coretech.portal.proyectos.repository;

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

import co.coretech.portal.seguimiento.ProyectosParaSeguimiento;

/** El proyecto de una fase o un paso, para el módulo seguimiento (HU-03, HU-04, HU-13). Lectura, bloqueo y cambios de garantía. */
@Repository
class ProyectosParaSeguimientoJdbc implements ProyectosParaSeguimiento {

    private static final String BASE = """
            SELECT p.id, p.codigo, p.nombre, p.cliente_id, p.equipo_id, s.nombre, c.codigo, p.estado::text, p.estado_plan::text,
                   p.avance_porcentaje, p.fecha_inicio, p.fecha_estimada_entrega, p.garantia_hasta
              FROM proyectos p
              JOIN servicios s ON s.id = p.servicio_id
              LEFT JOIN citas c ON c.id = p.cita_id
            """;

    private final JdbcTemplate jdbc;
    private final ProyectoRepositorio proyectos;

    ProyectosParaSeguimientoJdbc(JdbcTemplate jdbc, ProyectoRepositorio proyectos) {
        this.jdbc = jdbc;
        this.proyectos = proyectos;
    }

    private static Proyecto fila(ResultSet r, int n) throws SQLException {
        return new Proyecto(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getObject(4, UUID.class),
                r.getObject(5, UUID.class), r.getString(6), r.getString(7), r.getString(8), r.getString(9), r.getBigDecimal(10),
                r.getObject(11, LocalDate.class), r.getObject(12, LocalDate.class), r.getObject(13, LocalDate.class));
    }

    @Override
    public Optional<Proyecto> porCodigo(String codigo) {
        return jdbc.query(BASE + " WHERE p.codigo = ?", ProyectosParaSeguimientoJdbc::fila, codigo).stream().findFirst();
    }

    @Override
    public Optional<Proyecto> porId(UUID proyectoId) {
        return jdbc.query(BASE + " WHERE p.id = ?", ProyectosParaSeguimientoJdbc::fila, proyectoId).stream().findFirst();
    }

    @Override
    public void bloquear(UUID proyectoId) {
        jdbc.query("SELECT id FROM proyectos WHERE id = ? FOR UPDATE", (r, n) -> r.getObject(1, UUID.class), proyectoId);
    }

    @Override
    public void iniciarGarantia(UUID proyectoId, LocalDate garantiaHasta) {
        proyectos.iniciarGarantia(proyectoId, garantiaHasta);
    }

    @Override
    public void finalizar(UUID proyectoId, Instant cuando) {
        proyectos.finalizar(proyectoId, cuando);
    }

    @Override
    public List<UUID> garantiasCumplidas(LocalDate hoy) {
        return jdbc.query("SELECT id FROM proyectos WHERE estado = 'EN_GARANTIA' AND garantia_hasta <= ? ORDER BY codigo",
                (r, n) -> r.getObject(1, UUID.class), hoy);
    }

    @Override
    public List<UUID> cerradosAntesDe(Instant limite) {
        // Un proyecto cancelado no guarda la fecha de cancelación: se usa la de su última actualización (la del cambio de estado)
        return jdbc.query("""
                SELECT id FROM proyectos
                 WHERE (estado = 'FINALIZADO' AND finalizado_at < ?) OR (estado = 'CANCELADO' AND updated_at < ?)
                """, (r, n) -> r.getObject(1, UUID.class), Timestamp.from(limite), Timestamp.from(limite));
    }
}
