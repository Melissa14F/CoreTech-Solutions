package co.coretech.portal.acceso.controller;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.acceso.service.ServicioAuditoria;
import co.coretech.portal.acceso.service.ServicioAuditoria.Pagina;
import co.coretech.portal.acceso.service.ServicioAuditoria.Parametros;
import co.coretech.portal.acceso.service.ServicioAuditoria.Resumen;
import co.coretech.portal.compartido.PropiedadesCoretech;

/**
 * Auditoría (HU-22): solo lectura. No hay PUT, PATCH ni DELETE: la auditoría no se edita ni se borra
 * (RNF-02). Permiso seguridad.auditoria.
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("@permisos.tiene('seguridad.auditoria')")
class ControladorAuditoria {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ServicioAuditoria auditoria;
    private final PropiedadesCoretech propiedades;
    private final Clock reloj;

    ControladorAuditoria(ServicioAuditoria auditoria, PropiedadesCoretech propiedades, Clock reloj) {
        this.auditoria = auditoria;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Fechas en formato AAAA-MM-DD (días de Colombia); categoría con el valor de la lista del resumen. */
    @GetMapping
    Pagina consultar(@RequestParam(required = false) String categoria, @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return auditoria.consultar(new Parametros(categoria, usuarioId, desde, hasta, q), pagina, tamano);
    }

    @GetMapping("/resumen")
    Resumen resumen() {
        return auditoria.resumen();
    }

    @GetMapping("/exportar")
    ResponseEntity<byte[]> exportar(@RequestParam(required = false) String categoria,
            @RequestParam(required = false) String usuarioId, @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta, @RequestParam(required = false) String q) {
        String csv = auditoria.exportar(new Parametros(categoria, usuarioId, desde, hasta, q));
        String archivo = "auditoria-coretech-" + LocalDate.now(reloj.withZone(propiedades.zonaNegocio())) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo).build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
