package co.coretech.portal.catalogo.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.coretech.portal.catalogo.aplicacion.ServicioCatalogo;
import co.coretech.portal.catalogo.aplicacion.ServicioCatalogo.DatosServicio;
import co.coretech.portal.catalogo.web.DtoCatalogo.RespuestaImagen;
import co.coretech.portal.catalogo.web.DtoCatalogo.ServicioGestion;
import co.coretech.portal.catalogo.web.DtoCatalogo.ServicioPublico;
import co.coretech.portal.catalogo.web.DtoCatalogo.SolicitudOrden;
import co.coretech.portal.catalogo.web.DtoCatalogo.SolicitudServicio;
import co.coretech.portal.catalogo.web.DtoCatalogo.SolicitudVisibilidad;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;

/**
 * Servicios del catálogo. Públicos: la lista visible (HU-07) y las imágenes subidas. El resto pide el
 * permiso "Editar los servicios del catálogo" (HU-18).
 */
@RestController
@RequestMapping("/api/v1/servicios")
class ControladorServicios {

    private static final String PERMISO = "@permisos.tiene('sitio.servicios')";

    private final ServicioCatalogo catalogo;

    ControladorServicios(ServicioCatalogo catalogo) {
        this.catalogo = catalogo;
    }

    /** Público: servicios visibles en el orden del carrusel. Los ocultos nunca salen aquí (RN-21). */
    @GetMapping
    List<ServicioPublico> visibles() {
        return catalogo.visibles().stream().map(ServicioPublico::de).toList();
    }

    /** Administrador: todos, también los ocultos, con su orden, sus citas recientes y sus fases. */
    @GetMapping("/gestion")
    @PreAuthorize(PERMISO)
    List<ServicioGestion> todos() {
        return ServicioGestion.de(catalogo.todos());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PERMISO)
    ServicioGestion crear(@RequestBody SolicitudServicio s) {
        return ServicioGestion.de(catalogo.crear(datos(s)));
    }

    @PutMapping("/{servicioId}")
    @PreAuthorize(PERMISO)
    ServicioGestion editar(@PathVariable UUID servicioId, @RequestBody SolicitudServicio s) {
        return ServicioGestion.de(catalogo.editar(servicioId, datos(s)));
    }

    @PatchMapping("/{servicioId}/visible")
    @PreAuthorize(PERMISO)
    ServicioGestion cambiarVisibilidad(@PathVariable UUID servicioId, @RequestBody SolicitudVisibilidad s) {
        return ServicioGestion.de(catalogo.cambiarVisibilidad(servicioId, s.visible()));
    }

    @PatchMapping("/{servicioId}/orden")
    @PreAuthorize(PERMISO)
    List<ServicioGestion> mover(@PathVariable UUID servicioId, @RequestBody SolicitudOrden s) {
        return ServicioGestion.de(catalogo.mover(servicioId, s.direccion()));
    }

    /** RN-21: con citas o proyectos responde 409 y el servicio se puede ocultar. */
    @DeleteMapping("/{servicioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(PERMISO)
    void eliminar(@PathVariable UUID servicioId) {
        catalogo.eliminar(servicioId);
    }

    /** Sube una imagen propia (WebP, PNG o JPG de máximo 2 MB); devuelve la URL para guardarla en el servicio. */
    @PostMapping(path = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PERMISO)
    RespuestaImagen subirImagen(@RequestParam("archivo") MultipartFile archivo) {
        try {
            return new RespuestaImagen(catalogo.subirImagen(archivo.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen subida", e);
        }
    }

    /** Público: una imagen subida (la página principal la muestra a cualquier visitante). */
    @GetMapping("/imagenes/{nombre}")
    ResponseEntity<byte[]> imagen(@PathVariable String nombre) {
        var archivo = catalogo.imagen(nombre).orElseThrow(() -> new NoEncontradoException("Imagen no encontrada"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoMime()))
                // El nombre es único y el archivo no cambia: el navegador la puede guardar mucho tiempo
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(archivo.contenido());
    }

    private static DatosServicio datos(SolicitudServicio s) {
        return new DatosServicio(s.nombre(), s.descripcionCorta(), s.imagenUrl(), s.imagenAlt(), s.visible());
    }
}
