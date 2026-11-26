package co.coretech.portal.proyectos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Proyecto de un cliente (tabla proyectos). Los cambios de estado, de equipo y de datos NO se hacen cambiando la entidad: los
 * hace {@code ProyectoRepositorio} con una sola sentencia {@code UPDATE}, como exigen los disparadores de PostgreSQL
 * ({@code fn_validar_estado_plan}, {@code tg_recalcular_avance}: p. ej. reanudar cambia {@code estado} y {@code estado_plan}
 * juntos). Por eso la entidad no tiene métodos que modifiquen su estado.
 * <p>
 * Las fases se cargan de forma perezosa ({@link FetchType#LAZY}): listar proyectos no trae sus fases ni sus pasos.
 */
@Entity
@Table(name = "proyectos", indexes = {
        @Index(name = "proyectos_cliente_id_idx", columnList = "cliente_id"),
        @Index(name = "proyectos_equipo_id_estado_idx", columnList = "equipo_id, estado")
})
public class Proyecto {

    @Id
    private UUID id;

    /** Código visible, por ejemplo P-0024. */
    private String codigo;

    private String nombre;

    private String alcance;

    @Column(name = "cita_id")
    private UUID citaId;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(name = "servicio_id")
    private UUID servicioId;

    @Column(name = "equipo_id")
    private UUID equipoId;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_estimada_entrega")
    private LocalDate fechaEstimadaEntrega;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private EstadoProyecto estado;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado_plan")
    private EstadoPlan estadoPlan;

    @Column(name = "valor_cotizado")
    private BigDecimal valorCotizado;

    @Column(name = "condiciones_cotizacion")
    private String condicionesCotizacion;

    @Column(name = "plan_enviado_at")
    private Instant planEnviadoEn;

    /** Último día para que el cliente responda (3 días hábiles, RN-29). */
    @Column(name = "plan_plazo_respuesta")
    private LocalDate planPlazoRespuesta;

    /** Día en que vence el plan sin respuesta (10 días hábiles, RN-29). */
    @Column(name = "plan_vence_el")
    private LocalDate planVenceEl;

    @Column(name = "plan_aprobado_at")
    private Instant planAprobadoEn;

    @Column(name = "finalizado_at")
    private Instant finalizadoEn;

    /** Lo calcula el disparador tg_recalcular_avance: la aplicación solo lo lee. */
    @Column(name = "avance_porcentaje", insertable = false, updatable = false)
    private BigDecimal avancePorcentaje;

    @Column(name = "garantia_hasta")
    private LocalDate garantiaHasta;

    /** Motivo de una pausa o cancelación; se muestra en el panel del cliente y del equipo. */
    @Column(name = "motivo_estado")
    private String motivoEstado;

    @Column(name = "plan_recordado_at")
    private Instant planRecordadoEn;

    @Column(name = "plan_cancelacion_avisada_at")
    private Instant planCancelacionAvisadaEn;

    /** Se incrementa en cada cambio (control de cambios concurrentes); lo suben las consultas de {@code ProyectoRepositorio}. */
    private int version;

    /** Lo llena la base con su valor por defecto. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant creadoEn;

    /** Lo llena la base al insertar; las consultas de actualización lo cambian. */
    @Column(name = "updated_at", insertable = false)
    private Instant actualizadoEn;

    @OneToMany(mappedBy = "proyecto", fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    private List<ProyectoFase> fases = new ArrayList<>();

    protected Proyecto() {
    }

    /** Proyecto nuevo (HU-02): ACTIVO, con el plan en BORRADOR y 0 % de avance (lo calcula la base). */
    public static Proyecto nuevo(String codigo, String nombre, String alcance, UUID citaId, UUID clienteId, UUID servicioId,
            UUID equipoId, UUID creadoPor, LocalDate inicio, LocalDate entrega) {
        Proyecto p = new Proyecto();
        p.id = UUID.randomUUID();
        p.codigo = codigo;
        p.nombre = nombre;
        p.alcance = alcance;
        p.citaId = citaId;
        p.clienteId = clienteId;
        p.servicioId = servicioId;
        p.equipoId = equipoId;
        p.creadoPor = creadoPor;
        p.fechaInicio = inicio;
        p.fechaEstimadaEntrega = entrega;
        p.estado = EstadoProyecto.ACTIVO;
        p.estadoPlan = EstadoPlan.BORRADOR;
        return p;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getAlcance() {
        return alcance;
    }

    public UUID getCitaId() {
        return citaId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getServicioId() {
        return servicioId;
    }

    public UUID getEquipoId() {
        return equipoId;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaEstimadaEntrega() {
        return fechaEstimadaEntrega;
    }

    public EstadoProyecto getEstado() {
        return estado;
    }

    public EstadoPlan getEstadoPlan() {
        return estadoPlan;
    }

    public BigDecimal getValorCotizado() {
        return valorCotizado;
    }

    public String getCondicionesCotizacion() {
        return condicionesCotizacion;
    }

    public Instant getPlanEnviadoEn() {
        return planEnviadoEn;
    }

    public LocalDate getPlanPlazoRespuesta() {
        return planPlazoRespuesta;
    }

    public LocalDate getPlanVenceEl() {
        return planVenceEl;
    }

    public Instant getPlanAprobadoEn() {
        return planAprobadoEn;
    }

    public Instant getFinalizadoEn() {
        return finalizadoEn;
    }

    public BigDecimal getAvancePorcentaje() {
        return avancePorcentaje;
    }

    public LocalDate getGarantiaHasta() {
        return garantiaHasta;
    }

    public UUID getCreadoPor() {
        return creadoPor;
    }

    public String getMotivoEstado() {
        return motivoEstado;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    /** Las fases del plan en su orden; se cargan la primera vez que se piden (LAZY). */
    public List<ProyectoFase> getFases() {
        return List.copyOf(fases);
    }
}
