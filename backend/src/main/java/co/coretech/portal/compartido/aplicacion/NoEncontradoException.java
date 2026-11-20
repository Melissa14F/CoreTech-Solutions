package co.coretech.portal.compartido.aplicacion;

/** El recurso no existe o pertenece a otro cliente (RN-10: a un cliente nunca se le revela que existe). → 404 */
public class NoEncontradoException extends ExcepcionNegocio {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
