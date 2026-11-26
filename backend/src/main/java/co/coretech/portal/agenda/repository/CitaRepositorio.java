package co.coretech.portal.agenda.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.coretech.portal.agenda.domain.Cita;
import co.coretech.portal.agenda.domain.EstadoCita;

/**
 * Los estados van siempre como parámetro, nunca como literal en el JPQL: con el enum de PostgreSQL Hibernate
 * escribiría {@code 'PENDIENTE'::EstadoCita} y la base lo rechaza.
 */
public interface CitaRepositorio extends JpaRepository<Cita, UUID> {

    /** Estados que ocupan una franja (RN-02). */
    List<EstadoCita> OCUPAN_FRANJA = List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);

    /** Código visible siguiente (C-0064) de la secuencia de la base (V2). */
    @Query(value = "SELECT fn_siguiente_codigo_cita()", nativeQuery = true)
    String siguienteCodigo();

    @Query("SELECT c.fechaHora FROM Cita c WHERE c.estado IN :estados AND c.fechaHora >= :desde AND c.fechaHora < :hasta")
    List<Instant> horasConEstado(@Param("estados") Collection<EstadoCita> estados, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /** Horas de las citas que ocupan una franja (PENDIENTE o CONFIRMADA, RN-02) en [desde, hasta). */
    default List<Instant> franjasOcupadas(Instant desde, Instant hasta) {
        return horasConEstado(OCUPAN_FRANJA, desde, hasta);
    }

    @Query("SELECT c.fechaHora FROM Cita c WHERE c.clienteId = :cliente AND c.estado <> :excluido "
            + "AND c.fechaHora >= :desde AND c.fechaHora < :hasta")
    List<Instant> horasDelClienteSin(@Param("cliente") UUID cliente, @Param("excluido") EstadoCita excluido,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Horas de las citas no canceladas de un cliente en [desde, hasta) (RN-03). */
    default List<Instant> franjasDelCliente(UUID cliente, Instant desde, Instant hasta) {
        return horasDelClienteSin(cliente, EstadoCita.CANCELADA, desde, hasta);
    }

    @Query("SELECT COUNT(c) > 0 FROM Cita c WHERE c.fechaHora = :hora AND c.estado IN :estados")
    boolean hayCitaEn(@Param("hora") Instant hora, @Param("estados") Collection<EstadoCita> estados);

    default boolean franjaOcupada(Instant hora) {
        return hayCitaEn(hora, OCUPAN_FRANJA);
    }

    @Query("SELECT COUNT(c) > 0 FROM Cita c WHERE c.clienteId = :cliente AND c.fechaHora = :hora AND c.estado <> :excluido")
    boolean clienteTieneCitaSin(@Param("cliente") UUID cliente, @Param("hora") Instant hora,
            @Param("excluido") EstadoCita excluido);

    default boolean clienteTieneCita(UUID cliente, Instant hora) {
        return clienteTieneCitaSin(cliente, hora, EstadoCita.CANCELADA);
    }

    @Query("SELECT c FROM Cita c WHERE c.estado = :estado")
    List<Cita> deEstado(@Param("estado") EstadoCita estado);

    List<Cita> findByClienteIdOrderByFechaHoraDesc(UUID clienteId);

    Optional<Cita> findByIdAndClienteId(UUID id, UUID clienteId);

    /** Citas en [desde, hasta) para la administración; los filtros de estado y equipo se aplican en el servicio. */
    @Query("SELECT c FROM Cita c WHERE c.fechaHora >= :desde AND c.fechaHora < :hasta ORDER BY c.fechaHora DESC")
    List<Cita> entre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("SELECT c FROM Cita c WHERE c.equipoId = :equipo ORDER BY c.fechaHora DESC")
    List<Cita> deEquipo(@Param("equipo") UUID equipo);

    /** Citas vivas (PENDIENTE o CONFIRMADA) de hoy en adelante, por equipo: la carga que ve el administrador. */
    @Query("SELECT c.equipoId, COUNT(c) FROM Cita c WHERE c.equipoId IS NOT NULL AND c.estado IN :estados "
            + "AND c.fechaHora >= :desde GROUP BY c.equipoId")
    List<Object[]> proximasPorEquipo(@Param("estados") Collection<EstadoCita> estados, @Param("desde") Instant desde);
}
