package co.coretech.portal.comunicacion.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.comunicacion.service.ServicioMensajes.Hilo;
import co.coretech.portal.comunicacion.service.ServicioMensajes.MensajeVista;
import co.coretech.portal.comunicacion.service.ServicioMensajes;
import co.coretech.portal.comunicacion.dto.DtoMensajes.SolicitudMensaje;

/**
 * Los mensajes de un proyecto (HU-14).
 * <ul>
 *   <li>{@code GET} y {@code POST /proyectos/{codigo}/mensajes}: el hilo y un mensaje nuevo. El cliente envía el {@code tema}
 *       de la lista; el Product Owner responde sin tema (D-41).</li>
 *   <li>{@code PATCH /mensajes/{id}}: solo el autor y solo durante 5 minutos; después, 409.</li>
 * </ul>
 * Los tres piden {@code proyectos.ver_propios} (la tienen el cliente, el Product Owner y el administrador); el servicio decide
 * si la persona participa en el proyecto: un cliente ajeno recibe 404 (RN-10) y quien no es de este proyecto, 403.
 */
@RestController
@RequestMapping("/api/v1")
class ControladorMensajes {

    private static final String PARTICIPA = "@permisos.tiene('proyectos.ver_propios')";

    private final ServicioMensajes servicio;

    ControladorMensajes(ServicioMensajes servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/proyectos/{codigo}/mensajes")
    @PreAuthorize(PARTICIPA)
    Hilo hilo(@PathVariable String codigo) {
        return servicio.hilo(codigo);
    }

    @PostMapping("/proyectos/{codigo}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PARTICIPA)
    MensajeVista enviar(@PathVariable String codigo, @RequestBody SolicitudMensaje cuerpo) {
        return servicio.enviar(codigo, cuerpo.tema(), cuerpo.contenido());
    }

    @PatchMapping("/mensajes/{mensajeId}")
    @PreAuthorize(PARTICIPA)
    MensajeVista editar(@PathVariable UUID mensajeId, @RequestBody SolicitudMensaje cuerpo) {
        return servicio.editar(mensajeId, cuerpo.tema(), cuerpo.contenido());
    }
}
