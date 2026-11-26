package co.coretech.portal.agenda.controller;

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

import co.coretech.portal.agenda.service.ServicioGestionCitas;
import co.coretech.portal.agenda.service.ServicioGestionCitas.DatosCuestionario;
import co.coretech.portal.agenda.service.ServicioGestionCitas.Filtros;
import co.coretech.portal.agenda.domain.EstadoCita;
import co.coretech.portal.agenda.dto.DtoCitas.CitaGestion;
import co.coretech.portal.agenda.dto.DtoCitas.ConfirmacionCita;
import co.coretech.portal.agenda.dto.DtoCitas.CuestionarioCreado;
import co.coretech.portal.agenda.dto.DtoCitas.CuestionarioVista;
import co.coretech.portal.agenda.dto.DtoCitas.EquipoParaAsignar;
import co.coretech.portal.agenda.dto.DtoCitas.ListaCitas;
import co.coretech.portal.agenda.dto.DtoCitas.SolicitudAsignar;
import co.coretech.portal.agenda.dto.DtoCitas.SolicitudCancelarAdmin;
import co.coretech.portal.agenda.dto.DtoCitas.SolicitudConfirmar;
import co.coretech.portal.agenda.dto.DtoCitas.SolicitudCuestionario;
import co.coretech.portal.compartido.service.SesionActual;

/**
 * Citas para el administrador (HU-09) y para el desarrollador (Product Owner) de un equipo (HU-10). Cada endpoint pide
 * su permiso, no el rol; que la cita sea de su equipo lo decide el servicio (403 si no lo es).
 */
@RestController
@RequestMapping("/api/v1/citas")
class ControladorGestionCitas {

    private final ServicioGestionCitas gestion;
    private final SesionActual sesion;

    ControladorGestionCitas(ServicioGestionCitas gestion, SesionActual sesion) {
        this.gestion = gestion;
        this.sesion = sesion;
    }

    /** Administrador: todas las citas con filtros (estado, sinEquipo, equipoId, desde, hasta) y los números de cada filtro. */
    @GetMapping("/gestion")
    @PreAuthorize("@permisos.tiene('citas.ver_todas')")
    ListaCitas todas(@RequestParam(required = false) String estado, @RequestParam(required = false) Boolean sinEquipo,
            @RequestParam(required = false) UUID equipoId, @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano) {
        return ListaCitas.de(gestion.listar(new Filtros(estado, sinEquipo, desde, hasta, equipoId, q, pagina, tamano)));
    }

    /** Administrador: los equipos activos con su carga, para elegir al asignar. */
    @GetMapping("/equipos-disponibles")
    @PreAuthorize("@permisos.tiene('citas.asignar')")
    List<EquipoParaAsignar> equiposDisponibles() {
        return gestion.equiposParaAsignar().stream().map(EquipoParaAsignar::de).toList();
    }

    @PatchMapping("/{citaId}/asignar")
    @PreAuthorize("@permisos.tiene('citas.asignar')")
    CitaGestion asignar(@PathVariable UUID citaId, @RequestBody SolicitudAsignar s) {
        return CitaGestion.de(gestion.asignar(citaId, s.equipoId(), s.nota()));
    }

    /** RN-11: la administración cancela una cita PENDIENTE o CONFIRMADA con motivo obligatorio. */
    @PatchMapping("/{citaId}/cancelar-admin")
    @PreAuthorize("@permisos.tiene('citas.asignar')")
    CitaGestion cancelarComoAdministrador(@PathVariable UUID citaId, @RequestBody(required = false) SolicitudCancelarAdmin s) {
        return CitaGestion.de(gestion.cancelar(sesion.usuario().id(), citaId, s == null ? null : s.motivo()));
    }

    /** Product Owner: las citas de su equipo. */
    @GetMapping("/equipo")
    @PreAuthorize("@permisos.tiene('citas.ver_asignadas')")
    List<CitaGestion> deMiEquipo() {
        return gestion.deMiEquipo(sesion.usuario().id()).stream().map(CitaGestion::de).toList();
    }

    @PatchMapping("/{citaId}/confirmar")
    @PreAuthorize("@permisos.tiene('citas.atender')")
    ConfirmacionCita confirmar(@PathVariable UUID citaId, @RequestBody(required = false) SolicitudConfirmar s) {
        var vista = gestion.confirmar(sesion.usuario().id(), citaId, s == null ? null : s.enlaceReunion(),
                s == null ? null : s.nota());
        return new ConfirmacionCita(vista.cita().getId(), vista.cita().getEstado(), vista.cita().getConfirmadaEn());
    }

    /** RN-24: el cuestionario deja la cita ATENDIDA y no se edita después. */
    @PostMapping("/{citaId}/cuestionario")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permisos.tiene('citas.atender')")
    CuestionarioCreado llenarCuestionario(@PathVariable UUID citaId, @RequestBody SolicitudCuestionario s) {
        UUID id = gestion.llenarCuestionario(sesion.usuario().id(), citaId, new DatosCuestionario(s.resumen(),
                s.descripcionCliente(), s.presupuesto(), s.fechaDeseada(), s.idioma(), s.urgencia()));
        return new CuestionarioCreado(id, EstadoCita.ATENDIDA);
    }

    /** Lo ven el administrador (cualquiera) y el Product Owner de la cita. */
    @GetMapping("/{citaId}/cuestionario")
    @PreAuthorize("@permisos.tiene('citas.ver_todas') or @permisos.tiene('citas.ver_asignadas')")
    CuestionarioVista verCuestionario(@PathVariable UUID citaId) {
        return CuestionarioVista.de(gestion.cuestionarioDe(sesion.usuario(), citaId));
    }
}
