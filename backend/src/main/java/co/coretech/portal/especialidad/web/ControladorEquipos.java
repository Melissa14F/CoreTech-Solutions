package co.coretech.portal.especialidad.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.especialidad.aplicacion.ServicioEquipos;
import co.coretech.portal.especialidad.aplicacion.ServicioEquipos.DatosEquipo;
import co.coretech.portal.especialidad.aplicacion.ServicioEquipos.DatosIntegrante;
import co.coretech.portal.especialidad.aplicacion.Vistas.EquipoVista;

/** Equipos de trabajo para el administrador (HU-26). Todo pide el permiso seguridad.cuentas. */
@RestController
@RequestMapping("/api/v1/equipos")
@PreAuthorize("@permisos.tiene('seguridad.cuentas')")
class ControladorEquipos {

    private final ServicioEquipos equipos;

    ControladorEquipos(ServicioEquipos equipos) {
        this.equipos = equipos;
    }

    @GetMapping
    List<EquipoVista> listar() {
        return equipos.listar();
    }

    @GetMapping("/{equipoId}")
    EquipoVista obtener(@PathVariable UUID equipoId) {
        return equipos.obtener(equipoId);
    }

    /** Crea el equipo y la cuenta de su Product Owner; la contraseña temporal sale por correo, nunca en la respuesta. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EquipoVista crear(@RequestBody DatosEquipo cuerpo) {
        return equipos.crear(cuerpo);
    }

    @PutMapping("/{equipoId}")
    EquipoVista editar(@PathVariable UUID equipoId, @RequestBody DatosEquipo cuerpo) {
        return equipos.editar(equipoId, cuerpo);
    }

    @PostMapping("/{equipoId}/integrantes")
    @ResponseStatus(HttpStatus.CREATED)
    EquipoVista agregarIntegrante(@PathVariable UUID equipoId, @RequestBody DatosIntegrante cuerpo) {
        return equipos.agregarIntegrante(equipoId, cuerpo);
    }

    @DeleteMapping("/{equipoId}/integrantes/{desarrolladorId}")
    EquipoVista quitarIntegrante(@PathVariable UUID equipoId, @PathVariable UUID desarrolladorId) {
        return equipos.quitarIntegrante(equipoId, desarrolladorId);
    }
}
