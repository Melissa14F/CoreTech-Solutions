package co.coretech.portal.acceso.datos;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.dominio.TokenRecuperacion;

public interface TokenRecuperacionRepositorio extends JpaRepository<TokenRecuperacion, UUID> {

    Optional<TokenRecuperacion> findByTokenHash(String tokenHash);

    /** Al pedir un enlace nuevo, los anteriores sin usar dejan de existir: solo vale el último. */
    @Modifying
    @Query("DELETE FROM TokenRecuperacion t WHERE t.usuarioId = :usuarioId AND t.usadoEn IS NULL")
    void borrarPendientes(@Param("usuarioId") UUID usuarioId);

    /** Conservación de datos (RNF-09): los enlaces usados o vencidos ya no sirven para nada. */
    @Modifying
    @Transactional
    @Query("DELETE FROM TokenRecuperacion t WHERE t.usadoEn IS NOT NULL OR t.expiraEn < :ahora")
    int borrarUsadosOVencidos(@Param("ahora") Instant ahora);
}
