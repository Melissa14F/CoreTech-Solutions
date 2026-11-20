package co.coretech.portal.compartido.aplicacion;

/** Credenciales incorrectas o sesión vencida. → 401 */
public class NoAutenticadoException extends ExcepcionNegocio {

    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
