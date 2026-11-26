package co.coretech.portal.compartido.exception;

/** Credenciales incorrectas o sesión vencida. → 401 */
public class NoAutenticadoException extends ExcepcionNegocio {

    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
