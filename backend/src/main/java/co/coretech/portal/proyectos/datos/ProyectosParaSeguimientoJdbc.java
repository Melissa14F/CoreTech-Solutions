package co.coretech.portal.proyectos.datos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.seguimiento.ProyectosParaSeguimiento;

/** El proyecto de una fase o un paso, para el módulo seguimiento (HU-03, HU-04). Solo lectura, más el bloqueo de la fila. */
@Repository
class ProyectosParaSeguimientoJdbc implements ProyectosParaSeguimiento {

    private static final String BASE = """
            SELECT p.id, p.codigo, p.nombre, p.cliente_id, p.equipo_id, s.nombre, c.codigo, p.estado::text, p.estado_plan::text,
                   p.avance_porcentaje, p.fecha_inicio, p.fecha_estimada_entrega
              FROM proyectos p
              JOIN servicios s ON s.id = p.servicio_id
              LEFT JOIN citas c ON c.id = p.cita_id
            """;

    private final JdbcTemplate jdbc;

    ProyectosParaSeguimientoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Proyecto fila(ResultSet r, int n) throws SQLException {
        return new Proyecto(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getObject(4, UUID.class),
                r.getObject(5, UUID.class), r.getString(6), r.getString(7), r.getString(8), r.getString(9), r.getBigDecimal(10),
                r.getObject(11, LocalDate.class), r.getObject(12, LocalDate.class));
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
}
