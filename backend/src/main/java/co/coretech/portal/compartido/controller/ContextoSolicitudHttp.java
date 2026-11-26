package co.coretech.portal.compartido.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import co.coretech.portal.compartido.service.ContextoSolicitud;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Lee el usuario de Spring Security y la IP y el navegador de la petición en curso.
 * <p>
 * El usuario es el nombre de la autenticación, que debe ser el id (UUID) del usuario: así lo arma el
 * filtro del JWT en P06 (el "sub" del token). Detrás de Nginx la IP real llega en X-Forwarded-For;
 * para que {@code getRemoteAddr()} la devuelva, el perfil de producción activa
 * {@code server.forward-headers-strategy=framework} (P25).
 */
@Component
class ContextoSolicitudHttp implements ContextoSolicitud {

    @Override
    public Optional<UUID> usuarioId() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(autenticacion.getName()));
        } catch (IllegalArgumentException noEsUnId) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> ip() {
        return peticion().map(HttpServletRequest::getRemoteAddr);
    }

    @Override
    public Optional<String> agente() {
        return peticion().map(p -> p.getHeader("User-Agent"));
    }

    private static Optional<HttpServletRequest> peticion() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos
                ? Optional.of(atributos.getRequest())
                : Optional.empty();
    }
}
