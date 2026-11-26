package co.coretech.portal.compartido.service;

import co.coretech.portal.compartido.exception.NoAutenticadoException;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * El usuario con sesión, para los servicios. Los controladores protegidos siempre tienen uno
 * (si no, la seguridad ya respondió 401); por eso {@link #usuario()} falla si no lo hay.
 */
@Component
public class SesionActual {

    public Optional<UsuarioSesion> buscar() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioSesion usuario
                ? Optional.of(usuario)
                : Optional.empty();
    }

    public UsuarioSesion usuario() {
        return buscar().orElseThrow(() -> new NoAutenticadoException("Tu sesión venció, ingresa de nuevo"));
    }
}
