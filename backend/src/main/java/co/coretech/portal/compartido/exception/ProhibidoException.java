package co.coretech.portal.compartido.exception;

/** El rol del usuario no tiene permiso para la acción. → 403 */
public class ProhibidoException extends ExcepcionNegocio {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}
