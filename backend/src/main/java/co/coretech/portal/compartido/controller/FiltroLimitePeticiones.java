package co.coretech.portal.compartido.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import co.coretech.portal.compartido.PropiedadesCoretech;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite de intentos por IP en las rutas públicas de acceso (RNF-10): sin él se podían probar contraseñas
 * o llenar de correos un buzón sin parar. Pasado el límite responde 429 con Retry-After. El bloqueo de la
 * cuenta a los 5 intentos fallidos (HU-06) sigue aparte: este límite protege aunque se prueben muchas cuentas.
 * <p>
 * La IP es la de la petición; detrás de Nginx llega la real con server.forward-headers-strategy (P25).
 * No es un bean: lo crea {@link ConfiguracionSeguridad}.
 */
class FiltroLimitePeticiones extends OncePerRequestFilter {

    static final String MENSAJE = "Hiciste demasiados intentos. Espera unos minutos e intenta de nuevo.";

    private record Regla(int maximo, Duration ventana) {
    }

    private final Map<String, Regla> reglas;
    private final LimitadorPeticiones limitador;
    private final EscritorErrores errores;

    FiltroLimitePeticiones(PropiedadesCoretech.Limites limites, LimitadorPeticiones limitador, EscritorErrores errores) {
        this.reglas = Map.of(
                "/api/v1/auth/login", new Regla(limites.loginPorMinuto(), Duration.ofMinutes(1)),
                "/api/v1/auth/recuperacion", new Regla(limites.recuperacionPor15Minutos(), Duration.ofMinutes(15)),
                "/api/v1/auth/restablecer", new Regla(limites.restablecerPor15Minutos(), Duration.ofMinutes(15)),
                "/api/v1/auth/registro", new Regla(limites.registroPorHora(), Duration.ofHours(1)),
                // Registro con Google o Microsoft (RF-50): mismo límite que el registro con correo
                "/api/v1/auth/externo/completar", new Regla(limites.registroPorHora(), Duration.ofHours(1)));
        this.limitador = limitador;
        this.errores = errores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Regla regla = "POST".equals(peticion.getMethod()) ? reglas.get(peticion.getRequestURI()) : null;
        if (regla != null) {
            String clave = peticion.getRequestURI() + "|" + peticion.getRemoteAddr();
            Optional<Duration> espera = limitador.intentar(clave, regla.maximo(), regla.ventana());
            if (espera.isPresent()) {
                respuesta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, espera.get().toSeconds())));
                errores.escribir(respuesta, HttpStatus.TOO_MANY_REQUESTS, MENSAJE);
                return;
            }
        }
        cadena.doFilter(peticion, respuesta);
    }
}
