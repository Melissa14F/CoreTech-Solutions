package co.coretech.portal.compartido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import co.coretech.portal.compartido.service.ServidorCorreo.CorreoSinConexionException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** D-51: por dónde sale cada correo (Gmail conectado, Mailpit o ninguno) y los destinos permitidos en desarrollo. */
class ServidorCorreoEnrutadoTest {

    static final Correo A_HELIX = Correo.soloTexto("helix.jmdev2026@gmail.com", "Asunto", "Texto");
    static final Correo A_LAURA = Correo.soloTexto("laura.gomez@correo.com", "Asunto", "Texto");

    JavaMailSender smtp = mock(JavaMailSender.class);
    ConexionGmail gmail = mock(ConexionGmail.class);

    ServidorCorreoEnrutado servidor(JavaMailSender conSmtp, List<String> permitidos) {
        when(smtp.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        return new ServidorCorreoEnrutado(conSmtp, gmail, "CoreTech Solutions <no-responder@coretech.local>", permitidos);
    }

    @Test
    void conGmailConectadoYSinListaTodoSalePorGmail() {
        when(gmail.configurado()).thenReturn(true);
        when(gmail.conectada()).thenReturn(true);
        ServidorCorreoEnrutado s = servidor(null, List.of());

        s.enviar(A_LAURA);

        verify(gmail).enviar(A_LAURA);
        assertThat(s.canal("laura.gomez@correo.com")).isEqualTo("GMAIL");
    }

    @Test
    void enDesarrolloGmailSoloEscribeALosPermitidosYLoDemasVaAMailpit() {
        when(gmail.configurado()).thenReturn(true);
        when(gmail.conectada()).thenReturn(true);
        ServidorCorreoEnrutado s = servidor(smtp, List.of(" Helix.JMDev2026@gmail.com ", "@coretechsolutions.co", ""));

        s.enviar(A_HELIX);
        s.enviar(A_LAURA);

        verify(gmail).enviar(A_HELIX);
        verify(gmail, never()).enviar(A_LAURA);
        verify(smtp).send(any(MimeMessage.class));
        assertThat(s.permitido("andres.perez@coretechsolutions.co")).isTrue();
        assertThat(s.permitido("otra@gmail.com")).isFalse();
        assertThat(s.canal("laura.gomez@correo.com")).isEqualTo("SMTP");
    }

    @Test
    void enDesarrolloElClienteQueSeRegistraConSuCorreoLoRecibeYLasCuentasDemoNo() {
        when(gmail.configurado()).thenReturn(true);
        when(gmail.conectada()).thenReturn(true);
        when(smtp.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        ServidorCorreoEnrutado s = new ServidorCorreoEnrutado(smtp, gmail, "CoreTech Solutions <no-responder@coretech.local>",
                List.of(), List.of("@correo.com", "@CoreTechSolutions.co"));
        Correo aClienteNuevo = Correo.soloTexto("cliente.real@gmail.com", "Asunto", "Texto");
        Correo aPatricia = Correo.soloTexto("patricia.leon@coretechsolutions.co", "Asunto", "Texto");

        s.enviar(aClienteNuevo);
        s.enviar(A_LAURA);
        s.enviar(aPatricia);

        verify(gmail).enviar(aClienteNuevo);
        verify(gmail, never()).enviar(A_LAURA);
        verify(gmail, never()).enviar(aPatricia);
        assertThat(s.canal("cliente.real@gmail.com")).isEqualTo("GMAIL");
        assertThat(s.canal("laura.gomez@correo.com")).isEqualTo("SMTP");
    }

    @Test
    void sinGmailConectadoUsaMailpit() {
        when(gmail.conectada()).thenReturn(false);
        ServidorCorreoEnrutado s = servidor(smtp, List.of());

        s.enviar(A_LAURA);

        verify(smtp).send(any(MimeMessage.class));
        verify(gmail, never()).enviar(any());
    }

    @Test
    void enProduccionSinGmailConectadoLosCorreosEsperan() {
        when(gmail.configurado()).thenReturn(true);
        when(gmail.conectada()).thenReturn(false);
        ServidorCorreoEnrutado s = servidor(null, List.of());

        assertThatThrownBy(() -> s.enviar(A_LAURA)).isInstanceOf(CorreoSinConexionException.class)
                .hasMessageContaining("conectarlo con Gmail");
        assertThat(s.canal("laura.gomez@correo.com")).isNull();
    }

    @Test
    void sinSmtpNiClienteDeGoogleLaApiNoArranca() {
        when(gmail.configurado()).thenReturn(false);
        assertThatThrownBy(() -> servidor(null, List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORETECH_GOOGLE_ID");
    }
}
