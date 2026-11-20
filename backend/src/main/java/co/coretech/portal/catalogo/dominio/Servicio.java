package co.coretech.portal.catalogo.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Servicio del catálogo (tabla servicios, HU-07, HU-18). Un servicio oculto no aparece en el sitio ni al
 * agendar; uno con citas o proyectos no se elimina, solo se oculta (RN-21).
 */
@Entity
@Table(name = "servicios")
public class Servicio {

    @Id
    private UUID id;

    private String nombre;

    /** Identificador para la URL (?servicio=tienda-en-linea); se fija al crearlo y no cambia. */
    private String slug;

    @Column(name = "descripcion_corta")
    private String descripcionCorta;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(name = "imagen_alt")
    private String imagenAlt;

    /** Posición en el carrusel de la página principal (1, 2, 3…). */
    private short orden;

    private boolean visible;

    @Column(name = "created_at")
    private Instant creadoEn;

    @Column(name = "updated_at")
    private Instant actualizadoEn;

    protected Servicio() {
    }

    public static Servicio nuevo(String nombre, String slug, String descripcionCorta, String imagenUrl, String imagenAlt,
            short orden, boolean visible, Instant ahora) {
        Servicio s = new Servicio();
        s.id = UUID.randomUUID();
        s.nombre = nombre;
        s.slug = slug;
        s.descripcionCorta = descripcionCorta;
        s.imagenUrl = imagenUrl;
        s.imagenAlt = imagenAlt;
        s.orden = orden;
        s.visible = visible;
        s.creadoEn = ahora;
        s.actualizadoEn = ahora;
        return s;
    }

    public void editar(String nombre, String descripcionCorta, String imagenUrl, String imagenAlt, boolean visible, Instant ahora) {
        this.nombre = nombre;
        this.descripcionCorta = descripcionCorta;
        this.imagenUrl = imagenUrl;
        this.imagenAlt = imagenAlt;
        this.visible = visible;
        this.actualizadoEn = ahora;
    }

    public void cambiarVisibilidad(boolean visible, Instant ahora) {
        this.visible = visible;
        this.actualizadoEn = ahora;
    }

    public void ponerEnPosicion(short orden, Instant ahora) {
        this.orden = orden;
        this.actualizadoEn = ahora;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescripcionCorta() {
        return descripcionCorta;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public String getImagenAlt() {
        return imagenAlt;
    }

    public short getOrden() {
        return orden;
    }

    public boolean isVisible() {
        return visible;
    }
}
