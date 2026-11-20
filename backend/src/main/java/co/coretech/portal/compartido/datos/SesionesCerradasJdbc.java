package co.coretech.portal.compartido.datos;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tokens de sesión cerrados con "Cerrar sesión" que todavía no vencen (tabla sesiones_cerradas, V7). */
@Repository
public class SesionesCerradasJdbc {

    private final JdbcTemplate jdbc;

    SesionesCerradasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Guarda el token cerrado y, de paso, borra los que ya vencieron (no hace falta recordarlos). */
    public void cerrar(UUID jti, Instant expira, Instant ahora) {
        jdbc.update("DELETE FROM sesiones_cerradas WHERE expira_at < ?", Timestamp.from(ahora));
        jdbc.update("INSERT INTO sesiones_cerradas (jti, expira_at) VALUES (?, ?) ON CONFLICT (jti) DO NOTHING",
                jti, Timestamp.from(expira));
    }

    public boolean estaCerrada(UUID jti) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM sesiones_cerradas WHERE jti = ?)", Boolean.class, jti));
    }
}
