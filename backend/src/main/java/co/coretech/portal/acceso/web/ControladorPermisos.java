package co.coretech.portal.acceso.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.acceso.aplicacion.ServicioPermisos;
import co.coretech.portal.acceso.aplicacion.ServicioPermisos.Cambio;
import co.coretech.portal.acceso.aplicacion.ServicioPermisos.Matriz;
import co.coretech.portal.acceso.web.DtoAcceso.SolicitudPermisos;
import jakarta.validation.Valid;

/**
 * Roles y permisos (HU-21): la matriz completa y sus cambios en una sola petición (todos o ninguno).
 * Permiso seguridad.permisos.
 */
@RestController
@RequestMapping("/api/v1/roles/permisos")
@PreAuthorize("@permisos.tiene('seguridad.permisos')")
class ControladorPermisos {

    private final ServicioPermisos permisos;

    ControladorPermisos(ServicioPermisos permisos) {
        this.permisos = permisos;
    }

    @GetMapping
    Matriz matriz() {
        return permisos.matriz();
    }

    @PutMapping
    Matriz cambiar(@Valid @RequestBody SolicitudPermisos cuerpo) {
        return permisos.cambiar(cuerpo.cambios().stream()
                .map(c -> new Cambio(c.rol(), c.permiso(), c.permitido()))
                .toList());
    }
}
