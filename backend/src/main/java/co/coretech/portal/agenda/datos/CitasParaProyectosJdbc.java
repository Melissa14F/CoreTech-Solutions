package co.coretech.portal.agenda.datos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.proyectos.CitasParaProyectos;

/** Citas para crear proyectos (HU-02, RN-04). Consultas de solo lectura sobre la tabla citas. */
@Repository
class CitasParaProyectosJdbc implements CitasParaProyectos {

    private static final String BASE = """
            SELECT id, codigo, estado::text, cliente_id, servicio_id, fecha_hora, equipo_id FROM citas
            """;

    private final JdbcTemplate jdbc;

    CitasParaProyectosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static CitaOrigen fila(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new CitaOrigen(r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getObject(4, UUID.class),
                r.getObject(5, UUID.class), r.getTimestamp(6).toInstant(), r.getObject(7, UUID.class));
    }

    @Override
    public Optional<CitaOrigen> buscar(UUID citaId) {
        return jdbc.query(BASE + " WHERE id = ?", CitasParaProyectosJdbc::fila, citaId).stream().findFirst();
    }

    @Override
    public List<CitaOrigen> atendidas() {
        return jdbc.query(BASE + " WHERE estado = 'ATENDIDA' ORDER BY fecha_hora DESC", CitasParaProyectosJdbc::fila);
    }
}
