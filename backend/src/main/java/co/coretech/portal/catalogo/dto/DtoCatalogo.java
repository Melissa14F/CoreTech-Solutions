package co.coretech.portal.catalogo.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import co.coretech.portal.catalogo.service.ServicioCatalogo.VistaServicio;
import co.coretech.portal.catalogo.service.ServicioContenido;
import co.coretech.portal.catalogo.domain.ContenidoSitio;
import co.coretech.portal.catalogo.domain.Servicio;

/**
 * Cuerpos de /api/v1/servicios y /api/v1/contenido (HU-07, HU-18, HU-19). Las validaciones las hace el
 * servicio, que responde 422 con un error por campo y los mensajes del prototipo.
 */
public final class DtoCatalogo {

    private DtoCatalogo() {
    }

    /** Tarjeta del catálogo público (flujo 1 de docs/04-api.md). */
    public record ServicioPublico(UUID servicioId, String nombre, String slug, String descripcionCorta, String imagenUrl, String imagenAlt) {

        public static ServicioPublico de(Servicio s) {
            return new ServicioPublico(s.getId(), s.getNombre(), s.getSlug(), s.getDescripcionCorta(), s.getImagenUrl(), s.getImagenAlt());
        }
    }

    /** Servicio en el panel del administrador: también los ocultos, con su orden y su uso. */
    public record ServicioGestion(UUID servicioId, String nombre, String slug, String descripcionCorta, String imagenUrl, String imagenAlt,
            int orden, boolean visible, long citasUltimos6Meses, int fasesPlantilla) {

        public static ServicioGestion de(VistaServicio v) {
            Servicio s = v.servicio();
            return new ServicioGestion(s.getId(), s.getNombre(), s.getSlug(), s.getDescripcionCorta(), s.getImagenUrl(),
                    s.getImagenAlt(), s.getOrden(), s.isVisible(), v.citasUltimos6Meses(), v.fasesPlantilla());
        }

        public static List<ServicioGestion> de(List<VistaServicio> vistas) {
            return vistas.stream().map(ServicioGestion::de).toList();
        }
    }

    public record SolicitudServicio(String nombre, String descripcionCorta, String imagenUrl, String imagenAlt, Boolean visible) {
    }

    public record SolicitudVisibilidad(boolean visible) {
    }

    /** -1 sube un lugar en el carrusel; 1 lo baja. */
    public record SolicitudOrden(int direccion) {
    }

    public record RespuestaImagen(String imagenUrl) {
    }

    /** Textos de la página principal: clave → texto. */
    public record ContenidoPublico(Map<String, String> textos) {
    }

    public record TextoEditable(String clave, String seccion, String valor, int minLongitud, int maxLongitud) {

        public static TextoEditable de(ContenidoSitio c) {
            return new TextoEditable(c.getClave(), c.getSeccion(), c.getValor(), c.getMinLongitud(), c.getMaxLongitud());
        }
    }

    public record UltimaPublicacion(Instant fecha, String nombre) {
    }

    public record ContenidoEdicion(List<TextoEditable> textos, UltimaPublicacion ultimaPublicacion) {

        public static ContenidoEdicion de(ServicioContenido.Edicion e) {
            return new ContenidoEdicion(e.textos().stream().map(TextoEditable::de).toList(),
                    e.ultimaPublicacion().map(u -> new UltimaPublicacion(u.fecha(), u.nombre())).orElse(null));
        }
    }

    /** Solo los textos que cambiaron. */
    public record SolicitudPublicar(Map<String, String> textos) {
    }
}
