package co.coretech.portal.catalogo.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un texto editable de la página principal (tabla contenido_sitio, HU-19): su valor y el largo permitido.
 * Las filas las crea la migración V5; el administrador solo cambia el valor.
 */
@Entity
@Table(name = "contenido_sitio")
public class ContenidoSitio {

    @Id
    private String clave;

    private String seccion;

    private String valor;

    @Column(name = "min_longitud")
    private short minLongitud;

    @Column(name = "max_longitud")
    private short maxLongitud;

    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    @Column(name = "updated_at")
    private Instant actualizadoEn;

    protected ContenidoSitio() {
    }

    public void publicar(String nuevoValor, UUID usuario, Instant ahora) {
        this.valor = nuevoValor;
        this.actualizadoPor = usuario;
        this.actualizadoEn = ahora;
    }

    public String getClave() {
        return clave;
    }

    public String getSeccion() {
        return seccion;
    }

    public String getValor() {
        return valor;
    }

    public int getMinLongitud() {
        return minLongitud;
    }

    public int getMaxLongitud() {
        return maxLongitud;
    }

    public UUID getActualizadoPor() {
        return actualizadoPor;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
