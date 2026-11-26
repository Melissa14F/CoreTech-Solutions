package co.coretech.portal.acceso.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.domain.CuentaExterna;
import co.coretech.portal.acceso.domain.ProveedorExterno;
import co.coretech.portal.acceso.domain.Usuario;

/** Vínculos entre clientes y sus cuentas de Google o Microsoft (tabla cuentas_externas, V9, RF-50). */
public interface CuentaExternaRepositorio extends JpaRepository<CuentaExterna, UUID> {

    @Query("select c.usuario.id from CuentaExterna c where c.proveedor = :proveedor and c.sujeto = :sujeto")
    Optional<UUID> usuarioDe(@Param("proveedor") ProveedorExterno proveedor, @Param("sujeto") String sujeto);

    @Query("select count(c) > 0 from CuentaExterna c where c.usuario.id = :usuarioId and c.proveedor = :proveedor")
    boolean tieneVinculo(@Param("usuarioId") UUID usuarioId, @Param("proveedor") ProveedorExterno proveedor);

    /** Guarda el último ingreso y, si el proveedor dio un correo, lo actualiza; si no hay vínculo, no hace nada. */
    @Modifying
    @Transactional
    @Query("""
            update CuentaExterna c set c.ultimoIngresoEn = :ahora, c.email = coalesce(:email, c.email)
            where c.proveedor = :proveedor and c.sujeto = :sujeto
            """)
    int registrarIngreso(@Param("proveedor") ProveedorExterno proveedor, @Param("sujeto") String sujeto,
            @Param("email") String email, @Param("ahora") Instant ahora);

    /** Al anonimizar a un cliente (Ley 1581): sus vínculos se borran y ya no puede entrar con Google ni Microsoft. */
    @Modifying
    @Transactional
    @Query("delete from CuentaExterna c where c.usuario.id = :usuarioId")
    int borrarDeUsuario(@Param("usuarioId") UUID usuarioId);

    /** Vincula la cuenta del proveedor al usuario. {@code usuario} es una referencia: no se lee de la base. */
    @Transactional
    default void vincular(Usuario usuario, ProveedorExterno proveedor, String sujeto, String email, Instant ahora) {
        saveAndFlush(CuentaExterna.nueva(usuario, proveedor, sujeto, email, ahora));
    }
}
