package co.coretech.portal.comunicacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas diarias del módulo comunicación (conservación de datos, RNF-09, Ley 1581). */
@Component
class TareasComunicacion {

    private static final Logger LOG = LoggerFactory.getLogger(TareasComunicacion.class);

    private final ServicioNotificaciones notificaciones;

    TareasComunicacion(ServicioNotificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    /** Todos los días a las 3:15 a. m. de Colombia: borra las notificaciones leídas con más de 6 meses. */
    @Scheduled(cron = "0 15 3 * * *", zone = "America/Bogota")
    void borrarNotificacionesLeidasAntiguas() {
        int borradas = notificaciones.borrarLeidasAntiguas();
        if (borradas > 0) {
            LOG.info("Se borraron {} notificaciones leídas con más de 6 meses", borradas);
        }
    }
}
