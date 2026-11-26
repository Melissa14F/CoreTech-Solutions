package co.coretech.portal.acceso.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.coretech.portal.acceso.repository.TokenRecuperacionRepositorio;

/** Tareas diarias del módulo acceso (conservación de datos, RNF-09, Ley 1581). */
@Component
public class TareasAcceso {

    private static final Logger LOG = LoggerFactory.getLogger(TareasAcceso.class);

    private final TokenRecuperacionRepositorio tokens;
    private final Clock reloj;

    TareasAcceso(TokenRecuperacionRepositorio tokens, Clock reloj) {
        this.tokens = tokens;
        this.reloj = reloj;
    }

    /** Todos los días a las 3:00 a. m. de Colombia: borra los enlaces de recuperación usados o vencidos. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Bogota")
    public int borrarEnlacesRecuperacionVencidos() {
        int borrados = tokens.borrarUsadosOVencidos(Instant.now(reloj));
        if (borrados > 0) {
            LOG.info("Se borraron {} enlaces de recuperación usados o vencidos", borrados);
        }
        return borrados;
    }
}
