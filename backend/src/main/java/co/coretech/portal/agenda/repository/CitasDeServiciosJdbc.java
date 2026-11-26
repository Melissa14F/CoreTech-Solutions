package co.coretech.portal.agenda.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.catalogo.CitasDeServicios;

/** Citas por servicio para el catálogo (HU-18, RN-21). Consultas de solo lectura sobre la tabla citas. */
@Repository
class CitasDeServiciosJdbc implements CitasDeServicios {

    private final JdbcTemplate jdbc;

    CitasDeServiciosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Long> contarDesde(Instant desde) {
        Map<UUID, Long> conteo = new HashMap<>();
        jdbc.query("SELECT servicio_id, count(*) FROM citas WHERE created_at >= ? GROUP BY servicio_id",
                fila -> {
                    conteo.put(fila.getObject(1, UUID.class), fila.getLong(2));
                },
                Timestamp.from(desde));
        return conteo;
    }

    @Override
    public boolean tieneCitas(UUID servicioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM citas WHERE servicio_id = ?)", Boolean.class, servicioId));
    }
}
