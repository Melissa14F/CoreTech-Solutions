package co.coretech.portal.compartido.datos;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** La conexión con Gmail del correo del sistema (tabla correo_sistema, V8; una sola fila). */
@Repository
public class CorreoSistemaJdbc {

    private final JdbcTemplate jdbc;

    CorreoSistemaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Conexion(String cuenta, String tokenCifrado, String estado, UUID conectadoPor, Instant conectadoEn,
            String ultimoError, Instant ultimoErrorEn) {

        public boolean activa() {
            return "CONECTADO".equals(estado);
        }
    }

    public Optional<Conexion> obtener() {
        return jdbc.query("""
                SELECT cuenta, token_cifrado, estado, conectado_por, conectado_at, ultimo_error, ultimo_error_at
                  FROM correo_sistema WHERE id = 1
                """, (rs, i) -> new Conexion(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, UUID.class), instante(rs.getTimestamp(5)), rs.getString(6), instante(rs.getTimestamp(7))))
                .stream().findFirst();
    }

    /** Guarda (o reemplaza) la conexión: queda CONECTADO y sin errores. */
    public void guardar(String cuenta, String tokenCifrado, UUID conectadoPor, Instant ahora) {
        jdbc.update("""
                INSERT INTO correo_sistema (id, cuenta, token_cifrado, estado, conectado_por, conectado_at)
                VALUES (1, ?, ?, 'CONECTADO', ?, ?)
                ON CONFLICT (id) DO UPDATE SET cuenta = EXCLUDED.cuenta, token_cifrado = EXCLUDED.token_cifrado,
                  estado = 'CONECTADO', conectado_por = EXCLUDED.conectado_por, conectado_at = EXCLUDED.conectado_at,
                  ultimo_error = NULL, ultimo_error_at = NULL
                """, cuenta, tokenCifrado, conectadoPor, Timestamp.from(ahora));
    }

    public void registrarError(String error, boolean revocado, Instant ahora) {
        jdbc.update("""
                UPDATE correo_sistema SET ultimo_error = ?, ultimo_error_at = ?,
                  estado = CASE WHEN ? THEN 'REVOCADO' ELSE estado END
                 WHERE id = 1
                """, error.length() > 255 ? error.substring(0, 255) : error, Timestamp.from(ahora), revocado);
    }

    public void limpiarError() {
        jdbc.update("UPDATE correo_sistema SET ultimo_error = NULL, ultimo_error_at = NULL WHERE id = 1 AND ultimo_error IS NOT NULL");
    }

    public void borrar() {
        jdbc.update("DELETE FROM correo_sistema WHERE id = 1");
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
