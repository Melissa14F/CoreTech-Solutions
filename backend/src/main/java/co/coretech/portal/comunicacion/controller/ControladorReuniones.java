package co.coretech.portal.comunicacion.controller;

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

import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.comunicacion.service.ServicioReuniones.SolicitudVista;
import co.coretech.portal.comunicacion.service.ServicioReuniones.SolicitudesDelProyecto;
import co.coretech.portal.comunicacion.service.ServicioReuniones;
import co.coretech.portal.comunicacion.dto.DtoReuniones.RespuestaSolicitud;
import co.coretech.portal.comunicacion.dto.DtoReuniones.ResumenReunion;
import co.coretech.portal.comunicacion.dto.DtoReuniones.SolicitudReunion;

/**
 * Las reuniones de seguimiento (HU-15, HU-16, D-42).
 * <ul>
 *   <li>{@code GET} y {@code POST /proyectos/{codigo}/solicitudes-reunion}: ver las del proyecto y pedir una (solo el cliente
 *       dueño; permiso {@code reuniones.solicitar}).</li>
 *   <li>{@code GET /solicitudes-reunion}: las del equipo (Product Owner) o todas (administrador); permiso
 *       {@code reuniones.programar}.</li>
 *   <li>{@code PATCH /solicitudes-reunion/{id}}: programar, rechazar o cancelar, según {@code accion}.</li>
 *   <li>{@code POST /solicitudes-reunion/{id}/resumen}: el resumen después de la reunión; la solicitud queda ATENDIDA.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
class ControladorReuniones {

    private static final String VER = "@permisos.tiene('proyectos.ver_propios') or @permisos.tiene('proyectos.ver_todos')";
    private static final String SOLICITAR = "@permisos.tiene('reuniones.solicitar')";
    private static final String GESTIONAR = "@permisos.tiene('reuniones.programar')";

    private final ServicioReuniones servicio;

    ControladorReuniones(ServicioReuniones servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/proyectos/{codigo}/solicitudes-reunion")
    @PreAuthorize(VER)
    SolicitudesDelProyecto deProyecto(@PathVariable String codigo) {
        return servicio.deProyecto(codigo);
    }

    @PostMapping("/proyectos/{codigo}/solicitudes-reunion")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLICITAR)
    SolicitudVista solicitar(@PathVariable String codigo, @RequestBody SolicitudReunion c) {
        return servicio.solicitar(codigo, c.motivo(), c.razon(), c.modalidad(), c.opcion1() == null ? null : c.opcion1().toInstant(),
                c.opcion2() == null ? null : c.opcion2().toInstant());
    }

    @GetMapping("/solicitudes-reunion")
    @PreAuthorize(GESTIONAR)
    List<SolicitudVista> listar() {
        return servicio.listar();
    }

    @PatchMapping("/solicitudes-reunion/{solicitudId}")
    @PreAuthorize(GESTIONAR)
    SolicitudVista responder(@PathVariable UUID solicitudId, @RequestBody RespuestaSolicitud c) {
        String accion = c.accion() == null ? "" : c.accion();
        return switch (accion) {
            case "PROGRAMAR" -> servicio.programar(solicitudId, c.opcion(), c.enlace());
            case "RECHAZAR" -> servicio.rechazar(solicitudId, c.motivo());
            case "CANCELAR" -> servicio.cancelar(solicitudId, c.motivo());
            default -> throw new ReglaNegocioException("accion", "Elige si programas, rechazas o cancelas la reunión.");
        };
    }

    @PostMapping("/solicitudes-reunion/{solicitudId}/resumen")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTIONAR)
    SolicitudVista resumen(@PathVariable UUID solicitudId, @RequestBody ResumenReunion c) {
        return servicio.cerrar(solicitudId, c.resumen(), c.quePidio(), c.siguientePaso());
    }
}
