package co.coretech.portal.especialidad.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.especialidad.service.ServicioDesarrolladores.DatosDesarrollador;
import co.coretech.portal.especialidad.service.ServicioDesarrolladores;
import co.coretech.portal.especialidad.service.Vistas.DesarrolladorVista;
import co.coretech.portal.especialidad.dto.DtoDesarrolladores.SolicitudActivo;

/** Registro de desarrolladores para el administrador (HU-25). Todo pide el permiso seguridad.cuentas. */
@RestController
@RequestMapping("/api/v1/desarrolladores")
@PreAuthorize("@permisos.tiene('seguridad.cuentas')")
class ControladorDesarrolladores {

    private final ServicioDesarrolladores desarrolladores;

    ControladorDesarrolladores(ServicioDesarrolladores desarrolladores) {
        this.desarrolladores = desarrolladores;
    }

    @GetMapping
    List<DesarrolladorVista> listar() {
        return desarrolladores.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DesarrolladorVista registrar(@RequestBody DatosDesarrollador cuerpo) {
        return desarrolladores.registrar(cuerpo);
    }

    @PutMapping("/{desarrolladorId}")
    DesarrolladorVista editar(@PathVariable UUID desarrolladorId, @RequestBody DatosDesarrollador cuerpo) {
        return desarrolladores.editar(desarrolladorId, cuerpo);
    }

    @PatchMapping("/{desarrolladorId}/activo")
    DesarrolladorVista cambiarActivo(@PathVariable UUID desarrolladorId, @RequestBody SolicitudActivo cuerpo) {
        if (cuerpo.activo() == null) {
            throw new ReglaNegocioException("activo", "Indica si el desarrollador queda activo.");
        }
        return desarrolladores.cambiarActivo(desarrolladorId, cuerpo.activo());
    }
}
