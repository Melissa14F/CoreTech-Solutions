package co.coretech.portal.proyectos.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Fase del plan de trabajo de un proyecto (tabla proyecto_fases): vista de LECTURA (ver {@link Proyecto}). El orden es
 * único por proyecto entre las fases no quitadas (índice parcial {@code ux_pf_orden}). Una fase quitada queda en estado QUITADA; no se borra.
 */
@Entity
@Immutable
@Table(name = "proyecto_fases", indexes = {
        @Index(name = "proyecto_fases_proyecto_id_orden_idx", columnList = "proyecto_id, orden")
})
public class ProyectoFase {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id")
    private Proyecto proyecto;

    private short orden;

    private String nombre;

    @Column(name = "aprueba_cliente")
    private boolean apruebaCliente;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private EstadoFase estado;

    @Column(name = "rondas_ajuste_usadas")
    private short rondasAjusteUsadas;

    @Column(name = "aprobada_at")
    private Instant aprobadaEn;

    @OneToMany(mappedBy = "fase", fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    private List<Paso> pasos = new ArrayList<>();

    protected ProyectoFase() {
    }

    public UUID getId() {
        return id;
    }

    public Proyecto getProyecto() {
        return proyecto;
    }

    public short getOrden() {
        return orden;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isApruebaCliente() {
        return apruebaCliente;
    }

    public EstadoFase getEstado() {
        return estado;
    }

    public short getRondasAjusteUsadas() {
        return rondasAjusteUsadas;
    }

    public Instant getAprobadaEn() {
        return aprobadaEn;
    }

    /** Los pasos de la fase en su orden; se cargan la primera vez que se piden (LAZY). */
    public List<Paso> getPasos() {
        return List.copyOf(pasos);
    }
}
