package co.coretech.portal.agenda.dominio;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cita con la empresa (tabla citas, HU-01, HU-08). La hora se guarda como instante (UTC) junto con el país y la
 * zona IANA del cliente (RN-23). Solo en PENDIENTE el cliente puede cancelarla (RN-11).
 */
@Entity
@Table(name = "citas")
public class Cita {

    @Id
    private UUID id;

    private String codigo;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(name = "servicio_id")
    private UUID servicioId;

    @Column(name = "fecha_hora")
    private Instant fechaHora;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private Modalidad modalidad;

    @Column(name = "descripcion_necesidad")
    private String descripcionNecesidad;

    private String pais;

    @Column(name = "zona_horaria")
    private String zonaHoraria;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private EstadoCita estado;

    @Column(name = "equipo_id")
    private UUID equipoId;

    @Column(name = "nota_cliente")
    private String notaCliente;

    @Column(name = "nota_asignacion")
    private String notaAsignacion;

    @Column(name = "confirmada_at")
    private Instant confirmadaEn;

    @Column(name = "atendida_at")
    private Instant atendidaEn;

    @Column(name = "enlace_reunion")
    private String enlaceReunion;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "cancelada_por")
    private UUID canceladaPor;

    @Column(name = "cancelada_at")
    private Instant canceladaEn;

    @Column(name = "created_at")
    private Instant creadaEn;

    @Column(name = "updated_at")
    private Instant actualizadaEn;

    protected Cita() {
    }

    public static Cita nueva(String codigo, UUID clienteId, UUID servicioId, Instant fechaHora, Modalidad modalidad,
            String descripcionNecesidad, String pais, String zonaHoraria, Instant ahora) {
        Cita c = new Cita();
        c.id = UUID.randomUUID();
        c.codigo = codigo;
        c.clienteId = clienteId;
        c.servicioId = servicioId;
        c.fechaHora = fechaHora;
        c.modalidad = modalidad;
        c.descripcionNecesidad = descripcionNecesidad;
        c.pais = pais;
        c.zonaHoraria = zonaHoraria;
        c.estado = EstadoCita.PENDIENTE;
        c.creadaEn = ahora;
        c.actualizadaEn = ahora;
        return c;
    }

    /** RN-11: el servicio ya comprobó que está PENDIENTE y es del cliente. */
    public void cancelar(UUID quien, String motivo, Instant ahora) {
        this.estado = EstadoCita.CANCELADA;
        this.canceladaPor = quien;
        this.motivoCancelacion = motivo;
        this.canceladaEn = ahora;
        this.actualizadaEn = ahora;
    }

    /** HU-09: el administrador asigna o reasigna el equipo (el servicio ya validó el estado y que el equipo esté activo). */
    public void asignar(UUID equipo, String nota, Instant ahora) {
        this.equipoId = equipo;
        this.notaAsignacion = nota;
        this.actualizadaEn = ahora;
    }

    /** HU-10, RN-12: el servicio ya comprobó que está PENDIENTE y el enlace si es virtual. */
    public void confirmar(String enlace, String nota, Instant ahora) {
        this.estado = EstadoCita.CONFIRMADA;
        this.enlaceReunion = enlace;
        this.notaCliente = nota;
        this.confirmadaEn = ahora;
        this.actualizadaEn = ahora;
    }

    /** Una cita confirmada que cambia de equipo vuelve a PENDIENTE: el nuevo Product Owner la confirma con su propio enlace. */
    public void reabrir(Instant ahora) {
        this.estado = EstadoCita.PENDIENTE;
        this.enlaceReunion = null;
        this.notaCliente = null;
        this.confirmadaEn = null;
        this.actualizadaEn = ahora;
    }

    /** RN-24: solo se llama junto con el cuestionario. */
    public void atender(Instant ahora) {
        this.estado = EstadoCita.ATENDIDA;
        this.atendidaEn = ahora;
        this.actualizadaEn = ahora;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getServicioId() {
        return servicioId;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public Modalidad getModalidad() {
        return modalidad;
    }

    public String getDescripcionNecesidad() {
        return descripcionNecesidad;
    }

    public String getPais() {
        return pais;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public String getEnlaceReunion() {
        return enlaceReunion;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public UUID getCanceladaPor() {
        return canceladaPor;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }

    public Instant getCanceladaEn() {
        return canceladaEn;
    }

    public UUID getEquipoId() {
        return equipoId;
    }

    public String getNotaCliente() {
        return notaCliente;
    }

    public String getNotaAsignacion() {
        return notaAsignacion;
    }

    public Instant getConfirmadaEn() {
        return confirmadaEn;
    }

    public Instant getAtendidaEn() {
        return atendidaEn;
    }
}
