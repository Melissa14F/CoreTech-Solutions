package co.coretech.portal.compartido.exception;

/**
 * Base de las excepciones que lanzan los servicios cuando una regla de negocio impide la acción.
 * <p>
 * No conocen HTTP: el manejador global de errores ({@code compartido.web.ManejadorGlobalErrores})
 * decide el código según el tipo. El mensaje se muestra tal cual al usuario, así que va en español
 * sencillo, sin tecnicismos (los textos salen de las HU y del prototipo).
 */
public abstract class ExcepcionNegocio extends RuntimeException {

    protected ExcepcionNegocio(String mensaje) {
        super(mensaje);
    }
}
