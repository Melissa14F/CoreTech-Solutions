package co.coretech.portal.compartido.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * El usuario de la petición en curso, cargado de la base en cada petición (así un cambio de rol,
 * de permisos o una cuenta desactivada aplica de inmediato).
 * <p>
 * {@code versionSesion}: si no coincide con la del token, la sesión ya no vale (V7).
 * <p>
 * {@link #getName()} devuelve el id: es lo que guarda la auditoría como usuario.
 */
public record UsuarioSesion(UUID id, String nombre, String email, String rol, Set<String> permisos,
        boolean debeCambiarPassword, int versionSesion) implements AuthenticatedPrincipal {

    public UsuarioSesion {
        permisos = Set.copyOf(permisos);
    }

    @Override
    public String getName() {
        return id.toString();
    }

    public boolean tienePermiso(String codigo) {
        return permisos.contains(codigo);
    }
}
