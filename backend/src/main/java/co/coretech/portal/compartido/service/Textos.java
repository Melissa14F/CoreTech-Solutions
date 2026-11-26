package co.coretech.portal.compartido.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilidades de texto que comparten los servicios: antes cada uno tenía su propia copia. Son funciones puras, sin estado.
 */
public final class Textos {

    /** Largo máximo del texto de un aviso (columna {@code notificaciones.mensaje}). */
    public static final int MAXIMO_AVISO = 255;

    private Textos() {
    }

    /** Corta el texto a {@code maximo} caracteres; si lo corta, el último es «…». */
    public static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    /** Corta el texto al largo máximo de un aviso. */
    public static String recortar(String texto) {
        return recortar(texto, MAXIMO_AVISO);
    }

    /** El texto sin espacios en los extremos; {@code null} se vuelve vacío. */
    public static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }

    /** Minúsculas y sin tildes, para buscar sin que importen los acentos. */
    public static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
