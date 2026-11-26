package co.coretech.portal.agenda.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.acceso.CitasDeUsuarios;

/** Citas de cada cliente para la lista de usuarios (HU-20). Solo lectura sobre la tabla citas. */
@Repository
class CitasDeUsuariosJdbc implements CitasDeUsuarios {

    private final JdbcTemplate jdbc;

    CitasDeUsuariosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Conteo> porCliente() {
        Map<UUID, Conteo> conteo = new HashMap<>();
        jdbc.query("""
                SELECT cliente_id,
                       count(*) FILTER (WHERE estado = 'PENDIENTE'),
                       count(*) FILTER (WHERE estado = 'CONFIRMADA'),
                       count(*) FILTER (WHERE estado = 'ATENDIDA'),
                       count(*) FILTER (WHERE estado = 'CANCELADA')
                  FROM citas GROUP BY cliente_id
                """, fila -> {
                    conteo.put(fila.getObject(1, UUID.class),
                            new Conteo(fila.getInt(2), fila.getInt(3), fila.getInt(4), fila.getInt(5)));
                });
        return conteo;
    }
}
