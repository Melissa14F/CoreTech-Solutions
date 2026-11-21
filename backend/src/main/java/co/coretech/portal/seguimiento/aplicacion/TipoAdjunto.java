package co.coretech.portal.seguimiento.aplicacion;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tipos de adjunto de un paso (HU-03): PNG, JPG, SVG y PDF. Se reconocen por el contenido del archivo, nunca por la
 * extensión ni por el tipo que declara el navegador: un .exe renombrado a .png no pasa.
 * <p>
 * Un SVG es texto con etiquetas y puede traer código. Se acepta solo si es texto UTF-8 que empieza como SVG y no contiene
 * scripts, manejadores de eventos ({@code onload=…}), {@code javascript:}, {@code foreignObject} ni etiquetas que incrusten
 * otras páginas. Aun así, la descarga lo sirve con una política que impide ejecutar nada.
 */
public enum TipoAdjunto {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    SVG("svg", "image/svg+xml"),
    PDF("pdf", "application/pdf");

    private static final Pattern MANEJADOR_DE_EVENTO = Pattern.compile("[\\s\"'/]on[a-z]+\\s*=");
    private static final int MUESTRA_SVG = 4096;

    private final String extension;
    private final String tipoMime;

    TipoAdjunto(String extension, String tipoMime) {
        this.extension = extension;
        this.tipoMime = tipoMime;
    }

    public String extension() {
        return extension;
    }

    public String tipoMime() {
        return tipoMime;
    }

    /** El tipo según los primeros bytes (o, en un SVG, el texto completo); vacío si no es ninguno de los cuatro o no es seguro. */
    public static Optional<TipoAdjunto> reconocer(byte[] c) {
        if (c.length >= 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G' && c[4] == 0x0D && c[5] == 0x0A
                && c[6] == 0x1A && c[7] == 0x0A) {
            return Optional.of(PNG);
        }
        if (c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (c.length >= 5 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F' && c[4] == '-') {
            return Optional.of(PDF);
        }
        return esSvgSeguro(c) ? Optional.of(SVG) : Optional.empty();
    }

    private static boolean esSvgSeguro(byte[] contenido) {
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(contenido)).toString();
        } catch (CharacterCodingException e) {
            return false;
        }
        String limpio = texto.startsWith("﻿") ? texto.substring(1) : texto;
        String minusculas = limpio.toLowerCase(Locale.ROOT);
        String inicio = minusculas.stripLeading();
        boolean empiezaComoSvg = inicio.startsWith("<svg") || inicio.startsWith("<?xml") || inicio.startsWith("<!doctype svg")
                || inicio.startsWith("<!--");
        if (!empiezaComoSvg || !minusculas.substring(0, Math.min(minusculas.length(), MUESTRA_SVG)).contains("<svg")) {
            return false;
        }
        return !(minusculas.contains("<script") || minusculas.contains("javascript:") || minusculas.contains("<foreignobject")
                || minusculas.contains("<iframe") || minusculas.contains("<embed") || minusculas.contains("<object")
                || minusculas.contains("<!entity") || MANEJADOR_DE_EVENTO.matcher(minusculas).find());
    }
}
