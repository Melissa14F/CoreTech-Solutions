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

import co.coretech.portal.agenda.service.ServicioCitas;
import co.coretech.portal.agenda.service.ServicioCitas.DatosCita;
import co.coretech.portal.agenda.service.ServicioDisponibilidad.Franja;
import co.coretech.portal.agenda.dto.DtoCitas.CitaCancelada;
import co.coretech.portal.agenda.dto.DtoCitas.CitaCreada;
import co.coretech.portal.agenda.dto.DtoCitas.CitaPropia;
import co.coretech.portal.agenda.dto.DtoCitas.SolicitudCancelar;
import co.coretech.portal.agenda.dto.DtoCitas.SolicitudCita;
import co.coretech.portal.compartido.service.SesionActual;

/**
 * Citas del cliente (HU-01, HU-08). Cada endpoint pide su permiso, no el rol: "Agendar citas" para agendar y ver
 * la disponibilidad y las propias, "Cancelar su cita pendiente" para cancelar. Los de administración y del
 * desarrollador (Product Owner) llegan en P13.
 */
@RestController
@RequestMapping("/api/v1/citas")
class ControladorCitas {

    private final ServicioCitas citas;
    private final SesionActual sesion;

    ControladorCitas(ServicioCitas citas, SesionActual sesion) {
        this.citas = citas;
        this.sesion = sesion;
    }

    /** Paso 2 del agendamiento: las franjas del mes en la zona del cliente (LIBRE, OCUPADO o NO_HABIL). */
    @GetMapping("/disponibilidad")
    @PreAuthorize("@permisos.tiene('citas.agendar')")
    List<Franja> disponibilidad(@RequestParam(required = false) UUID servicioId, @RequestParam(required = false) String mes,
            @RequestParam(required = false) String zona) {
        return citas.disponibilidad(sesion.usuario().id(), servicioId, mes, zona);
    }

    /** Paso 3: confirma la cita. 409 si la franja ya se ocupó; 422 si no cumple RN-01. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permisos.tiene('citas.agendar')")
    CitaCreada agendar(@RequestBody SolicitudCita s) {
        return CitaCreada.de(citas.agendar(sesion.usuario().id(),
                new DatosCita(s.servicioId(), s.fechaHora(), s.modalidad(), s.descripcionNecesidad(), s.pais(), s.zonaHoraria()))
                .cita());
    }

    /** Mis citas: solo las del cliente en sesión. */
    @GetMapping
    @PreAuthorize("@permisos.tiene('citas.agendar')")
    List<CitaPropia> misCitas() {
        return citas.deCliente(sesion.usuario().id()).stream().map(CitaPropia::de).toList();
    }

    /** RN-11: solo PENDIENTE y solo la dueña o el dueño; el motivo es opcional (15 a 300 caracteres). */
    @PatchMapping("/{citaId}/cancelar")
    @PreAuthorize("@permisos.tiene('citas.cancelar_propia')")
    CitaCancelada cancelar(@PathVariable UUID citaId, @RequestBody(required = false) SolicitudCancelar s) {
        return CitaCancelada.de(citas.cancelar(sesion.usuario().id(), citaId, s == null ? null : s.motivo()));
    }
}
