package co.coretech.portal.catalogo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que el catálogo necesita saber de las citas de cada servicio. La implementa el módulo agenda, dueño
 * de la tabla citas: así el catálogo no depende de agenda (agenda sí depende del catálogo).
 */
public interface CitasDeServicios {

    /** Citas agendadas desde {@code desde}, por servicio ("26 citas en 6 meses"). */
    Map<UUID, Long> contarDesde(Instant desde);

    /** RN-21: un servicio con citas no se elimina, solo se oculta. */
    boolean tieneCitas(UUID servicioId);
}
