package co.coretech.portal.compartido.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Carga el usuario de la sesión con su rol y sus permisos. Lo implementa el módulo acceso, que es el
 * dueño de usuarios, roles y permisos; {@code compartido} solo conoce esta interfaz y así no depende
 * de ningún módulo de negocio.
 */
public interface CargadorUsuarioSesion {

    /** Vacío si el usuario no existe o su cuenta está desactivada: la sesión deja de valer. */
    Optional<UsuarioSesion> cargar(UUID usuarioId);
}
