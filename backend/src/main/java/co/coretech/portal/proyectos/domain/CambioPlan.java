package co.coretech.portal.proyectos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cambio de un plan aprobado (tabla cambios_plan, HU-30, RN-30, D-32): agregar o quitar una fase pendiente, cambiar la fecha,
 * quitar un paso pendiente o cotizar un cambio de alcance. Lo propone el Product Owner, lo autoriza la administración y lo acepta el
 * cliente. El recorrido de estados también lo valida la base ({@code tg_validar_estado_cambio_plan}).
 * <p>
 * Los cambios de estado NO se hacen cambiando la entidad: son consultas {@code UPDATE … WHERE estado = <esperado>} de
 * {@code CambioPlanRepositorio}, de modo que si dos personas responden a la vez solo una gana.
 */
@Entity
@Table(name = "cambios_plan")
public class CambioPlan {

    @Id
    private UUID id;

    @Column(name = "proyecto_id")
    private UUID proyectoId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private TipoCambioPlan tipo;

    @Column(name = "nombre_fase")
    private String nombreFase;

    private Short posicion;

    @Column(name = "aprueba_cliente")
    private boolean apruebaCliente;

    /** Los pasos de la fase nueva, como JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pasos_propuestos", columnDefinition = "jsonb")
    private String pasosPropuestos;

    @Column(name = "proyecto_fase_id")
    private UUID proyectoFaseId;

    @Column(name = "paso_id")
    private UUID pasoId;

    @Column(name = "solicitud_cambio_id")
    private UUID solicitudCambioId;

    @Column(name = "titulo_paso")
    private String tituloPaso;

    @Column(name = "explicacion_paso")
    private String explicacionPaso;

    private String motivo;

    @Column(name = "nueva_fecha_entrega")
    private LocalDate nuevaFechaEntrega;

    @Column(name = "valor_adicional")
    private BigDecimal valorAdicional;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private EstadoCambioPlan estado;

    @Column(name = "propuesto_por")
    private UUID propuestoPor;

    @Column(name = "autorizado_por")
    private UUID autorizadoPor;

    @Column(name = "autorizado_at")
    private Instant autorizadoEn;

    @Column(name = "respondido_at")
    private Instant respondidoEn;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "created_at")
    private Instant creadoEn;

    protected CambioPlan() {
    }

    /** Un cambio PROPUESTO por el equipo; la administración lo autoriza después. */
    public static CambioPlan proponer(UUID proyectoId, TipoCambioPlan tipo, String nombreFase, Integer posicion, boolean apruebaCliente,
            String pasosJson, UUID faseId, UUID pasoId, UUID solicitudId, String tituloPaso, String explicacionPaso, String motivo,
            LocalDate nuevaEntrega, BigDecimal valorAdicional, UUID propuestoPor, Instant ahora) {
        CambioPlan c = new CambioPlan();
        c.id = UUID.randomUUID();
        c.proyectoId = proyectoId;
        c.tipo = tipo;
        c.nombreFase = nombreFase;
        c.posicion = posicion == null ? null : posicion.shortValue();
        c.apruebaCliente = apruebaCliente;
        c.pasosPropuestos = pasosJson;
        c.proyectoFaseId = faseId;
        c.pasoId = pasoId;
        c.solicitudCambioId = solicitudId;
        c.tituloPaso = tituloPaso;
        c.explicacionPaso = explicacionPaso;
        c.motivo = motivo;
        c.nuevaFechaEntrega = nuevaEntrega;
        c.valorAdicional = valorAdicional == null ? BigDecimal.ZERO : valorAdicional;
        c.estado = EstadoCambioPlan.PROPUESTO;
        c.propuestoPor = propuestoPor;
        c.creadoEn = ahora;
        return c;
    }

    /** El cambio de fecha que la administración crea al editar un plan aprobado: nace AUTORIZADO y lo acepta el cliente (D-33). */
    public static CambioPlan cambioDeFechaAutorizado(UUID proyectoId, LocalDate nuevaFecha, String motivo, UUID administradorId,
            Instant ahora) {
        CambioPlan c = proponer(proyectoId, TipoCambioPlan.CAMBIAR_FECHA, null, null, false, null, null, null, null, null, null,
                motivo, nuevaFecha, BigDecimal.ZERO, administradorId, ahora);
        c.estado = EstadoCambioPlan.AUTORIZADO;
        c.autorizadoPor = administradorId;
        c.autorizadoEn = ahora;
        return c;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProyectoId() {
        return proyectoId;
    }

    public TipoCambioPlan getTipo() {
        return tipo;
    }

    public EstadoCambioPlan getEstado() {
        return estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public LocalDate getNuevaFechaEntrega() {
        return nuevaFechaEntrega;
    }

    public BigDecimal getValorAdicional() {
        return valorAdicional;
    }

    public String getPasosPropuestos() {
        return pasosPropuestos;
    }

    public UUID getPropuestoPor() {
        return propuestoPor;
    }

    public UUID getAutorizadoPor() {
        return autorizadoPor;
    }

    public Instant getAutorizadoEn() {
        return autorizadoEn;
    }

    public Instant getRespondidoEn() {
        return respondidoEn;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
