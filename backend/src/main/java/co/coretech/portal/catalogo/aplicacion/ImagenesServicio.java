package co.coretech.portal.catalogo.aplicacion;

import java.util.List;

/**
 * Imágenes de los servicios (HU-18): las de la galería (archivos de la SPA en /img/servicios/) o una que
 * sube el administrador, guardada en el almacén y servida por la API.
 */
public final class ImagenesServicio {

    private ImagenesServicio() {
    }

    /** Galería del prototipo (admin/servicios.html); viven en prototipo/frontend/public/img/servicios/. */
    public static final List<String> GALERIA = List.of(
            "img/servicios/tienda-en-linea.webp",
            "img/servicios/web-corporativa.webp",
            "img/servicios/app-movil.webp",
            "img/servicios/sistema-inventario.webp",
            "img/servicios/integracion-pagos.webp");

    /** Ruta pública de una imagen subida: GET /api/v1/servicios/imagenes/{nombre}. */
    public static final String PREFIJO_SUBIDA = "/api/v1/servicios/imagenes/";

    /** Carpeta del almacén de archivos. */
    public static final String CARPETA = "servicios";

    /** Tope del prototipo: "La imagen pesa más de 2 MB". */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
}
