package co.coretech.portal.comunicacion.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import co.coretech.portal.acceso.ClienteRegistrado;
import co.coretech.portal.comunicacion.AvisoSolicitado;

/**
 * Avisos que nacen en el módulo acceso. Corre dentro de la transacción del registro y la convierte en un
 * {@link AvisoSolicitado}: si el registro se revierte, no hay bienvenida.
 */
@Component
class AvisosDeAcceso {

    static final String TIPO_BIENVENIDA = "CUENTA_CREADA";
    static final String TITULO_BIENVENIDA = "Te damos la bienvenida a CoreTech Solutions";
    static final String TEXTO_BIENVENIDA = "Tu cuenta de cliente quedó creada en nuestro portal. Desde aquí puedes agendar "
            + "una cita con nuestro equipo y seguir el avance de tus proyectos. Si no creaste esta cuenta, contáctanos "
            + "con los datos de la página principal.";
    static final String ENLACE_BIENVENIDA = "/cliente/agendar";

    private final ApplicationEventPublisher eventos;

    AvisosDeAcceso(ApplicationEventPublisher eventos) {
        this.eventos = eventos;
    }

    /** D-52: bienvenida al cliente que se registra (campana y correo a la dirección con la que se registró). */
    @EventListener
    void clienteRegistrado(ClienteRegistrado evento) {
        eventos.publishEvent(AvisoSolicitado.para(evento.usuarioId(), TIPO_BIENVENIDA, TITULO_BIENVENIDA,
                TEXTO_BIENVENIDA, ENLACE_BIENVENIDA));
    }
}
