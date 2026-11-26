package co.coretech.portal.proyectos.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.proyectos.domain.CambioPlan;
import co.coretech.portal.proyectos.domain.EstadoCambioPlan;
import co.coretech.portal.proyectos.domain.TipoCambioPlan;

/**
 * Cambios de un plan aprobado con Spring Data JPA (HU-30, RN-30, D-32). Cada cambio de estado es una consulta
 * {@code UPDATE … WHERE estado = <esperado>}: devuelve {@code false} si no cambió ninguna fila (otra persona respondió antes),
 * y el servicio responde 409. Las lecturas con varios JOIN siguen en {@code CambiosPlanJdbc}.
 */
public interface CambioPlanRepositorio extends JpaRepository<CambioPlan, UUID> {

    /** Crea el cambio PROPUESTO (el equipo lo propone y la administración lo autoriza después). Devuelve su id. */
    @Transactional
    default UUID insertar(UUID proyectoId, TipoCambioPlan tipo, String nombreFase, Integer posicion, boolean apruebaCliente, String pasosJson,
            UUID faseId, UUID pasoId, UUID solicitudId, String tituloPaso, String explicacionPaso, String motivo, LocalDate nuevaEntrega,
            BigDecimal valorAdicional, UUID propuestoPor, Instant ahora) {
        CambioPlan cambio = CambioPlan.proponer(proyectoId, tipo, nombreFase, posicion, apruebaCliente,
                pasosJson, faseId, pasoId, solicitudId, tituloPaso, explicacionPaso, motivo, nuevaEntrega, valorAdicional, propuestoPor,
                ahora);
        saveAndFlush(cambio);
        return cambio.getId();
    }

    /** El cambio de fecha que la administración crea al editar un plan aprobado: nace AUTORIZADO y lo acepta el cliente (D-33). */
    @Transactional
    default UUID insertarCambioDeFecha(UUID proyectoId, LocalDate nuevaFecha, String motivo, UUID administradorId, Instant ahora) {
        CambioPlan cambio = CambioPlan.cambioDeFechaAutorizado(proyectoId, nuevaFecha, motivo, administradorId, ahora);
        saveAndFlush(cambio);
        return cambio.getId();
    }

    /** ¿Hay un cambio PROPUESTO o AUTORIZADO que aún espera respuesta? */
    default boolean hayCambioDePlanAbierto(UUID proyectoId) {
        return hayConEstado(proyectoId, List.of(EstadoCambioPlan.PROPUESTO, EstadoCambioPlan.AUTORIZADO));
    }

    @Query("select count(c) > 0 from CambioPlan c where c.proyectoId = :proyectoId and c.estado in :estados")
    boolean hayConEstado(@Param("proyectoId") UUID proyectoId, @Param("estados") List<EstadoCambioPlan> estados);

    // ---- Responder: una sentencia UPDATE condicionada por el estado actual

    /** PROPUESTO → AUTORIZADO, o → ACEPTADO si el cambio se aplica al autorizarlo (QUITAR_PASO en una fase del equipo). */
    @Transactional
    default boolean autorizar(UUID id, EstadoCambioPlan nuevoEstado, UUID administradorId, Instant ahora) {
        return autorizarCambio(id, nuevoEstado, administradorId, ahora, EstadoCambioPlan.PROPUESTO) == 1;
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update CambioPlan c set c.estado = :nuevo, c.autorizadoPor = :administrador, c.autorizadoEn = :ahora
             where c.id = :id and c.estado = :esperado
            """)
    int autorizarCambio(@Param("id") UUID id, @Param("nuevo") EstadoCambioPlan nuevo, @Param("administrador") UUID administrador,
            @Param("ahora") Instant ahora, @Param("esperado") EstadoCambioPlan esperado);

    @Transactional
    default boolean rechazarComoAdministrador(UUID id, String motivo) {
        return rechazarCambio(id, EstadoCambioPlan.RECHAZADO_ADMIN, motivo, null, EstadoCambioPlan.PROPUESTO) == 1;
    }

    @Transactional
    default boolean rechazarComoCliente(UUID id, String motivo, Instant ahora) {
        return rechazarCambio(id, EstadoCambioPlan.RECHAZADO_CLIENTE, motivo, ahora, EstadoCambioPlan.AUTORIZADO) == 1;
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update CambioPlan c set c.estado = :nuevo, c.motivoRechazo = :motivo, c.respondidoEn = :ahora
             where c.id = :id and c.estado = :esperado
            """)
    int rechazarCambio(@Param("id") UUID id, @Param("nuevo") EstadoCambioPlan nuevo, @Param("motivo") String motivo,
            @Param("ahora") Instant ahora, @Param("esperado") EstadoCambioPlan esperado);

    @Transactional
    default boolean aceptar(UUID id, Instant ahora) {
        return aceptarCambio(id, EstadoCambioPlan.ACEPTADO, ahora, EstadoCambioPlan.AUTORIZADO) == 1;
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update CambioPlan c set c.estado = :nuevo, c.respondidoEn = :ahora where c.id = :id and c.estado = :esperado")
    int aceptarCambio(@Param("id") UUID id, @Param("nuevo") EstadoCambioPlan nuevo, @Param("ahora") Instant ahora,
            @Param("esperado") EstadoCambioPlan esperado);
}
