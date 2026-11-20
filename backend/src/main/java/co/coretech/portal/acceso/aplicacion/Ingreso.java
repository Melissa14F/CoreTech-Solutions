package co.coretech.portal.acceso.aplicacion;

import java.util.UUID;

/** Resultado de registrarse o iniciar sesión: con esto el controlador abre la sesión (cookie). */
public record Ingreso(UUID usuarioId, String rol, String nombre, boolean debeCambiarPassword, int versionSesion) {
}
