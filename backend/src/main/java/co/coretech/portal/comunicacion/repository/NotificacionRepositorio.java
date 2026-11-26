package co.coretech.portal.comunicacion.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.comunicacion.domain.Notificacion;

public interface NotificacionRepositorio extends JpaRepository<Notificacion, UUID> {

    /** Las del usuario: primero las no leídas y, dentro de cada grupo, de la más nueva a la más vieja. */
    @Query("SELECT n FROM Notificacion n WHERE n.usuarioId = :usuarioId ORDER BY n.leida ASC, n.creadaEn DESC, n.id")
    List<Notificacion> bandeja(@Param("usuarioId") UUID usuarioId, Limit limite);

    long countByUsuarioIdAndLeidaFalse(UUID usuarioId);

    Optional<Notificacion> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") UUID usuarioId);

    /**
     * Correos por enviar ya: sin enviar, con intentos disponibles y cumplida la espera creciente desde que se
     * creó la notificación (el intento 1 de inmediato, el 2 desde el minuto 1 y el 3 desde el minuto 5).
     */
    @Query("""
            SELECT n FROM Notificacion n
            WHERE n.enviadaEmail = false
              AND (n.intentosEnvio = 0
                   OR (n.intentosEnvio = 1 AND n.creadaEn <= :listasSegundo)
                   OR (n.intentosEnvio = 2 AND n.creadaEn <= :listasTercero))
            ORDER BY n.creadaEn, n.id
            """)
    List<Notificacion> correosPorEnviar(@Param("listasSegundo") Instant listasSegundo,
            @Param("listasTercero") Instant listasTercero, Limit limite);

    @Modifying
    @Transactional
    @Query("UPDATE Notificacion n SET n.enviadaEmail = true, n.intentosEnvio = n.intentosEnvio + 1, n.ultimoError = null WHERE n.id = :id")
    void registrarEnvio(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("UPDATE Notificacion n SET n.intentosEnvio = n.intentosEnvio + 1, n.ultimoError = :error WHERE n.id = :id")
    void registrarFallo(@Param("id") UUID id, @Param("error") String error);

    /** Sin correo posible (cuenta desactivada o anonimizada): se dan por agotados los intentos y no se reintenta. */
    @Modifying
    @Transactional
    @Query("UPDATE Notificacion n SET n.intentosEnvio = 3, n.ultimoError = :motivo WHERE n.id = :id")
    void descartarCorreo(@Param("id") UUID id, @Param("motivo") String motivo);

    /** Conservación de datos (RNF-09): las leídas con más de 6 meses. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notificacion n WHERE n.leida = true AND n.creadaEn < :antesDe")
    int borrarLeidasAntesDe(@Param("antesDe") Instant antesDe);
}
