package co.coretech.portal.compartido.exception;

/** La cuenta está bloqueada por intentos fallidos (RNF-10). → 423 */
public class CuentaBloqueadaException extends ExcepcionNegocio {

    public CuentaBloqueadaException(String mensaje) {
        super(mensaje);
    }
}
