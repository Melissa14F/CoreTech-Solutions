package co.coretech.portal.especialidad.datos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.acceso.EquiposDeUsuarios;

/** Equipo que lidera cada Product Owner con cuenta (HU-20). Solo lectura sobre equipos e integrantes. */
@Repository
class EquiposDeUsuariosJdbc implements EquiposDeUsuarios {

    private final JdbcTemplate jdbc;

    EquiposDeUsuariosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Equipo> porProductOwner() {
        Map<UUID, Equipo> equipos = new HashMap<>();
        jdbc.query("""
                SELECT d.usuario_id, e.id, e.max_proyectos
                  FROM integrantes_equipo i
                  JOIN desarrolladores d ON d.id = i.desarrollador_id
                  JOIN equipos e ON e.id = i.equipo_id
                 WHERE i.rol = 'PRODUCT_OWNER' AND d.usuario_id IS NOT NULL AND e.activo
                """, fila -> {
                    equipos.put(fila.getObject(1, UUID.class), new Equipo(fila.getObject(2, UUID.class), fila.getInt(3)));
                });
        return equipos;
    }
}
