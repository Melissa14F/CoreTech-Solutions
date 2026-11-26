package co.coretech.portal.proyectos.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Paso de una fase (tabla pasos): vista de LECTURA. Un paso HECHO es inmutable también en la base (disparador
 * {@code fn_bloquear_cambios}); el que lo marca es {@code SeguimientoJdbc}.
 */
@Entity
@Immutable
@Table(name = "pasos", indexes = {
        @Index(name = "pasos_proyecto_fase_id_orden_idx", columnList = "proyecto_fase_id, orden")
})
public class Paso {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_fase_id")
    private ProyectoFase fase;

    private short orden;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private TipoPaso tipo;

    private String titulo;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private EstadoPaso estado;

    private String nota;

    @Column(name = "hecho_at")
    private Instant hechoEn;

    protected Paso() {
    }

    public UUID getId() {
        return id;
    }

    public ProyectoFase getFase() {
        return fase;
    }

    public short getOrden() {
        return orden;
    }

    public TipoPaso getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public EstadoPaso getEstado() {
        return estado;
    }

    public String getNota() {
        return nota;
    }

    public Instant getHechoEn() {
        return hechoEn;
    }
}
