package co.coretech.portal.comunicacion.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.comunicacion.aplicacion.ServicioNotificaciones;
import co.coretech.portal.comunicacion.aplicacion.ServicioNotificaciones.Bandeja;

/**
 * Campana de notificaciones (HU-24). La tienen todos los roles: basta con tener sesión (sin sesión, 401).
 * No pide un permiso de la matriz porque cada usuario solo ve y marca las suyas; la de otro responde 404.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
class ControladorNotificaciones {

    private final ServicioNotificaciones notificaciones;

    ControladorNotificaciones(ServicioNotificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping
    Bandeja bandeja() {
        return notificaciones.bandeja();
    }

    @PatchMapping("/{notificacionId}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void marcarLeida(@PathVariable UUID notificacionId) {
        notificaciones.marcarLeida(notificacionId);
    }

    /** "Marcar todas como leídas". */
    @PatchMapping("/leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void marcarTodasLeidas() {
        notificaciones.marcarTodasLeidas();
    }
}
