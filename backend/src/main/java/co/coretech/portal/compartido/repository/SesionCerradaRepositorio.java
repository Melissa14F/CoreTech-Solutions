package co.coretech.portal.compartido.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.compartido.domain.SesionCerrada;

/**
 * Tokens de sesión cerrados con «Cerrar sesión» que todavía no vencen (tabla sesiones_cerradas, V7).
 * <p>
 * El {@code INSERT … ON CONFLICT DO NOTHING} es una consulta nativa a propósito: cerrar dos veces la misma sesión (dos
 * pestañas) no debe fallar, y un {@code save()} sobre una clave existente haría un {@code UPDATE} o lanzaría una
 * excepción por clave repetida.
 */
public interface SesionCerradaRepositorio extends JpaRepository<SesionCerrada, UUID> {

    @Modifying
    @Transactional
    @Query("delete from SesionCerrada s where s.expiraEn < :ahora")
    int borrarVencidas(@Param("ahora") Instant ahora);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO sesiones_cerradas (jti, expira_at) VALUES (:jti, :expira) ON CONFLICT (jti) DO NOTHING",
            nativeQuery = true)
    int insertarSiNoExiste(@Param("jti") UUID jti, @Param("expira") Instant expira);

    /** Guarda el token cerrado y, de paso, borra los que ya vencieron (no hace falta recordarlos). */
    @Transactional
    default void cerrar(UUID jti, Instant expira, Instant ahora) {
        borrarVencidas(ahora);
        insertarSiNoExiste(jti, expira);
    }

    default boolean estaCerrada(UUID jti) {
        return existsById(jti);
    }
}
