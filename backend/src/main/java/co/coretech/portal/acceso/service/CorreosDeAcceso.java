package co.coretech.portal.acceso.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import co.coretech.portal.compartido.service.CorreoSaliente;

/**
 * Correos del módulo acceso. Salen solo si la transacción se confirmó: si algo falla, no llega
 * un enlace que no existe.
 */
@Component
class CorreosDeAcceso {

    private final CorreoSaliente correo;

    CorreosDeAcceso(CorreoSaliente correo) {
        this.correo = correo;
    }

    @TransactionalEventListener
    void enlaceRecuperacion(EnlaceRecuperacionEmitido evento) {
        correo.enviar(evento.email(), "Restablece tu contraseña de CoreTech", """
                Hola, %s:

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en CoreTech Solutions.
                Para crear una nueva, abre este enlace:

                %s

                El enlace vale 30 minutos y se puede usar una sola vez. Si no lo pediste, ignora este correo:
                tu contraseña sigue igual.

                CoreTech Solutions
                """.formatted(evento.nombre(), evento.enlace()));
    }

    @TransactionalEventListener
    void cuentaCreada(CuentaPersonalCreada evento) {
        correo.enviar(evento.email(), "Tu cuenta en CoreTech Solutions", """
                Hola, %s:

                Te creamos una cuenta en el portal de CoreTech Solutions.

                Correo: %s
                Contraseña temporal: %s

                Ingresa en %s. Al entrar por primera vez te pediremos crear tu propia contraseña.

                CoreTech Solutions
                """.formatted(evento.nombre(), evento.email(), evento.claveTemporal(), evento.urlIngreso()));
    }
}
