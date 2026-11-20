package co.coretech.portal.comunicacion.aplicacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el correo de un aviso en el mismo instante en que se guarda (por ejemplo, la bienvenida al crear la cuenta),
 * sin esperar la pasada de 15 segundos de {@link TareaEnvioCorreos}. Corre en otro hilo y solo después de confirmar
 * la transacción: la petición del usuario nunca espera al servidor de correo, y si falla, la cola reintenta
 * (hasta 3 intentos, RNF-13). Se apaga con {@code coretech.correos.inmediato=false} (las pruebas lo hacen para
 * controlar cuándo corre la cola).
 */
@Component
class EnvioInmediato {

    private final TareaEnvioCorreos cola;
    private final boolean activo;

    EnvioInmediato(TareaEnvioCorreos cola, @Value("${coretech.correos.inmediato:true}") boolean activo) {
        this.cola = cola;
        this.activo = activo;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void alGuardar(AvisosGuardados evento) {
        if (activo) {
            cola.enviarPendientes();
        }
    }
}
