package co.coretech.portal.compartido.aplicacion;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Decide por dónde sale cada correo (D-51):
 * <ol>
 * <li>Si el correo del sistema está conectado con Gmail y el destino está permitido → Gmail (correo real).</li>
 * <li>Si hay SMTP configurado (Mailpit en dev y test) → SMTP.</li>
 * <li>Si no → {@link CorreoSinConexionException}: el correo espera a que un administrador conecte Gmail.</li>
 * </ol>
 * Destinos (direcciones o dominios "@dominio.com"): {@code coretech.correos.destinos-bloqueados} nunca reciben por Gmail
 * y {@code destinos-permitidos}, si tiene valores, es la única lista que sí. Lo que Gmail no puede recibir va a Mailpit.
 * En desarrollo se bloquean los dominios de las cuentas demo (@correo.com y @coretechsolutions.co son dominios reales
 * con dueños reales): un cliente que se registra con su propio correo sí recibe el correo real. En producción las
 * dos listas están vacías: Gmail escribe al correo que cada usuario registró.
 * <p>
 * Producción no tiene SMTP: sin el cliente de Google configurado (CORETECH_GOOGLE_ID y CORETECH_GOOGLE_SECRETO) la
 * API no arranca, porque no habría forma de enviar correos.
 */
@Service
class ServidorCorreoEnrutado implements ServidorCorreo {

    private final JavaMailSender smtp;
    private final ConexionGmail gmail;
    private final String remitenteSmtp;
    private final List<String> destinosPermitidos;
    private final List<String> destinosBloqueados;

    @Autowired
    ServidorCorreoEnrutado(ObjectProvider<JavaMailSender> smtp, ConexionGmail gmail,
            @Value("${coretech.correos.remitente}") String remitenteSmtp,
            @Value("${coretech.correos.destinos-permitidos:}") List<String> destinosPermitidos,
            @Value("${coretech.correos.destinos-bloqueados:}") List<String> destinosBloqueados) {
        this(smtp.getIfAvailable(), gmail, remitenteSmtp, destinosPermitidos, destinosBloqueados);
    }

    ServidorCorreoEnrutado(JavaMailSender smtp, ConexionGmail gmail, String remitenteSmtp, List<String> destinosPermitidos) {
        this(smtp, gmail, remitenteSmtp, destinosPermitidos, List.of());
    }

    ServidorCorreoEnrutado(JavaMailSender smtp, ConexionGmail gmail, String remitenteSmtp, List<String> destinosPermitidos,
            List<String> destinosBloqueados) {
        if (smtp == null && !gmail.configurado()) {
            throw new IllegalStateException("No hay cómo enviar correos: define CORETECH_GOOGLE_ID y CORETECH_GOOGLE_SECRETO "
                    + "(cliente OAuth de Google Cloud) para conectar Gmail desde el panel del administrador");
        }
        this.smtp = smtp;
        this.gmail = gmail;
        this.remitenteSmtp = remitenteSmtp;
        this.destinosPermitidos = limpiar(destinosPermitidos);
        this.destinosBloqueados = limpiar(destinosBloqueados);
    }

    private static List<String> limpiar(List<String> destinos) {
        return destinos.stream().map(d -> d.trim().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty()).toList();
    }

    private static boolean coincide(String destino, List<String> lista) {
        return lista.stream().anyMatch(d -> d.startsWith("@") ? destino.endsWith(d) : destino.equals(d));
    }

    @Override
    public void enviar(Correo correo) {
        if (permitido(correo.para()) && gmail.conectada()) {
            gmail.enviar(correo);
        } else if (smtp != null) {
            enviarSmtp(correo);
        } else {
            throw new CorreoSinConexionException("El correo del sistema no está conectado: un administrador debe "
                    + "conectarlo con Gmail en Correo del sistema");
        }
    }

    /** Por dónde saldría un correo a esta dirección: "GMAIL", "SMTP" o null (sin conexión). */
    String canal(String para) {
        if (permitido(para) && gmail.conectada()) {
            return "GMAIL";
        }
        return smtp != null ? "SMTP" : null;
    }

    /** Gmail puede escribirle: no está bloqueado y, si hay lista de permitidos, está en ella. */
    boolean permitido(String para) {
        String destino = para.trim().toLowerCase(Locale.ROOT);
        if (coincide(destino, destinosBloqueados)) {
            return false;
        }
        return destinosPermitidos.isEmpty() || coincide(destino, destinosPermitidos);
    }

    private void enviarSmtp(Correo correo) {
        try {
            MimeMessage mensaje = smtp.createMimeMessage();
            ArmadoCorreo.llenar(mensaje, correo, remitenteSmtp);
            smtp.send(mensaje);
        } catch (MailException | MessagingException e) {
            throw new CorreoNoEnviadoException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
        }
    }
}
