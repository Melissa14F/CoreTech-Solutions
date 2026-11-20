package co.coretech.portal.acceso.dominio;

import java.util.Locale;
import java.util.Optional;

/**
 * Proveedores con los que un cliente puede registrarse e ingresar (RF-50, D-50): OpenID Connect con PKCE.
 * <p>
 * Microsoft usa el inquilino "consumers" (cuentas personales: Outlook, Hotmail, Live), porque Microsoft verifica su
 * correo; en las cuentas de trabajo o estudio el correo puede no estar verificado y se podría usar para entrar a la
 * cuenta de otra persona. Las empresas con Microsoft 365 se registran con su correo y contraseña.
 */
public enum ProveedorExterno {

    GOOGLE("Google",
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://openidconnect.googleapis.com/v1/userinfo"),
    MICROSOFT("Microsoft",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            "https://graph.microsoft.com/oidc/userinfo");

    /** Solo lo necesario para identificar a la persona: nombre y correo (nada del buzón). */
    public static final String ALCANCES = "openid email profile";

    private final String nombre;
    private final String urlAutorizacion;
    private final String urlToken;
    private final String urlUsuario;

    ProveedorExterno(String nombre, String urlAutorizacion, String urlToken, String urlUsuario) {
        this.nombre = nombre;
        this.urlAutorizacion = urlAutorizacion;
        this.urlToken = urlToken;
        this.urlUsuario = urlUsuario;
    }

    /** "google" o "microsoft" en la ruta (sin distinguir mayúsculas). */
    public static Optional<ProveedorExterno> deRuta(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String ruta() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nombre() {
        return nombre;
    }

    public String urlAutorizacion() {
        return urlAutorizacion;
    }

    public String urlToken() {
        return urlToken;
    }

    public String urlUsuario() {
        return urlUsuario;
    }
}
