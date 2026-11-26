package co.coretech.portal.acceso.controller;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.acceso.service.Ingreso;
import co.coretech.portal.acceso.service.ServicioIngresoExterno.DatosPendientes;
import co.coretech.portal.acceso.service.ServicioIngresoExterno.Entro;
import co.coretech.portal.acceso.service.ServicioIngresoExterno.FaltaRegistro;
import co.coretech.portal.acceso.service.ServicioIngresoExterno.NoEntro;
import co.coretech.portal.acceso.service.ServicioIngresoExterno.Regreso;
import co.coretech.portal.acceso.service.ServicioIngresoExterno;
import co.coretech.portal.acceso.dto.DtoIngresoExterno.Proveedor;
import co.coretech.portal.acceso.dto.DtoIngresoExterno.RespuestaCompletar;
import co.coretech.portal.acceso.dto.DtoIngresoExterno.SolicitudCompletar;
import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.controller.ServicioSesion;

/**
 * Registro e ingreso con Google o Microsoft (RF-50, D-50). Todo es público (aún no hay sesión). Los botones son
 * enlaces normales: el navegador va al proveedor y vuelve a /callback, que redirige a la SPA:
 * <ul>
 * <li>entró → abre la sesión (la misma cookie de siempre) y lleva a /cliente/proyectos;</li>
 * <li>es nuevo → cookie {@value #COOKIE_REGISTRO} de 10 minutos y lleva a /completar-registro;</li>
 * <li>no pudo → lleva a /ingresar?externo=motivo&amp;proveedor=google.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
class ControladorIngresoExterno {

    static final String COOKIE_REGISTRO = "registro_externo";
    static final String RUTA_COOKIE = "/api/v1/auth/externo";

    private final ServicioIngresoExterno externo;
    private final ServicioSesion sesiones;
    private final String urlPortal;
    private final boolean cookieSegura;

    ControladorIngresoExterno(ServicioIngresoExterno externo, ServicioSesion sesiones, PropiedadesCoretech propiedades) {
        this.externo = externo;
        this.sesiones = sesiones;
        this.urlPortal = propiedades.urlPortal().replaceAll("/+$", "");
        this.cookieSegura = propiedades.sesion().cookieSegura();
    }

    /** Qué botones mostrar en Iniciar sesión y Crear cuenta. */
    @GetMapping("/proveedores")
    List<Proveedor> proveedores() {
        return externo.activos().stream()
                .map(p -> new Proveedor(p.name(), p.nombre(), "/api/v1/auth/externo/" + p.ruta()))
                .toList();
    }

    /** "Continuar con Google": lleva al navegador a la página del proveedor. */
    @GetMapping("/externo/{proveedor}")
    ResponseEntity<Void> ir(@PathVariable String proveedor) {
        try {
            return redirigir(externo.iniciar(proveedor));
        } catch (NoEncontradoException e) {
            return redirigir(urlPortal + "/ingresar?externo=no_disponible");
        }
    }

    @GetMapping("/externo/callback/{proveedor}")
    ResponseEntity<Void> regreso(@PathVariable String proveedor, @RequestParam(required = false) String state,
            @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
        Regreso regreso = externo.regreso(proveedor, state, code, error);
        if (regreso instanceof Entro entro) {
            Ingreso i = entro.ingreso();
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(urlPortal + "/cliente/proyectos"))
                    .header(HttpHeaders.SET_COOKIE, sesiones.abrir(i.usuarioId(), i.versionSesion(), false).toString())
                    .build();
        }
        if (regreso instanceof FaltaRegistro falta) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(urlPortal + "/completar-registro"))
                    .header(HttpHeaders.SET_COOKIE, cookieRegistro(falta.id(), Duration.ofMinutes(10)).toString())
                    .build();
        }
        NoEntro no = (NoEntro) regreso;
        String ruta = no.proveedor() == null ? "" : "&proveedor=" + no.proveedor().ruta();
        return redirigir(urlPortal + "/ingresar?externo=" + no.motivo().name().toLowerCase(Locale.ROOT) + ruta);
    }

    /** Lo que "Completa tu registro" muestra: proveedor, nombre sugerido y correo (no se puede cambiar). */
    @GetMapping("/externo/pendiente")
    DatosPendientes pendiente(@CookieValue(name = COOKIE_REGISTRO, required = false) String id) {
        return externo.pendiente(id);
    }

    @PostMapping("/externo/completar")
    ResponseEntity<RespuestaCompletar> completar(@CookieValue(name = COOKIE_REGISTRO, required = false) String id,
            @Valid @RequestBody SolicitudCompletar s) {
        Ingreso i = externo.completarRegistro(id, s.nombre(), s.celular(), s.empresa(), Boolean.TRUE.equals(s.aceptaDatos()));
        return ResponseEntity.created(URI.create("/api/v1/auth/yo"))
                .header(HttpHeaders.SET_COOKIE, sesiones.abrir(i.usuarioId(), i.versionSesion(), false).toString())
                .header(HttpHeaders.SET_COOKIE, cookieRegistro("", Duration.ZERO).toString())
                .body(new RespuestaCompletar(i.usuarioId(), i.rol(), i.nombre()));
    }

    private ResponseCookie cookieRegistro(String valor, Duration duracion) {
        return ResponseCookie.from(COOKIE_REGISTRO, valor)
                .httpOnly(true).secure(cookieSegura).sameSite("Strict").path(RUTA_COOKIE).maxAge(duracion).build();
    }

    private static ResponseEntity<Void> redirigir(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
