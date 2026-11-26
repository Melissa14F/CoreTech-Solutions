package co.coretech.portal.proyectos.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.proyectos.domain.EstadoPlan;
import co.coretech.portal.proyectos.domain.EstadoProyecto;
import co.coretech.portal.proyectos.domain.Proyecto;

/**
 * Proyectos con Spring Data JPA (ADR-004): alta, lecturas y cambios de estado, de datos y de equipo.
 * <p>
 * Los cambios son consultas {@code UPDATE} de una sola sentencia y no cambios a la entidad, porque los disparadores de
 * PostgreSQL validan cada sentencia completa: reanudar un plan VENCIDO o RECHAZADO cambia {@code estado} y
 * {@code estado_plan} juntos ({@code fn_validar_estado_plan}). Todas suben {@code version} y {@code updated_at}.
 * <p>
 * Un proyecto nunca se borra (RN-04: se cancela): los métodos de borrado heredados están bloqueados. Las fases y los pasos
 * son LAZY: {@link #findByCodigo} trae solo el proyecto; {@link #findConFasesByCodigo} trae el proyecto y sus fases en una
 * sola consulta, para no hacer una consulta por fase (problema N+1).
 */
public interface ProyectoRepositorio extends JpaRepository<Proyecto, UUID> {

    String NO_SE_BORRA = "Un proyecto no se borra: se cancela (RN-04)";

    /** Cita y código de un proyecto. */
    record CitaYCodigo(UUID citaId, String codigo) {
    }

    // ---- Lecturas

    Optional<Proyecto> findByCodigo(String codigo);

    @EntityGraph(attributePaths = "fases")
    Optional<Proyecto> findConFasesByCodigo(String codigo);

    List<Proyecto> findByClienteIdOrderByCreadoEnDesc(UUID clienteId);

    /** Código del proyecto que nació de la cita; una cita genera como máximo un proyecto (RN-04). */
    @Query("select p.codigo from Proyecto p where p.citaId = :citaId")
    Optional<String> codigoDeCita(@Param("citaId") UUID citaId);

    @Query("select new co.coretech.portal.proyectos.repository.ProyectoRepositorio$CitaYCodigo(p.citaId, p.codigo) from Proyecto p")
    List<CitaYCodigo> citasYCodigos();

    /** Códigos de proyecto por cita (RN-04). */
    default Map<UUID, String> codigosPorCita() {
        Map<UUID, String> codigos = new HashMap<>();
        citasYCodigos().forEach(c -> codigos.put(c.citaId(), c.codigo()));
        return codigos;
    }

    @Query(value = "SELECT fn_siguiente_codigo_proyecto()", nativeQuery = true)
    String siguienteCodigo();

    // ---- Alta

    /** Crea el proyecto ACTIVO con el plan en BORRADOR (HU-02). Devuelve su id. */
    @Transactional
    default UUID insertar(String codigo, String nombre, String alcance, UUID citaId, UUID clienteId, UUID servicioId,
            UUID equipoId, UUID creadoPor, LocalDate inicio, LocalDate entrega) {
        Proyecto proyecto = Proyecto.nuevo(codigo, nombre, alcance, citaId, clienteId, servicioId, equipoId, creadoPor, inicio,
                entrega);
        saveAndFlush(proyecto);
        return proyecto.getId();
    }

    /**
     * Copia la plantilla de fases del servicio como el plan del proyecto (RN-06, RN-27): las fases quedan PENDIENTES (la fase 1
     * empieza cuando el cliente aprueba el plan) y sus pasos, PENDIENTES. Devuelve cuántas fases. Es SQL nativo porque es un
     * {@code INSERT … SELECT} entre tablas: copia las filas dentro de la base sin traerlas a Java.
     */
    @Transactional
    default int copiarPlantilla(UUID proyectoId, UUID servicioId) {
        int fases = copiarFasesDePlantilla(proyectoId, servicioId);
        copiarPasosDePlantilla(proyectoId, servicioId);
        return fases;
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO proyecto_fases (id, proyecto_id, orden, nombre, que_es, aprueba_cliente)
            SELECT gen_random_uuid(), :proyectoId, orden, nombre, que_es, aprueba_cliente
              FROM plantilla_fases WHERE servicio_id = :servicioId
            """, nativeQuery = true)
    int copiarFasesDePlantilla(@Param("proyectoId") UUID proyectoId, @Param("servicioId") UUID servicioId);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO pasos (proyecto_fase_id, orden, titulo, explicacion)
            SELECT pf.id, pp.orden, pp.titulo, pp.explicacion
              FROM plantilla_pasos pp
              JOIN plantilla_fases tf ON tf.id = pp.plantilla_fase_id
              JOIN proyecto_fases pf ON pf.proyecto_id = :proyectoId AND pf.orden = tf.orden
             WHERE tf.servicio_id = :servicioId
            """, nativeQuery = true)
    int copiarPasosDePlantilla(@Param("proyectoId") UUID proyectoId, @Param("servicioId") UUID servicioId);

    // ---- Cambios (una sentencia UPDATE cada uno)

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.nombre = :nombre, p.alcance = :alcance, p.fechaEstimadaEntrega = :entrega,
                   p.actualizadoEn = instant, p.version = p.version + 1
             where p.id = :id
            """)
    int actualizarDatos(@Param("id") UUID id, @Param("nombre") String nombre, @Param("alcance") String alcance,
            @Param("entrega") LocalDate entrega);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update Proyecto p set p.equipoId = :equipoId, p.actualizadoEn = instant, p.version = p.version + 1 where p.id = :id")
    int cambiarEquipo(@Param("id") UUID id, @Param("equipoId") UUID equipoId);

    /** Aplica un cambio de fecha aceptado por el cliente (HU-30). */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update Proyecto p set p.fechaEstimadaEntrega = :entrega, p.actualizadoEn = instant, p.version = p.version + 1 where p.id = :id")
    int actualizarEntrega(@Param("id") UUID id, @Param("entrega") LocalDate entrega);

    /** Pausa o cancela con su motivo (se muestra en el panel del cliente y del equipo). */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.estado = :estado, p.motivoEstado = :motivo, p.actualizadoEn = instant, p.version = p.version + 1
             where p.id = :id
            """)
    int cambiarEstado(@Param("id") UUID id, @Param("estado") EstadoProyecto estado, @Param("motivo") String motivo);

    /**
     * Reanuda (RN-29): el proyecto vuelve a ACTIVO y, si el plan estaba RECHAZADO o VENCIDO, vuelve a BORRADOR en la misma
     * sentencia (así lo exige el disparador fn_validar_estado_plan) con el equipo elegido.
     */
    @Transactional
    default void reanudar(UUID id, UUID equipoId, boolean planVuelveABorrador) {
        if (planVuelveABorrador) {
            reanudarConPlanEnBorrador(id, equipoId, EstadoProyecto.ACTIVO, EstadoPlan.BORRADOR);
        } else {
            reanudarSinTocarElPlan(id, equipoId, EstadoProyecto.ACTIVO);
        }
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.estado = :activo, p.motivoEstado = null, p.equipoId = :equipoId, p.estadoPlan = :borrador,
                   p.planRecordadoEn = null, p.planCancelacionAvisadaEn = null, p.actualizadoEn = instant,
                   p.version = p.version + 1
             where p.id = :id
            """)
    int reanudarConPlanEnBorrador(@Param("id") UUID id, @Param("equipoId") UUID equipoId,
            @Param("activo") EstadoProyecto activo, @Param("borrador") EstadoPlan borrador);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.estado = :activo, p.motivoEstado = null, p.equipoId = :equipoId,
                   p.planRecordadoEn = null, p.planCancelacionAvisadaEn = null, p.actualizadoEn = instant,
                   p.version = p.version + 1
             where p.id = :id
            """)
    int reanudarSinTocarElPlan(@Param("id") UUID id, @Param("equipoId") UUID equipoId, @Param("activo") EstadoProyecto activo);

    // ---- Plan de trabajo y cotización (HU-28, HU-29, RN-27 a RN-29)

    /** Guarda la cotización a medias (borrador). La fecha no se toca si viene vacía: es obligatoria en la tabla. */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.valorCotizado = :valor, p.condicionesCotizacion = :condiciones,
                   p.fechaEstimadaEntrega = coalesce(:entrega, p.fechaEstimadaEntrega), p.actualizadoEn = instant,
                   p.version = p.version + 1
             where p.id = :id
            """)
    int guardarCotizacion(@Param("id") UUID id, @Param("valor") BigDecimal valor, @Param("condiciones") String condiciones,
            @Param("entrega") LocalDate entrega);

    /**
     * Envía el plan: el estado y los datos que exige ck_proyectos_enviado cambian en la misma sentencia. {@code plazo} es el día
     * en que vence el plazo de 3 días hábiles; {@code vence} el de 10 (RN-29).
     */
    @Transactional
    default void enviarPlan(UUID id, BigDecimal valor, String condiciones, LocalDate entrega, LocalDate plazo, LocalDate vence) {
        marcarPlanEnviado(id, EstadoPlan.ENVIADO, valor, condiciones, entrega, plazo, vence);
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.estadoPlan = :enviado, p.valorCotizado = :valor, p.condicionesCotizacion = :condiciones,
                   p.fechaEstimadaEntrega = :entrega, p.planEnviadoEn = instant, p.planPlazoRespuesta = :plazo,
                   p.planVenceEl = :vence, p.planRecordadoEn = null, p.planCancelacionAvisadaEn = null,
                   p.actualizadoEn = instant, p.version = p.version + 1
             where p.id = :id
            """)
    int marcarPlanEnviado(@Param("id") UUID id, @Param("enviado") EstadoPlan enviado, @Param("valor") BigDecimal valor,
            @Param("condiciones") String condiciones, @Param("entrega") LocalDate entrega, @Param("plazo") LocalDate plazo,
            @Param("vence") LocalDate vence);

    /** Aprobado: el plan queda fijo (RN-29). La fase 1 se inicia después (D-26: primero cambia el estado del plan). */
    @Transactional
    default void aprobarPlan(UUID id) {
        marcarPlanAprobado(id, EstadoPlan.APROBADO);
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.estadoPlan = :aprobado, p.planAprobadoEn = instant, p.actualizadoEn = instant,
                   p.version = p.version + 1
             where p.id = :id
            """)
    int marcarPlanAprobado(@Param("id") UUID id, @Param("aprobado") EstadoPlan aprobado);

    /** Pide ajustes, rechaza o vence: cambia solo el estado del plan; el disparador pausa el proyecto si hace falta. */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update Proyecto p set p.estadoPlan = :estadoPlan, p.actualizadoEn = instant, p.version = p.version + 1 where p.id = :id")
    int cambiarEstadoPlan(@Param("id") UUID id, @Param("estadoPlan") EstadoPlan estadoPlan);

    /** El recordatorio de la propuesta pendiente ya salió: no vuelve a salir hasta el próximo envío del plan. */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update Proyecto p set p.planRecordadoEn = instant where p.id = :id")
    int marcarRecordado(@Param("id") UUID id);

    /** El aviso de los 30 días a la administración ya salió: no se repite. */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("update Proyecto p set p.planCancelacionAvisadaEn = instant where p.id = :id")
    int marcarCancelacionAvisada(@Param("id") UUID id);

    // ---- Garantía y cierre (RN-13, RN-31)

    /** El proyecto ACTIVO pasa a EN_GARANTIA hasta la fecha dada; si ya no está ACTIVO, no cambia nada. */
    @Transactional
    default void iniciarGarantia(UUID id, LocalDate garantiaHasta) {
        pasarAGarantia(id, garantiaHasta, EstadoProyecto.EN_GARANTIA, EstadoProyecto.ACTIVO);
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.estado = :garantia, p.garantiaHasta = :hasta, p.actualizadoEn = instant, p.version = p.version + 1
             where p.id = :id and p.estado = :activo
            """)
    int pasarAGarantia(@Param("id") UUID id, @Param("hasta") LocalDate hasta, @Param("garantia") EstadoProyecto garantia,
            @Param("activo") EstadoProyecto activo);

    /** El proyecto EN_GARANTIA pasa a FINALIZADO; si ya no está en garantía, no cambia nada. */
    @Transactional
    default void finalizar(UUID id, Instant cuando) {
        pasarAFinalizado(id, cuando, EstadoProyecto.FINALIZADO, EstadoProyecto.EN_GARANTIA);
    }

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update Proyecto p set p.estado = :finalizado, p.finalizadoEn = :cuando, p.actualizadoEn = instant,
                   p.version = p.version + 1
             where p.id = :id and p.estado = :garantia
            """)
    int pasarAFinalizado(@Param("id") UUID id, @Param("cuando") Instant cuando, @Param("finalizado") EstadoProyecto finalizado,
            @Param("garantia") EstadoProyecto garantia);

    // ---- Borrado bloqueado

    @Override
    default void delete(Proyecto entidad) {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }

    @Override
    default void deleteById(UUID id) {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }

    @Override
    default void deleteAll(Iterable<? extends Proyecto> entidades) {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }

    @Override
    default void deleteAllInBatch() {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }

    @Override
    default void deleteAllInBatch(Iterable<Proyecto> entidades) {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) {
        throw new UnsupportedOperationException(NO_SE_BORRA);
    }
}
