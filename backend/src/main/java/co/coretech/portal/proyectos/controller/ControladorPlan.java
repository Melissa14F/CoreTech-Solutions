package co.coretech.portal.proyectos.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.proyectos.service.ServicioPlan.Aprobado;
import co.coretech.portal.proyectos.service.ServicioPlan.Enviado;
import co.coretech.portal.proyectos.service.ServicioPlan.Respondido;
import co.coretech.portal.proyectos.service.ServicioPlan.Solicitud;
import co.coretech.portal.proyectos.service.ServicioPlan.VistaPlan;
import co.coretech.portal.proyectos.service.ServicioPlan;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;
import co.coretech.portal.proyectos.dto.DtoPlan.SolicitudAjustes;
import co.coretech.portal.proyectos.dto.DtoPlan.SolicitudAprobar;
import co.coretech.portal.proyectos.dto.DtoPlan.SolicitudPlan;
import co.coretech.portal.proyectos.dto.DtoPlan.SolicitudRechazo;

/**
 * Plan de trabajo y cotización de un proyecto (HU-28, HU-29). Preparar y enviar el plan pide
 * {@code proyectos.planificar}; responderlo (aprobar, ajustes, rechazar) lo hace el cliente dueño del proyecto, que ve
 * sus proyectos con {@code proyectos.ver_propios}. El servicio decide si el proyecto es suyo (404) o de su equipo (403).
 */
@RestController
@RequestMapping("/api/v1/proyectos/{codigo}/plan")
class ControladorPlan {

    private final ServicioPlan plan;

    ControladorPlan(ServicioPlan plan) {
        this.plan = plan;
    }

    // --------------------------------------------------------------------------------------------- DTO

    // --------------------------------------------------------------------------------------------- Endpoints

    @GetMapping
    @PreAuthorize("@permisos.tiene('proyectos.planificar') or @permisos.tiene('proyectos.ver_propios')")
    VistaPlan obtener(@PathVariable String codigo) {
        return plan.obtener(codigo);
    }

    @PutMapping
    @PreAuthorize("@permisos.tiene('proyectos.planificar')")
    VistaPlan guardar(@PathVariable String codigo, @RequestBody SolicitudPlan cuerpo) {
        return plan.guardar(codigo, aSolicitud(cuerpo));
    }

    @PostMapping("/enviar")
    @PreAuthorize("@permisos.tiene('proyectos.planificar')")
    Enviado enviar(@PathVariable String codigo, @RequestBody SolicitudPlan cuerpo) {
        return plan.enviar(codigo, aSolicitud(cuerpo));
    }

    @PostMapping("/recordar")
    @PreAuthorize("@permisos.tiene('proyectos.planificar')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recordar(@PathVariable String codigo) {
        plan.recordar(codigo);
    }

    @PostMapping("/aprobar")
    @PreAuthorize("@permisos.tiene('proyectos.ver_propios')")
    Aprobado aprobar(@PathVariable String codigo, @RequestBody SolicitudAprobar cuerpo) {
        return plan.aprobar(codigo, Boolean.TRUE.equals(cuerpo.aceptaPlanYCotizacion()));
    }

    @PostMapping("/ajustes")
    @PreAuthorize("@permisos.tiene('proyectos.ver_propios')")
    @ResponseStatus(HttpStatus.CREATED)
    Respondido pedirAjustes(@PathVariable String codigo, @RequestBody SolicitudAjustes cuerpo) {
        return plan.pedirAjustes(codigo, cuerpo.comentario());
    }

    @PostMapping("/rechazar")
    @PreAuthorize("@permisos.tiene('proyectos.ver_propios')")
    @ResponseStatus(HttpStatus.CREATED)
    Respondido rechazar(@PathVariable String codigo, @RequestBody SolicitudRechazo cuerpo) {
        return plan.rechazar(codigo, cuerpo.motivo());
    }

    private static Solicitud aSolicitud(SolicitudPlan cuerpo) {
        List<FasePlantilla> fases = cuerpo.fases() == null ? null : cuerpo.fases().stream()
                .map(f -> f == null ? null : new FasePlantilla(0, f.nombre(), f.queEs(), Boolean.TRUE.equals(f.apruebaCliente()),
                        f.pasos() == null ? null : f.pasos().stream()
                                .map(p -> p == null ? null : new PasoPlantilla(0, p.titulo(), p.explicacion())).toList()))
                .toList();
        return new Solicitud(fases, cuerpo.valorCotizado(), cuerpo.condicionesCotizacion(), cuerpo.fechaEstimadaEntrega());
    }
}
