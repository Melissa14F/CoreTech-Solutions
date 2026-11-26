package co.coretech.portal.seguimiento.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import co.coretech.portal.seguimiento.service.ServicioSeguimiento.AdjuntoSubido;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.ArchivoSubido;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.Descarga;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.DetalleProyecto;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.PasoRegistrado;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento;
import co.coretech.portal.seguimiento.dto.DtoPasos.SolicitudPaso;

/**
 * Registrar el trabajo de la fase actual (HU-03) y consultar el estado del proyecto (HU-04).
 * <ul>
 *   <li>{@code PATCH /pasos/{id}}: marcar un paso como hecho. Permiso {@code proyectos.registrar_avance} (RN-07).</li>
 *   <li>{@code POST /pasos/{id}/adjuntos}: bocetos y documentos del paso. El mismo permiso.</li>
 *   <li>{@code GET /adjuntos/{id}} y {@code GET /proyectos/{codigo}}: los ve quien tenga {@code proyectos.ver_propios} o
 *       {@code proyectos.ver_todos}; el servicio decide si el proyecto es suyo (un cliente ajeno recibe 404, RN-10).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
class ControladorPasos {

    private static final String VER = "@permisos.tiene('proyectos.ver_propios') or @permisos.tiene('proyectos.ver_todos')";
    private static final String REGISTRAR = "@permisos.tiene('proyectos.registrar_avance')";

    private final ServicioSeguimiento servicio;

    ControladorPasos(ServicioSeguimiento servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/proyectos/{codigo}")
    @PreAuthorize(VER)
    DetalleProyecto detalle(@PathVariable String codigo) {
        return servicio.detalle(codigo);
    }

    @PatchMapping("/pasos/{pasoId}")
    @PreAuthorize(REGISTRAR)
    PasoRegistrado marcarHecho(@PathVariable UUID pasoId, @RequestBody SolicitudPaso cuerpo) {
        return servicio.marcarHecho(pasoId, cuerpo.estado(), cuerpo.nota());
    }

    @PostMapping(path = "/pasos/{pasoId}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(REGISTRAR)
    List<AdjuntoSubido> adjuntar(@PathVariable UUID pasoId,
            @RequestParam(name = "archivos", required = false) List<MultipartFile> archivos) {
        List<ArchivoSubido> subidos = new ArrayList<>();
        try {
            for (MultipartFile a : archivos == null ? List.<MultipartFile>of() : archivos) {
                subidos.add(new ArchivoSubido(a.getOriginalFilename(), a.getBytes()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
        return servicio.adjuntar(pasoId, subidos);
    }

    /**
     * Sirve el adjunto por la API, con control de acceso (no hay rutas públicas). {@code nosniff} impide que el navegador
     * adivine otro tipo; un SVG se sirve sin permiso para ejecutar nada ({@code sandbox}) aunque se abra directo.
     */
    @GetMapping("/adjuntos/{adjuntoId}")
    @PreAuthorize(VER)
    ResponseEntity<byte[]> descargar(@PathVariable UUID adjuntoId) {
        Descarga d = servicio.descargar(adjuntoId);
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data:; sandbox");
        return respuesta.body(d.archivo().contenido());
    }
}
