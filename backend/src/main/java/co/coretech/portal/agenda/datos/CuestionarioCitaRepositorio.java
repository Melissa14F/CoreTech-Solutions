package co.coretech.portal.agenda.datos;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cuestionario de la reunión (tabla cuestionarios_cita): una vez guardado no se edita ni se borra (RN-24, RNF-02). */
@Repository
public class CuestionarioCitaRepositorio {

    /** Una fila del cuestionario, con los nombres de los enums de la base. */
    public record Cuestionario(UUID id, UUID citaId, String resumen, String descripcionCliente, String presupuesto,
            String fechaDeseada, String idioma, String urgencia, UUID registradoPor, Instant creadoEn) {
    }

    private final JdbcTemplate jdbc;

    CuestionarioCitaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insertar(UUID citaId, String resumen, String descripcion, String presupuesto, String fechaDeseada,
            String idioma, String urgencia, UUID registradoPor, Instant ahora) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cuestionarios_cita (id, cita_id, resumen, descripcion_cliente, presupuesto, fecha_deseada, idioma,
                                                urgencia, registrado_por, created_at)
                VALUES (?, ?, ?, ?, ?::rango_presupuesto, ?, ?::idioma, ?::urgencia, ?, ?)
                """, id, citaId, resumen, descripcion, presupuesto, fechaDeseada, idioma, urgencia, registradoPor,
                java.sql.Timestamp.from(ahora));
        return id;
    }

    public boolean existe(UUID citaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cuestionarios_cita WHERE cita_id = ?)", Boolean.class, citaId));
    }

    public Optional<Cuestionario> deCita(UUID citaId) {
        return jdbc.query("""
                SELECT id, cita_id, resumen, descripcion_cliente, presupuesto::text, fecha_deseada, idioma::text, urgencia::text,
                       registrado_por, created_at
                  FROM cuestionarios_cita WHERE cita_id = ?
                """, (r, n) -> new Cuestionario(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3),
                r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8), r.getObject(9, UUID.class),
                r.getTimestamp(10).toInstant()), citaId).stream().findFirst();
    }

    /** Ids de las citas que ya tienen cuestionario, de entre las pedidas. */
    public java.util.Set<UUID> conCuestionario(java.util.Collection<UUID> citaIds) {
        java.util.Set<UUID> con = new java.util.HashSet<>();
        if (citaIds.isEmpty()) {
            return con;
        }
        String marcas = String.join(",", java.util.Collections.nCopies(citaIds.size(), "?"));
        jdbc.query("SELECT cita_id FROM cuestionarios_cita WHERE cita_id IN (" + marcas + ")",
                r -> { con.add(r.getObject(1, UUID.class)); }, citaIds.toArray());
        return con;
    }
}
