package co.coretech.portal.catalogo.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.catalogo.aplicacion.ServicioContenido;
import co.coretech.portal.catalogo.web.DtoCatalogo.ContenidoEdicion;
import co.coretech.portal.catalogo.web.DtoCatalogo.ContenidoPublico;
import co.coretech.portal.catalogo.web.DtoCatalogo.SolicitudPublicar;

/** Textos de la página principal: lectura pública (HU-07) y edición del administrador (HU-19). */
@RestController
@RequestMapping("/api/v1/contenido")
class ControladorContenido {

    private static final String PERMISO = "@permisos.tiene('sitio.contenido')";

    private final ServicioContenido contenido;

    ControladorContenido(ServicioContenido contenido) {
        this.contenido = contenido;
    }

    @GetMapping
    ContenidoPublico publico() {
        return new ContenidoPublico(contenido.publico());
    }

    /** Cada texto con su sección y su largo permitido, y la última publicación. */
    @GetMapping("/edicion")
    @PreAuthorize(PERMISO)
    ContenidoEdicion edicion() {
        return ContenidoEdicion.de(contenido.edicion());
    }

    /** Publica los textos que cambiaron; si uno no cumple, no se publica ninguno (422 por campo). */
    @PutMapping
    @PreAuthorize(PERMISO)
    ContenidoEdicion publicar(@RequestBody SolicitudPublicar s) {
        return ContenidoEdicion.de(contenido.publicar(s.textos()));
    }
}
