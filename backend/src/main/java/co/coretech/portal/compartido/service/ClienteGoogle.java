package co.coretech.portal.compartido.service;

/**
 * Llamadas a Google para el correo del sistema (D-51): OAuth 2.0 y la API de Gmail. Es una interfaz para que las
 * pruebas no salgan a internet.
 */
public interface ClienteGoogle {

    /** Permiso solo para enviar correos (no puede leer el buzón), más el correo de la cuenta. */
    String ALCANCES = "openid email https://www.googleapis.com/auth/gmail.send";
    String ALCANCE_ENVIAR = "https://www.googleapis.com/auth/gmail.send";

    /** URL de la pantalla de Google donde la persona inicia sesión y acepta el permiso. */
    String urlAutorizacion(String redirect, String estado, String retoPkce);

    /** Cambia el código que devuelve Google por los tokens. */
    Tokens intercambiar(String codigo, String verificadorPkce, String redirect);

    /** Correo de la cuenta dueña del token (OpenID Connect userinfo). */
    String correoDe(String tokenAcceso);

    /** Token de acceso nuevo (dura ~1 hora). Lanza {@link PermisoRetiradoException} si el permiso ya no vale. */
    TokenAcceso refrescar(String tokenRefresco);

    /** Envía el correo ya armado (MIME) por la API de Gmail. */
    void enviar(String tokenAcceso, byte[] mime);

    /** Retira el permiso en Google (al desconectar). Si falla, no importa: la conexión se borra igual. */
    void revocar(String tokenRefresco);

    record Tokens(String tokenRefresco, String tokenAcceso, long expiraEnSegundos, String alcances) {

        @Override
        public String toString() {
            return "Tokens[alcances=" + alcances + "]";
        }
    }

    record TokenAcceso(String valor, long expiraEnSegundos) {

        @Override
        public String toString() {
            return "TokenAcceso[expiraEnSegundos=" + expiraEnSegundos + "]";
        }
    }

    /** Google ya no acepta el permiso: la persona lo quitó, cambió la contraseña o venció. Hay que reconectar. */
    class PermisoRetiradoException extends RuntimeException {

        public PermisoRetiradoException(String mensaje) {
            super(mensaje);
        }
    }

    /** Google respondió con un error (o no respondió). */
    class ErrorGoogleException extends RuntimeException {

        public ErrorGoogleException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
