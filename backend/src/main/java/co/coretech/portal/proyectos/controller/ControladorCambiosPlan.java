package co.coretech.portal.proyectos.controller;

import java.util.List;
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

import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Aceptado;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.CambioVista;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.CambiosDelProyecto;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Creado;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Propuesta;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Respondido;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan;
import co.coretech.portal.proyectos.dto.DtoCambiosPlan.Rechazo;

/**
 * Cambios de un plan aprobado (HU-30, RN-30, D-32).
 * <ul>
 *   <li>{@code GET} y {@code POST /proyectos/{codigo}/cambios-plan}: ver los cambios del proyecto y proponer uno (solo el
 *       Product Owner del equipo; permiso {@code proyectos.planificar}).</li>
 *   <li>{@code GET /cambios-plan}: los que esperan la autorización del administrador.</li>
 *   <li>{@code PATCH /cambios-plan/{id}/autorizar}: el administrador ({@code proyectos.gestionar}).</li>
 *   <li>{@code PATCH /cambios-plan/{id}/aceptar}: el cliente dueño. {@code PATCH .../rechazar}: el administrador si está
 *       propuesto o el cliente si está autorizado.</li>
 * </ul>
 * El servicio decide quién es el dueño del proyecto: un cliente ajeno recibe 404 (RN-10) y quien no es del proyecto, 403.
 */
@RestController
@RequestMapping("/api/v1")
class ControladorCambiosPlan {

    private static final String VER = "@permisos.tiene('proyectos.ver_propios') or @permisos.tiene('proyectos.ver_todos')";
    private static final String PROPONER = "@permisos.tiene('proyectos.planificar')";
    private static final String ADMINISTRAR = "@permisos.tiene('proyectos.gestionar')";
    private static final String RESPONDER = "@permisos.tiene('proyectos.ver_propios') or @permisos.tiene('proyectos.gestionar')";

    private final ServicioCambiosPlan servicio;

    ControladorCambiosPlan(ServicioCambiosPlan servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/proyectos/{codigo}/cambios-plan")
    @PreAuthorize(VER)
    CambiosDelProyecto deProyecto(@PathVariable String codigo) {
        return servicio.deProyecto(codigo);
    }

    @PostMapping("/proyectos/{codigo}/cambios-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PROPONER)
    Creado proponer(@PathVariable String codigo, @RequestBody Propuesta cuerpo) {
        return servicio.proponer(codigo, cuerpo);
    }

    @GetMapping("/cambios-plan")
    @PreAuthorize(ADMINISTRAR)
    List<CambioVista> porAutorizar() {
        return servicio.porAutorizar();
    }

    @PatchMapping("/cambios-plan/{cambioId}/autorizar")
    @PreAuthorize(ADMINISTRAR)
    Respondido autorizar(@PathVariable UUID cambioId) {
        return servicio.autorizar(cambioId);
    }

    @PatchMapping("/cambios-plan/{cambioId}/aceptar")
    @PreAuthorize(VER)
    Aceptado aceptar(@PathVariable UUID cambioId) {
        return servicio.aceptar(cambioId);
    }

    @PatchMapping("/cambios-plan/{cambioId}/rechazar")
    @PreAuthorize(RESPONDER)
    Respondido rechazar(@PathVariable UUID cambioId, @RequestBody Rechazo cuerpo) {
        return servicio.rechazar(cambioId, cuerpo.motivo());
    }
}
