package co.coretech.portal.proyectos.datos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.acceso.ProyectosDeUsuarios;

/** Proyectos por cliente y por equipo para la lista de usuarios (HU-20). Solo lectura sobre la tabla proyectos. */
@Repository
class ProyectosDeUsuariosJdbc implements ProyectosDeUsuarios {

    private final JdbcTemplate jdbc;

    ProyectosDeUsuariosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Conteo> porCliente() {
        Map<UUID, Conteo> conteo = new HashMap<>();
        jdbc.query("""
                SELECT cliente_id,
                       count(*) FILTER (WHERE estado IN ('ACTIVO', 'PAUSADO', 'EN_GARANTIA')),
                       count(*) FILTER (WHERE estado = 'FINALIZADO')
                  FROM proyectos GROUP BY cliente_id
                """, fila -> {
                    conteo.put(fila.getObject(1, UUID.class), new Conteo(fila.getInt(2), fila.getInt(3)));
                });
        return conteo;
    }

    @Override
    public Map<UUID, Integer> activosPorEquipo() {
        Map<UUID, Integer> conteo = new HashMap<>();
        jdbc.query("SELECT equipo_id, count(*) FROM proyectos WHERE estado = 'ACTIVO' GROUP BY equipo_id",
                fila -> {
                    conteo.put(fila.getObject(1, UUID.class), fila.getInt(2));
                });
        return conteo;
    }
}
