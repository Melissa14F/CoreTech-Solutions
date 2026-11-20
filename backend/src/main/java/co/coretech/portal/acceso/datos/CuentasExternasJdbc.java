package co.coretech.portal.acceso.datos;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import co.coretech.portal.acceso.dominio.ProveedorExterno;

/** Vínculos entre clientes y sus cuentas de Google o Microsoft (tabla cuentas_externas, V9). */
@Repository
public class CuentasExternasJdbc {

    private final JdbcTemplate jdbc;

    CuentasExternasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** El usuario vinculado a esa cuenta del proveedor. */
    public Optional<UUID> usuarioDe(ProveedorExterno proveedor, String sujeto) {
        return jdbc.queryForList("SELECT usuario_id FROM cuentas_externas WHERE proveedor = ? AND sujeto = ?",
                UUID.class, proveedor.name(), sujeto).stream().findFirst();
    }

    public void vincular(UUID usuarioId, ProveedorExterno proveedor, String sujeto, String email, Instant ahora) {
        jdbc.update("""
                INSERT INTO cuentas_externas (usuario_id, proveedor, sujeto, email, created_at, ultimo_ingreso_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, usuarioId, proveedor.name(), sujeto, email, Timestamp.from(ahora), Timestamp.from(ahora));
    }

    public void registrarIngreso(ProveedorExterno proveedor, String sujeto, String email, Instant ahora) {
        jdbc.update("UPDATE cuentas_externas SET ultimo_ingreso_at = ?, email = COALESCE(?, email) WHERE proveedor = ? AND sujeto = ?",
                Timestamp.from(ahora), email, proveedor.name(), sujeto);
    }

    /** Al anonimizar a un cliente (Ley 1581): sus vínculos se borran y ya no puede entrar con Google ni Microsoft. */
    public int borrarDeUsuario(UUID usuarioId) {
        return jdbc.update("DELETE FROM cuentas_externas WHERE usuario_id = ?", usuarioId);
    }

    public boolean tieneVinculo(UUID usuarioId, ProveedorExterno proveedor) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cuentas_externas WHERE usuario_id = ? AND proveedor = ?)",
                Boolean.class, usuarioId, proveedor.name()));
    }
}
