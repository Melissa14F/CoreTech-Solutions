package co.coretech.portal.acceso.service;

import co.coretech.portal.acceso.domain.ProveedorExterno;

/**
 * Habla con Google o Microsoft para saber quién es la persona (OpenID Connect con PKCE). Es una interfaz para que las
 * pruebas no salgan a internet.
 */
public interface ClienteIdentidadExterna {

    /** El proveedor tiene su ID y clave de cliente configurados; si no, su botón no aparece. */
    boolean configurado(ProveedorExterno proveedor);

    /** Página del proveedor donde la persona inicia sesión y acepta compartir su nombre y correo. */
    String urlAutorizacion(ProveedorExterno proveedor, String redirect, String estado, String retoPkce);

    /** Cambia el código por la identidad de la persona. Lanza {@link ErrorProveedorException} si el proveedor falla. */
    Identidad identidad(ProveedorExterno proveedor, String codigo, String verificadorPkce, String redirect);

    /**
     * @param sujeto            identificador estable de la persona en el proveedor ("sub")
     * @param email             su correo, o null si no lo compartió
     * @param correoVerificado  el proveedor confirma que el correo es de la persona
     * @param nombre            nombre para mostrar, o null
     */
    record Identidad(String sujeto, String email, boolean correoVerificado, String nombre) {
    }

    class ErrorProveedorException extends RuntimeException {

        public ErrorProveedorException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
