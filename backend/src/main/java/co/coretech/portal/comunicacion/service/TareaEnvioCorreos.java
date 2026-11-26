package co.coretech.portal.comunicacion.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.compartido.service.PlantillasCorreo;
import co.coretech.portal.compartido.service.ServidorCorreo;
import co.coretech.portal.compartido.service.ServidorCorreo.CorreoSinConexionException;
import co.coretech.portal.comunicacion.repository.NotificacionRepositorio;
import co.coretech.portal.comunicacion.domain.Notificacion;

/**
 * Cola de correos de las notificaciones (HU-24, RNF-13). Cada 15 segundos envía las pendientes con la plantilla
 * HTML; si el servidor de correo falla, suma el intento y guarda el error. Hasta 3 intentos con espera creciente:
 * el primero de inmediato, el segundo desde el minuto 1 y el tercero desde el minuto 5 (contados desde que se
 * creó la notificación). La notificación interna se ve igual aunque el correo no salga.
 * <p>
 * Nunca corre dentro de la petición del usuario. Cada notificación se marca en su propia transacción,
 * después de hablar con el servidor de correo (no se tiene una transacción abierta mientras se espera).
 */
@Component
public class TareaEnvioCorreos {

    private static final Logger LOG = LoggerFactory.getLogger(TareaEnvioCorreos.class);

    static final Duration ESPERA_SEGUNDO_INTENTO = Duration.ofMinutes(1);
    static final Duration ESPERA_TERCER_INTENTO = Duration.ofMinutes(5);
    /** Cuántas envía en cada pasada; las demás quedan para la siguiente (15 s después). */
    static final int POR_PASADA = 50;
    static final String SIN_CORREO = "La cuenta está desactivada o anonimizada: no se envía el correo";
    private static final int MAX_ERROR = 255;

    private final NotificacionRepositorio notificaciones;
    private final DirectorioUsuarios usuarios;
    private final PlantillasCorreo plantillas;
    private final ServidorCorreo servidor;
    private final Clock reloj;
    private volatile boolean sinConexionAvisado;

    TareaEnvioCorreos(NotificacionRepositorio notificaciones, DirectorioUsuarios usuarios, PlantillasCorreo plantillas,
            ServidorCorreo servidor, Clock reloj) {
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
        this.plantillas = plantillas;
        this.servidor = servidor;
        this.reloj = reloj;
    }

    /**
     * Cada 15 segundos (RNF-13: el 95 % sale en menos de un minuto). En las pruebas se apaga con
     * {@code coretech.correos.cron: "-"} y se llama a mano.
     *
     * @return cuántos correos se enviaron en esta pasada
     */
    @Scheduled(cron = "${coretech.correos.cron:*/15 * * * * *}", zone = "America/Bogota")
    // synchronized: la pasada programada y el envío inmediato (EnvioInmediato) no deben mandar dos veces el mismo
    // correo; es un solo servidor, como el resto de lo que vive en memoria.
    public synchronized int enviarPendientes() {
        Instant ahora = Instant.now(reloj);
        List<Notificacion> pendientes = notificaciones.correosPorEnviar(
                ahora.minus(ESPERA_SEGUNDO_INTENTO), ahora.minus(ESPERA_TERCER_INTENTO), Limit.of(POR_PASADA));
        int enviados = 0;
        for (Notificacion n : pendientes) {
            try {
                if (enviar(n)) {
                    enviados++;
                }
            } catch (CorreoSinConexionException e) {
                // El correo del sistema no está conectado (D-51): no se gastan intentos; los correos esperan a que
                // un administrador conecte Gmail. Se avisa en el log una sola vez hasta que vuelva a funcionar.
                if (!sinConexionAvisado) {
                    LOG.warn("Los correos esperan: {}", e.getMessage());
                    sinConexionAvisado = true;
                }
                return enviados;
            }
        }
        if (enviados > 0) {
            sinConexionAvisado = false;
        }
        return enviados;
    }

    private boolean enviar(Notificacion n) {
        ContactoCorreo contacto = usuarios.contactoCorreo(n.getUsuarioId()).orElse(null);
        if (contacto == null) {
            notificaciones.descartarCorreo(n.getId(), SIN_CORREO);
            return false;
        }
        try {
            servidor.enviar(plantillas.aviso(contacto.email(), contacto.nombre(), n.getTitulo(), n.getMensaje(), n.getEnlace()));
            notificaciones.registrarEnvio(n.getId());
            return true;
        } catch (CorreoSinConexionException e) {
            throw e;
        } catch (RuntimeException e) {
            // CorreoNoEnviadoException (falla el servidor de correo) o un error al armar el correo: cuenta como
            // intento, para que una notificación dañada no frene la cola.
            int intento = n.getIntentosEnvio() + 1;
            notificaciones.registrarFallo(n.getId(), recortar(e.getMessage()));
            if (intento < Notificacion.MAX_INTENTOS) {
                LOG.info("No se pudo enviar el correo de la notificación {} (intento {} de {}); se reintentará: {}",
                        n.getId(), intento, Notificacion.MAX_INTENTOS, e.getMessage());
            } else {
                LOG.warn("No se pudo enviar el correo de la notificación {} después de {} intentos: {}",
                        n.getId(), Notificacion.MAX_INTENTOS, e.getMessage());
            }
            return false;
        }
    }

    private static String recortar(String error) {
        String texto = error == null || error.isBlank() ? "Error desconocido del servidor de correo" : error;
        return texto.length() <= MAX_ERROR ? texto : texto.substring(0, MAX_ERROR);
    }
}
