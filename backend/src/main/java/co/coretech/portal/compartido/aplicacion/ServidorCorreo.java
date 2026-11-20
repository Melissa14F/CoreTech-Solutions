package co.coretech.portal.compartido.aplicacion;

/**
 * Entrega un correo en el mismo hilo y espera la respuesta: por Gmail si el correo del sistema está conectado
 * (D-51) o por SMTP (Mailpit en desarrollo). Si no se pudo, lanza {@link CorreoNoEnviadoException}: quien lo llama
 * decide si reintenta.
 * <p>
 * No lo uses dentro de una petición del usuario (la haría esperar). Lo usan la cola de {@code notificaciones}
 * ({@code comunicacion}) y {@link CorreoSaliente}, que trabajan en segundo plano. La única excepción es el botón
 * "Enviar correo de prueba" del administrador, que necesita la respuesta en pantalla.
 */
public interface ServidorCorreo {

    void enviar(Correo correo);

    /** El correo no salió. El mensaje sirve para {@code notificaciones.ultimo_error}. */
    class CorreoNoEnviadoException extends RuntimeException {

        public CorreoNoEnviadoException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    /**
     * No hay por dónde enviar: el correo del sistema no está conectado (o Google retiró el permiso). No es culpa del
     * correo: la cola no gasta sus intentos y espera a que un administrador conecte Gmail.
     */
    class CorreoSinConexionException extends CorreoNoEnviadoException {

        public CorreoSinConexionException(String mensaje) {
            super(mensaje, null);
        }
    }
}
