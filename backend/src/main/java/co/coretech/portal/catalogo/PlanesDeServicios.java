package co.coretech.portal.catalogo;

import java.util.Map;
import java.util.UUID;

/**
 * Lo que el catálogo necesita de la plantilla de fases y de los proyectos de cada servicio. La implementa
 * el módulo proyectos, dueño de esas tablas (así el catálogo no depende de proyectos).
 */
public interface PlanesDeServicios {

    /** Número de fases de la plantilla de cada servicio ("5 fases" en la lista de servicios). */
    Map<UUID, Integer> fasesDePlantilla();

    /** RN-21: un servicio con proyectos no se elimina, solo se oculta. */
    boolean tieneProyectos(UUID servicioId);

    /** Borra la plantilla de un servicio que se va a eliminar (en la misma transacción). */
    void eliminarPlantilla(UUID servicioId);
}
