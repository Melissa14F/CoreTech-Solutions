package co.coretech.portal.compartido.aplicacion;

import java.util.Objects;

/**
 * Un correo listo para enviar.
 *
 * @param para   dirección del destinatario
 * @param asunto asunto
 * @param texto  versión en texto plano (la leen los programas de correo que no muestran HTML)
 * @param html   versión HTML, o {@code null} si el correo es solo texto
 */
public record Correo(String para, String asunto, String texto, String html) {

    public Correo {
        Objects.requireNonNull(para, "Falta el destinatario del correo");
        Objects.requireNonNull(asunto, "Falta el asunto del correo");
        Objects.requireNonNull(texto, "Falta el texto del correo");
    }

    public static Correo soloTexto(String para, String asunto, String texto) {
        return new Correo(para, asunto, texto, null);
    }
}
