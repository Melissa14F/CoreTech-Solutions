package co.coretech.portal.compartido.service;

import org.springframework.stereotype.Component;

/**
 * Expresión de autorización por permiso (RNF-01, HU-21): los endpoints nunca preguntan por el rol.
 * <pre>{@code
 * @PreAuthorize("@permisos.tiene('seguridad.cuentas')")
 * }</pre>
 * Los códigos están en la tabla {@code permisos} (migración V4) y se asignan por rol en {@code rol_permisos}.
 */
@Component("permisos")
public class Permisos {

    private final SesionActual sesion;

    public Permisos(SesionActual sesion) {
        this.sesion = sesion;
    }

    public boolean tiene(String codigo) {
        return sesion.buscar().map(u -> u.tienePermiso(codigo)).orElse(false);
    }
}
