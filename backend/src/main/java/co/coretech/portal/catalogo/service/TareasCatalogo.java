package co.coretech.portal.catalogo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas diarias del catálogo (conservación de datos, RNF-09). */
@Component
class TareasCatalogo {

    private static final Logger LOG = LoggerFactory.getLogger(TareasCatalogo.class);

    private final ServicioCatalogo catalogo;

    TareasCatalogo(ServicioCatalogo catalogo) {
        this.catalogo = catalogo;
    }

    /** Todos los días a las 3:30 a. m. de Colombia: borra las imágenes subidas que ningún servicio usa. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Bogota")
    void limpiarImagenesSinUso() {
        int borradas = catalogo.limpiarImagenesSinUso();
        if (borradas > 0) {
            LOG.info("Se borraron {} imágenes de servicios que no usaba ningún servicio", borradas);
        }
    }
}
