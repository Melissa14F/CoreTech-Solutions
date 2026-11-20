package co.coretech.portal.comunicacion.aplicacion;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.compartido.aplicacion.CorreoSistemaRevocado;
import co.coretech.portal.comunicacion.AvisoSolicitado;

/**
 * Avisos del correo del sistema (D-51). Si Google retira el permiso de Gmail, los administradores lo ven en la campana
 * con el botón para reconectar: por correo no se les puede avisar, porque justo ese es el correo que dejó de funcionar.
 */
@Component
class AvisosDeCorreoSistema {

    static final String TIPO = "CORREO_DESCONECTADO";
    static final String TITULO = "Hay que reconectar el correo del sistema";
    static final String ENLACE = "/admin/correo";

    private final DirectorioUsuarios usuarios;
    private final ApplicationEventPublisher eventos;

    AvisosDeCorreoSistema(DirectorioUsuarios usuarios, ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.eventos = eventos;
    }

    @EventListener
    void permisoRetirado(CorreoSistemaRevocado evento) {
        List<UUID> administradores = usuarios.administradoresActivos();
        if (administradores.isEmpty()) {
            return;
        }
        eventos.publishEvent(new AvisoSolicitado(administradores, TIPO, TITULO,
                "Google retiró el permiso de " + evento.cuenta() + " para enviar correos. Mientras tanto los correos esperan. "
                        + "Entra a «Correo del sistema» y pulsa «Volver a conectar».",
                ENLACE));
    }
}
