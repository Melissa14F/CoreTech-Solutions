package co.coretech.portal.compartido.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Arma el mensaje MIME (texto y, si hay, HTML) igual para Mailpit (SMTP) y para Gmail (API). */
final class ArmadoCorreo {

    private ArmadoCorreo() {
    }

    static void llenar(MimeMessage mensaje, Correo datos, String remitente) throws MessagingException {
        MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, datos.html() != null, StandardCharsets.UTF_8.name());
        ayudante.setFrom(remitente);
        ayudante.setTo(datos.para());
        ayudante.setSubject(datos.asunto());
        if (datos.html() != null) {
            ayudante.setText(datos.texto(), datos.html());
        } else {
            ayudante.setText(datos.texto());
        }
    }

    /** El correo completo en bytes, como lo pide la API de Gmail. */
    static byte[] bytes(Correo datos, String remitente) {
        try {
            MimeMessage mensaje = new MimeMessage(Session.getInstance(new Properties()));
            llenar(mensaje, datos, remitente);
            mensaje.saveChanges();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            mensaje.writeTo(salida);
            return salida.toByteArray();
        } catch (MessagingException | IOException e) {
            throw new ServidorCorreo.CorreoNoEnviadoException("No se pudo armar el correo: " + e.getMessage(), e);
        }
    }
}
