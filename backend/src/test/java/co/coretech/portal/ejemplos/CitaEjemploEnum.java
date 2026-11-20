package co.coretech.portal.ejemplos;

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
 * Ejemplo (solo para pruebas) de cómo se mapea un enum de PostgreSQL con Hibernate 6.
 * Usa una parte de la tabla {@code citas}; la entidad real de la cita va en el módulo agenda (P11).
 * <p>
 * Receta: {@code @Enumerated(EnumType.STRING)} + {@code @JdbcType(PostgreSQLEnumJdbcType.class)}. Sin el
 * {@code @JdbcType}, PostgreSQL rechaza el valor ("column … is of type modalidad but expression is of type
 * character varying"). No hace falta {@code columnDefinition}, aunque el nombre del enum de Java sea distinto
 * del tipo de la base.
 */
@Entity
@Table(name = "citas")
public class CitaEjemploEnum {

    public enum Modalidad { PRESENCIAL, VIRTUAL }

    public enum EstadoCita { PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA }

    @Id
    private UUID id;

    private String codigo;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(name = "servicio_id")
    private UUID servicioId;

    @Column(name = "fecha_hora")
    private Instant fechaHora;

    @Column(name = "descripcion_necesidad")
    private String descripcionNecesidad;

    // Enum de PostgreSQL: STRING + PostgreSQLEnumJdbcType. Sin el @JdbcType, Hibernate envía varchar y
    // PostgreSQL responde "column is of type modalidad but expression is of type character varying".
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private Modalidad modalidad;

    // El nombre del enum de Java no tiene que coincidir con el tipo de la base (EstadoCita / estado_cita):
    // con ddl-auto=validate Hibernate acepta la columna sin columnDefinition (comprobado con Hibernate 6.6).
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private EstadoCita estado;

    protected CitaEjemploEnum() {
    }

    public CitaEjemploEnum(UUID id, String codigo, UUID clienteId, UUID servicioId, Instant fechaHora,
            String descripcionNecesidad, Modalidad modalidad, EstadoCita estado) {
        this.id = id;
        this.codigo = codigo;
        this.clienteId = clienteId;
        this.servicioId = servicioId;
        this.fechaHora = fechaHora;
        this.descripcionNecesidad = descripcionNecesidad;
        this.modalidad = modalidad;
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public Modalidad getModalidad() {
        return modalidad;
    }

    public EstadoCita getEstado() {
        return estado;
    }
}
