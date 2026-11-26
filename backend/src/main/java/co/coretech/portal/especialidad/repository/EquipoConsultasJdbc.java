package co.coretech.portal.especialidad.repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.especialidad.EquipoConsultas;

/** Consultas de solo lectura sobre equipos, integrantes y la carga de proyectos (conteo en la tabla proyectos). */
@Repository
class EquipoConsultasJdbc implements EquipoConsultas {

    private static final String BASE = """
            SELECT e.id, d.usuario_id, d.nombre,
                   (SELECT count(*) FROM integrantes_equipo x WHERE x.equipo_id = e.id),
                   (SELECT count(*) FROM proyectos p WHERE p.equipo_id = e.id AND p.estado = 'ACTIVO'),
                   e.max_proyectos
              FROM equipos e
              JOIN integrantes_equipo i ON i.equipo_id = e.id AND i.rol = 'PRODUCT_OWNER'
              JOIN desarrolladores d ON d.id = i.desarrollador_id
            """;

    private final JdbcTemplate jdbc;

    EquipoConsultasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Equipo fila(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Equipo(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3), r.getInt(4),
                r.getInt(5), r.getInt(6));
    }

    @Override
    public List<Equipo> activos() {
        return jdbc.query(BASE + " WHERE e.activo AND d.activo AND d.usuario_id IS NOT NULL ORDER BY 5, d.nombre", EquipoConsultasJdbc::fila);
    }

    @Override
    public Optional<Equipo> activo(UUID equipoId) {
        return jdbc.query(BASE + " WHERE e.id = ? AND e.activo AND d.activo AND d.usuario_id IS NOT NULL", EquipoConsultasJdbc::fila, equipoId)
                .stream().findFirst();
    }

    @Override
    public Optional<UUID> equipoDelProductOwner(UUID usuarioId) {
        return jdbc.query(BASE + " WHERE d.usuario_id = ? AND e.activo", (r, n) -> r.getObject(1, UUID.class), usuarioId)
                .stream().findFirst();
    }

    @Override
    public Optional<ProductOwner> productOwner(UUID equipoId) {
        return jdbc.query("""
                SELECT d.usuario_id, d.nombre, d.correo
                  FROM equipos e
                  JOIN integrantes_equipo i ON i.equipo_id = e.id AND i.rol = 'PRODUCT_OWNER'
                  JOIN desarrolladores d ON d.id = i.desarrollador_id
                 WHERE e.id = ? AND e.activo AND d.activo AND d.usuario_id IS NOT NULL
                """, (r, n) -> new ProductOwner(r.getObject(1, UUID.class), r.getString(2), r.getString(3)), equipoId)
                .stream().findFirst();
    }

    @Override
    public List<Integrante> integrantes(UUID equipoId) {
        return jdbc.query("""
                SELECT d.nombre, i.rol::text
                  FROM integrantes_equipo i JOIN desarrolladores d ON d.id = i.desarrollador_id
                 WHERE i.equipo_id = ? AND i.rol <> 'PRODUCT_OWNER' AND d.activo
                 ORDER BY i.desde, d.nombre
                """, (r, n) -> new Integrante(r.getString(1), r.getString(2)), equipoId);
    }

    @Override
    public int cupoDisponible(UUID equipoId) {
        return activo(equipoId).map(e -> Math.max(0, e.maxProyectos() - e.proyectosActivos())).orElse(0);
    }

    @Override
    public void bloquear(UUID equipoId) {
        jdbc.query("SELECT id FROM equipos WHERE id = ? FOR UPDATE", (r, n) -> r.getObject(1, UUID.class), equipoId);
    }

    @Override
    public Map<UUID, Equipo> porIds(Collection<UUID> equipoIds) {
        Map<UUID, Equipo> mapa = new LinkedHashMap<>();
        if (equipoIds.isEmpty()) {
            return mapa;
        }
        String marcas = String.join(",", java.util.Collections.nCopies(equipoIds.size(), "?"));
        jdbc.query(BASE + " WHERE e.id IN (" + marcas + ")", EquipoConsultasJdbc::fila, equipoIds.toArray())
                .forEach(e -> mapa.put(e.equipoId(), e));
        return mapa;
    }
}
