package co.coretech.portal.compartido.controller;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protección CSRF (decisión de CLAUDE.md): la cookie de sesión viaja sola, así que una petición que
 * cambia datos (POST, PUT, PATCH, DELETE) con un encabezado Origin de otro sitio se rechaza con 403.
 * Junto con SameSite=Strict, un sitio ajeno no puede actuar en nombre del usuario.
 * <p>
 * Sin encabezado Origin se deja pasar: los navegadores lo envían en estas peticiones, y así siguen
 * funcionando curl y las pruebas. No es un bean: lo crea {@link ConfiguracionSeguridad}.
 */
class FiltroOrigen extends OncePerRequestFilter {

    static final String MENSAJE = "La petición viene de un sitio no permitido";
    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> origenesPermitidos;
    private final EscritorErrores errores;

    FiltroOrigen(List<String> origenesPermitidos, EscritorErrores errores) {
        this.origenesPermitidos = Set.copyOf(origenesPermitidos);
        this.errores = errores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String origen = peticion.getHeader("Origin");
        if (origen != null && !METODOS_SEGUROS.contains(peticion.getMethod()) && !origenesPermitidos.contains(origen)) {
            errores.escribir(respuesta, HttpStatus.FORBIDDEN, MENSAJE);
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }
}
