package co.coretech.portal.catalogo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que otros módulos pueden saber de los servicios del catálogo (por ejemplo, la agenda al agendar una cita).
 * Es la puerta del módulo catalogo: nadie más lee la tabla servicios ni usa sus entidades.
 */
public interface DirectorioServicios {

    /** Nombre del servicio si existe y está visible; vacío si no existe o está oculto (HU-01: "ya no está disponible"). */
    Optional<String> nombreSiVisible(UUID id);

    /** Nombres de los servicios pedidos (también ocultos: una cita vieja conserva el suyo), por id. */
    Map<UUID, String> nombres(Collection<UUID> ids);
}
