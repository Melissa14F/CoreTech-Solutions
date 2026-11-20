package co.coretech.portal.proyectos.datos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.catalogo.PlanesDeServicios;

/**
 * Plantillas y proyectos por servicio para el catálogo (HU-18, RN-21). La plantilla de fases la mantiene
 * este módulo (HU-27, P15); aquí solo se cuenta y, al eliminar un servicio sin proyectos, se borra.
 */
@Repository
class PlanesDeServiciosJdbc implements PlanesDeServicios {

    private final JdbcTemplate jdbc;

    PlanesDeServiciosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Integer> fasesDePlantilla() {
        Map<UUID, Integer> fases = new HashMap<>();
        jdbc.query("SELECT servicio_id, count(*) FROM plantilla_fases GROUP BY servicio_id",
                fila -> {
                    fases.put(fila.getObject(1, UUID.class), fila.getInt(2));
                });
        return fases;
    }

    @Override
    public boolean tieneProyectos(UUID servicioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM proyectos WHERE servicio_id = ?)", Boolean.class, servicioId));
    }

    @Override
    public void eliminarPlantilla(UUID servicioId) {
        // La revisión de RN-28 (tg_validar_plantilla_*) se salta un servicio que se queda sin fases.
        jdbc.update("""
                DELETE FROM plantilla_pasos
                 WHERE plantilla_fase_id IN (SELECT id FROM plantilla_fases WHERE servicio_id = ?)
                """, servicioId);
        jdbc.update("DELETE FROM plantilla_fases WHERE servicio_id = ?", servicioId);
    }
}
