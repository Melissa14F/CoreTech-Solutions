package co.coretech.portal.proyectos.datos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.FasePlantilla;
import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.PasoPlantilla;

/** Lee y reemplaza la plantilla de fases de un servicio (tablas plantilla_fases y plantilla_pasos). */
@Repository
public class PlantillasJdbc {

    private final JdbcTemplate jdbc;

    PlantillasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FasePlantilla> leer(UUID servicioId) {
        Map<UUID, FasePlantilla> fases = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, orden, nombre, que_es, aprueba_cliente
                  FROM plantilla_fases WHERE servicio_id = ? ORDER BY orden
                """, fila -> {
                    UUID id = fila.getObject("id", UUID.class);
                    fases.put(id, new FasePlantilla(fila.getInt("orden"), fila.getString("nombre"), fila.getString("que_es"),
                            fila.getBoolean("aprueba_cliente"), new ArrayList<>()));
                }, servicioId);
        jdbc.query("""
                SELECT p.plantilla_fase_id, p.orden, p.titulo, p.explicacion
                  FROM plantilla_pasos p JOIN plantilla_fases f ON f.id = p.plantilla_fase_id
                 WHERE f.servicio_id = ? ORDER BY p.orden
                """, fila -> {
                    FasePlantilla fase = fases.get(fila.getObject("plantilla_fase_id", UUID.class));
                    fase.pasos().add(new PasoPlantilla(fila.getInt("orden"), fila.getString("titulo"), fila.getString("explicacion")));
                }, servicioId);
        return new ArrayList<>(fases.values());
    }

    /** Reemplaza toda la plantilla. Debe llamarse dentro de una transacción: la base la revisa al confirmar (RN-28). */
    public void reemplazar(UUID servicioId, UUID usuarioId, List<FasePlantilla> fases) {
        jdbc.update("""
                DELETE FROM plantilla_pasos
                 WHERE plantilla_fase_id IN (SELECT id FROM plantilla_fases WHERE servicio_id = ?)
                """, servicioId);
        jdbc.update("DELETE FROM plantilla_fases WHERE servicio_id = ?", servicioId);
        int orden = 0;
        for (FasePlantilla fase : fases) {
            UUID faseId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO plantilla_fases (id, servicio_id, orden, nombre, que_es, aprueba_cliente, actualizado_por)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, faseId, servicioId, ++orden, fase.nombre(), fase.queEs(), fase.apruebaCliente(), usuarioId);
            int numero = 0;
            for (PasoPlantilla paso : fase.pasos()) {
                jdbc.update("""
                        INSERT INTO plantilla_pasos (plantilla_fase_id, orden, titulo, explicacion) VALUES (?, ?, ?, ?)
                        """, faseId, ++numero, paso.titulo(), paso.explicacion());
            }
        }
    }
}
