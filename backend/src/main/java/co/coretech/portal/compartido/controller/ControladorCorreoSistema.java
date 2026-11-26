package co.coretech.portal.compartido.controller;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.service.ConexionGmail.Resultado;
import co.coretech.portal.compartido.service.ServicioCorreoSistema;
import co.coretech.portal.compartido.dto.DtoCorreoSistema.EstadoCorreo;
import co.coretech.portal.compartido.dto.DtoCorreoSistema.ResultadoPrueba;
import co.coretech.portal.compartido.dto.DtoCorreoSistema.SolicitudPrueba;
import co.coretech.portal.compartido.dto.DtoCorreoSistema.UrlConexion;

/**
 * "Correo del sistema" del administrador (D-51). Pide el permiso seguridad.permisos (solo administradores, fijo por
 * RN-19), salvo el regreso de Google, que llega sin la cookie de sesión y se valida con el "estado" de un solo uso.
 */
@RestController
@RequestMapping("/api/v1/correo-sistema")
class ControladorCorreoSistema {

    static final String PERMISO = "@permisos.tiene('seguridad.permisos')";
    static final String PANTALLA = "/admin/correo";

    private final ServicioCorreoSistema correo;
    private final String urlPortal;

    ControladorCorreoSistema(ServicioCorreoSistema correo, PropiedadesCoretech propiedades) {
        this.correo = correo;
        this.urlPortal = propiedades.urlPortal().replaceAll("/+$", "");
    }

    @GetMapping
    @PreAuthorize(PERMISO)
    EstadoCorreo estado() {
        return EstadoCorreo.de(correo.estado());
    }

    /** "Conectar con Gmail": la SPA lleva el navegador a esta URL de Google. */
    @PostMapping("/gmail/conexion")
    @PreAuthorize(PERMISO)
    UrlConexion conectar() {
        return new UrlConexion(correo.iniciarConexion());
    }

    /** Google vuelve aquí; se redirige a la pantalla con el resultado. Público: lo protege el "estado" de un solo uso. */
    @GetMapping("/gmail/callback")
    ResponseEntity<Void> regresoDeGoogle(@RequestParam(required = false) String state,
            @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
        Resultado resultado = correo.completarConexion(state, code, error);
        URI destino = URI.create(urlPortal + PANTALLA + "?resultado=" + resultado.name().toLowerCase(Locale.ROOT));
        return ResponseEntity.status(HttpStatus.FOUND).location(destino).build();
    }

    @PostMapping("/prueba")
    @PreAuthorize(PERMISO)
    ResultadoPrueba enviarPrueba(@RequestBody SolicitudPrueba cuerpo) {
        String canal = correo.enviarPrueba(cuerpo.para());
        String mensaje = "GMAIL".equals(canal)
                ? "Correo enviado. Revisa la bandeja de entrada de " + cuerpo.para().trim() + "."
                : "Correo enviado al buzón de desarrollo (Mailpit, http://localhost:8025).";
        return new ResultadoPrueba(canal, mensaje);
    }

    @DeleteMapping("/gmail")
    @PreAuthorize(PERMISO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desconectar() {
        correo.desconectar();
    }
}
