package co.coretech.portal.acceso.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.acceso.aplicacion.Ingreso;
import co.coretech.portal.acceso.aplicacion.NuevoCliente;
import co.coretech.portal.acceso.aplicacion.ServicioAutenticacion;
import co.coretech.portal.acceso.aplicacion.ServicioRecuperacion;
import co.coretech.portal.acceso.web.DtoAcceso.RespuestaIngreso;
import co.coretech.portal.acceso.web.DtoAcceso.RespuestaMensaje;
import co.coretech.portal.acceso.web.DtoAcceso.RespuestaRegistro;
import co.coretech.portal.acceso.web.DtoAcceso.RespuestaYo;
import co.coretech.portal.acceso.web.DtoAcceso.SolicitudCambioPassword;
import co.coretech.portal.acceso.web.DtoAcceso.SolicitudLogin;
import co.coretech.portal.acceso.web.DtoAcceso.SolicitudRecuperacion;
import co.coretech.portal.acceso.web.DtoAcceso.SolicitudRegistro;
import co.coretech.portal.acceso.web.DtoAcceso.SolicitudRestablecer;
import co.coretech.portal.compartido.aplicacion.SesionActual;
import co.coretech.portal.compartido.aplicacion.UsuarioSesion;
import co.coretech.portal.compartido.web.ServicioSesion;
import jakarta.validation.Valid;

/** Acceso (HU-05, HU-06): registro, sesión y contraseña. La sesión viaja en la cookie HttpOnly. */
@RestController
@RequestMapping("/api/v1/auth")
class ControladorAutenticacion {

    private final ServicioAutenticacion autenticacion;
    private final ServicioRecuperacion recuperacion;
    private final ServicioSesion sesiones;
    private final SesionActual sesionActual;

    ControladorAutenticacion(ServicioAutenticacion autenticacion, ServicioRecuperacion recuperacion,
            ServicioSesion sesiones, SesionActual sesionActual) {
        this.autenticacion = autenticacion;
        this.recuperacion = recuperacion;
        this.sesiones = sesiones;
        this.sesionActual = sesionActual;
    }

    @PostMapping("/registro")
    ResponseEntity<RespuestaRegistro> registrar(@Valid @RequestBody SolicitudRegistro s) {
        Ingreso ingreso = autenticacion.registrar(new NuevoCliente(s.nombre(), s.email(), s.celular(), s.empresa(),
                s.password(), s.confirmacion(), Boolean.TRUE.equals(s.aceptaDatos())));
        return ResponseEntity.created(URI.create("/api/v1/auth/yo"))
                .header(HttpHeaders.SET_COOKIE, sesiones.abrir(ingreso.usuarioId(), ingreso.versionSesion(), false).toString())
                .body(new RespuestaRegistro(ingreso.usuarioId(), ingreso.rol(), ingreso.nombre()));
    }

    @PostMapping("/login")
    ResponseEntity<RespuestaIngreso> iniciarSesion(@Valid @RequestBody SolicitudLogin s) {
        Ingreso ingreso = autenticacion.iniciarSesion(s.email(), s.password());
        ResponseCookie cookie = sesiones.abrir(ingreso.usuarioId(), ingreso.versionSesion(), s.recordarme());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new RespuestaIngreso(ingreso.rol(), ingreso.nombre(), ingreso.debeCambiarPassword()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> cerrarSesion(@CookieValue(name = ServicioSesion.COOKIE, required = false) String token) {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sesiones.cerrar(token).toString()).build();
    }

    @GetMapping("/yo")
    RespuestaYo yo() {
        UsuarioSesion u = sesionActual.usuario();
        List<String> permisos = u.permisos().stream().sorted().toList();
        return new RespuestaYo(u.id(), u.nombre(), u.email(), u.rol(), permisos, u.debeCambiarPassword());
    }

    @PostMapping("/cambiar-password")
    ResponseEntity<RespuestaIngreso> cambiarPassword(@Valid @RequestBody SolicitudCambioPassword s,
            @CookieValue(name = ServicioSesion.COOKIE, required = false) String token) {
        Ingreso ingreso = autenticacion.cambiarPassword(s.passwordActual(), s.passwordNueva(), s.confirmacion());
        // Las demás sesiones dejan de valer (la versión subió); esta sigue con una cookie nueva
        boolean recordarme = sesiones.leer(token == null ? "" : token).map(ServicioSesion.Token::recordarme).orElse(false);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sesiones.abrir(ingreso.usuarioId(), ingreso.versionSesion(), recordarme).toString())
                .body(new RespuestaIngreso(ingreso.rol(), ingreso.nombre(), ingreso.debeCambiarPassword()));
    }

    /** "¿Olvidaste tu contraseña?": misma respuesta exista o no el correo. */
    @PostMapping("/recuperacion")
    ResponseEntity<RespuestaMensaje> solicitarRecuperacion(@Valid @RequestBody SolicitudRecuperacion s) {
        recuperacion.solicitarPorCorreo(s.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RespuestaMensaje("Si el correo existe, te enviamos un enlace"));
    }

    @PostMapping("/restablecer")
    RespuestaMensaje restablecer(@Valid @RequestBody SolicitudRestablecer s) {
        recuperacion.restablecer(s.token(), s.passwordNueva(), s.confirmacion());
        return new RespuestaMensaje("Listo, tu contraseña quedó cambiada. Ya puedes iniciar sesión.");
    }
}
