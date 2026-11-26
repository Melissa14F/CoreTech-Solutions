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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.proyectos.service.ServicioProyectos.Creacion;
import co.coretech.portal.proyectos.service.ServicioProyectos.Edicion;
import co.coretech.portal.proyectos.service.ServicioProyectos.Filtros;
import co.coretech.portal.proyectos.service.ServicioProyectos.ProyectoPropio;
import co.coretech.portal.proyectos.service.ServicioProyectos;
import co.coretech.portal.proyectos.dto.DtoProyectos.CitaAtendidaDto;
import co.coretech.portal.proyectos.dto.DtoProyectos.CreadoDto;
import co.coretech.portal.proyectos.dto.DtoProyectos.EquipoDto;
import co.coretech.portal.proyectos.dto.DtoProyectos.ListaProyectos;
import co.coretech.portal.proyectos.dto.DtoProyectos.ProyectoDto;
import co.coretech.portal.proyectos.dto.DtoProyectos.ProyectoEditado;
import co.coretech.portal.proyectos.dto.DtoProyectos.SolicitudCrear;
import co.coretech.portal.proyectos.dto.DtoProyectos.SolicitudEditar;
import co.coretech.portal.proyectos.dto.DtoProyectos.SolicitudEstado;

/**
 * Crear proyectos desde una cita atendida (HU-02) y gestionarlos: lista con filtros, editar, reasignar, pausar,
 * reanudar y cancelar (HU-17). Cada endpoint pide su permiso: crear → {@code proyectos.crear}, el resto →
 * {@code proyectos.gestionar}.
 */
@RestController
@RequestMapping("/api/v1/proyectos")
class ControladorProyectos {

    private final ServicioProyectos servicio;

    ControladorProyectos(ServicioProyectos servicio) {
        this.servicio = servicio;
    }

    // --------------------------------------------------------------------------------------------- DTO

    // --------------------------------------------------------------------------------------------- Endpoints

    /** Citas atendidas para elegir el origen; las que ya son proyecto traen su código y no se pueden elegir (RN-04). */
    @GetMapping("/citas-atendidas")
    @PreAuthorize("@permisos.tiene('proyectos.crear')")
    List<CitaAtendidaDto> citasAtendidas() {
        return servicio.citasAtendidas().stream().map(CitaAtendidaDto::de).toList();
    }

    /** Equipos activos con su carga: el que ya tiene su cupo lleno se muestra deshabilitado (RN-05). */
    @GetMapping("/equipos")
    @PreAuthorize("@permisos.tiene('proyectos.crear') or @permisos.tiene('proyectos.gestionar')")
    List<EquipoDto> equipos() {
        return servicio.equiposParaElegir().stream().map(e -> new EquipoDto(e.equipoId(), e.productOwner(), e.integrantes(),
                e.proyectosActivos(), e.maxProyectos(), Math.max(0, e.maxProyectos() - e.proyectosActivos()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permisos.tiene('proyectos.crear')")
    CreadoDto crear(@RequestBody SolicitudCrear cuerpo) {
        return CreadoDto.de(servicio.crear(new Creacion(cuerpo.citaId(), cuerpo.nombre(), cuerpo.alcance(), cuerpo.fechaInicio(),
                cuerpo.fechaEstimadaEntrega(), cuerpo.equipoId())));
    }

    /** «Mis proyectos» del cliente y del Product Owner: cada uno ve solo los suyos (RN-10). El administrador usa la lista de arriba. */
    @GetMapping("/propios")
    @PreAuthorize("@permisos.tiene('proyectos.ver_propios')")
    List<ProyectoPropio> propios() {
        return servicio.propios();
    }

    /**
     * Lista del administrador. Filtros: {@code estado} (todas, activo, riesgo, plan, pausado, garantia, finalizado,
     * cancelado), {@code equipoId}, {@code q} (código, nombre o cliente, sin tildes), {@code pagina} (desde 0) y {@code tamano}.
     */
    @GetMapping
    @PreAuthorize("@permisos.tiene('proyectos.gestionar')")
    ListaProyectos listar(@RequestParam(required = false) String estado, @RequestParam(required = false) UUID equipoId,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return ListaProyectos.de(servicio.listar(new Filtros(estado, equipoId, q, pagina, tamano)));
    }

    @PatchMapping("/{codigo}")
    @PreAuthorize("@permisos.tiene('proyectos.gestionar')")
    ProyectoEditado editar(@PathVariable String codigo, @RequestBody SolicitudEditar cuerpo) {
        return ProyectoEditado.de(servicio.editar(codigo, new Edicion(cuerpo.nombre(), cuerpo.alcance(), cuerpo.fechaEstimadaEntrega(),
                cuerpo.equipoId(), cuerpo.motivo())));
    }

    @PatchMapping("/{codigo}/estado")
    @PreAuthorize("@permisos.tiene('proyectos.gestionar')")
    ProyectoDto cambiarEstado(@PathVariable String codigo, @RequestBody SolicitudEstado cuerpo) {
        return ProyectoDto.de(servicio.cambiarEstado(codigo, cuerpo.estado(), cuerpo.motivo(), cuerpo.equipoId()));
    }
}
