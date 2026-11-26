package co.coretech.portal.seguimiento.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.seguimiento.service.ServicioFases;
import co.coretech.portal.seguimiento.service.ServicioFases.Aprobada;
import co.coretech.portal.seguimiento.service.ServicioFases.CambioPedido;
import co.coretech.portal.seguimiento.service.ServicioFases.CorreccionRegistrada;
import co.coretech.portal.seguimiento.dto.DtoFases.EntregadaDto;
import co.coretech.portal.seguimiento.dto.DtoFases.SolicitudCambio;
import co.coretech.portal.seguimiento.dto.DtoFases.SolicitudCorreccion;

/**
 * El ciclo de una fase (HU-11, HU-12, HU-13).
 * <ul>
 *   <li>{@code POST /fases/{id}/entregar} y {@code POST /fases/{id}/pasos} (corrección): permiso
 *       {@code proyectos.registrar_avance}; el servicio exige ser el Product Owner del equipo (RN-07).</li>
 *   <li>{@code POST /fases/{id}/aprobar} y {@code POST /fases/{id}/solicitudes-cambio}: las responde el cliente dueño del
 *       proyecto ({@code proyectos.ver_propios}); el de otro cliente recibe 404 (RN-10).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/fases")
class ControladorFases {

    private static final String REGISTRAR = "@permisos.tiene('proyectos.registrar_avance')";
    private static final String RESPONDER = "@permisos.tiene('proyectos.ver_propios')";

    private final ServicioFases servicio;

    ControladorFases(ServicioFases servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/{faseId}/entregar")
    @PreAuthorize(REGISTRAR)
    EntregadaDto entregar(@PathVariable UUID faseId) {
        return EntregadaDto.de(servicio.entregar(faseId));
    }

    @PostMapping("/{faseId}/aprobar")
    @PreAuthorize(RESPONDER)
    Aprobada aprobar(@PathVariable UUID faseId) {
        return servicio.aprobar(faseId);
    }

    @PostMapping("/{faseId}/solicitudes-cambio")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RESPONDER)
    CambioPedido pedirCambio(@PathVariable UUID faseId, @RequestBody SolicitudCambio cuerpo) {
        return servicio.pedirCambio(faseId, cuerpo.tipo(), cuerpo.descripcion());
    }

    @PostMapping("/{faseId}/pasos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(REGISTRAR)
    CorreccionRegistrada registrarCorreccion(@PathVariable UUID faseId, @RequestBody SolicitudCorreccion cuerpo) {
        return servicio.registrarCorreccion(faseId, cuerpo.origen(), cuerpo.titulo(), cuerpo.explicacion());
    }
}
