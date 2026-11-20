package co.coretech.portal.compartido.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import co.coretech.portal.compartido.aplicacion.ServidorCorreo.CorreoNoEnviadoException;

/**
 * Correos directos con reintentos en memoria (D-49): el primer intento sale de inmediato en segundo plano;
 * si falla, se reintenta al minuto y, si vuelve a fallar, a los 5 minutos (3 intentos en total, como la cola
 * de notificaciones, HU-24). El último fallo queda en el log.
 */
@Service
class CorreoSalienteSmtp implements CorreoSaliente {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoSalienteSmtp.class);

    /** Espera antes de cada intento: el primero, de inmediato. */
    static final List<Duration> ESPERAS = List.of(Duration.ZERO, Duration.ofMinutes(1), Duration.ofMinutes(5));

    private final ServidorCorreo servidor;
    private final TaskScheduler planificador;
    private final Clock reloj;
    private final List<Duration> esperas;

    @Autowired
    CorreoSalienteSmtp(ServidorCorreo servidor, TaskScheduler planificador, Clock reloj) {
        this(servidor, planificador, reloj, ESPERAS);
    }

    CorreoSalienteSmtp(ServidorCorreo servidor, TaskScheduler planificador, Clock reloj, List<Duration> esperas) {
        this.servidor = servidor;
        this.planificador = planificador;
        this.reloj = reloj;
        this.esperas = List.copyOf(esperas);
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        programar(Correo.soloTexto(para, asunto, texto), 0);
    }

    private void programar(Correo correo, int intento) {
        Instant cuando = Instant.now(reloj).plus(esperas.get(intento));
        planificador.schedule(() -> intentar(correo, intento), cuando);
    }

    private void intentar(Correo correo, int intento) {
        try {
            servidor.enviar(correo);
        } catch (CorreoNoEnviadoException e) {
            int siguiente = intento + 1;
            if (siguiente < esperas.size()) {
                LOG.info("No se pudo enviar el correo \"{}\" (intento {} de {}); se reintenta: {}",
                        correo.asunto(), siguiente, esperas.size(), e.getMessage());
                programar(correo, siguiente);
            } else {
                LOG.warn("No se pudo enviar el correo \"{}\" después de {} intentos: {}",
                        correo.asunto(), esperas.size(), e.getMessage());
            }
        }
    }
}
