package co.coretech.portal.compartido.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Inserta en {@code auditoria}. Solo inserta: la tabla rechaza UPDATE y DELETE (tg_auditoria_inmutable).
 * La consulta para el administrador (HU-22) va en el módulo acceso.
 */
@Repository
public class AuditoriaRepositorio {

    private final JdbcTemplate jdbc;

    AuditoriaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fila nueva de la auditoría; {@code cambiosJson} es el JSON de campo → valor anterior y nuevo. */
    public record NuevoRegistro(UUID usuarioId, String categoria, String accion, String entidad, String entidadId,
            String cambiosJson, String ip, String agente, Instant creadoEn) {
    }

    public void insertar(NuevoRegistro r) {
        jdbc.update("""
                INSERT INTO auditoria (usuario_id, categoria, accion, entidad, entidad_id, cambios, ip, agente, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """,
                r.usuarioId(), r.categoria(), r.accion(), r.entidad(), r.entidadId(),
                r.cambiosJson(), r.ip(), r.agente(), Timestamp.from(r.creadoEn()));
    }
}
