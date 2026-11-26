package co.coretech.portal.acceso.service;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import co.coretech.portal.compartido.exception.ReglaNegocioException;

/**
 * RN-22: mínimo 8 caracteres, con al menos una mayúscula, un número y un símbolo, y confirmada en un
 * segundo campo. Se aplica al registrarse, al cambiarla y al restablecerla.
 */
@Component
class PoliticaContrasena {

    static final String NO_CUMPLE =
            "La contraseña aún no cumple todos los requisitos: mínimo 8 caracteres, una mayúscula, un número y un símbolo.";
    static final String NO_COINCIDEN = "Las contraseñas no coinciden.";
    static final String DEMASIADO_LARGA = "La contraseña es demasiado larga: usa máximo 64 caracteres.";
    /** BCrypt solo usa los primeros 72 bytes y, desde Spring Security 6.3, rechaza las más largas (sería un 500). */
    static final int MAX_BYTES = 72;

    void validar(String password, String confirmacion, String campoPassword, String campoConfirmacion) {
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ReglaNegocioException(campoPassword, DEMASIADO_LARGA);
        }
        if (!cumple(password)) {
            throw new ReglaNegocioException(campoPassword, NO_CUMPLE);
        }
        if (!password.equals(confirmacion)) {
            throw new ReglaNegocioException(campoConfirmacion, NO_COINCIDEN);
        }
    }

    static boolean cumple(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean mayuscula = false;
        boolean numero = false;
        boolean simbolo = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            mayuscula |= Character.isUpperCase(c);
            numero |= Character.isDigit(c);
            simbolo |= !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
        }
        return mayuscula && numero && simbolo;
    }
}
