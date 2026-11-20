package co.coretech.portal.comunicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * El correo de un aviso sale en cuanto se guarda (por ejemplo, la bienvenida al crear la cuenta), sin esperar la pasada
 * de 15 segundos de la cola, y sin que la petición del usuario espere al servidor de correo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@TestPropertySource(properties = "coretech.correos.inmediato=true")
class EnvioInmediatoTest {

    static final String CLAVE = "Segura2026!";
    /** El servidor de correo "tarda" esto: la petición no debe esperarlo. */
    static final long TARDANZA_DEL_SERVIDOR_MS = 3000;

    @MockitoBean
    JavaMailSender servidorSmtp;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    final List<String> destinatarios = new CopyOnWriteArrayList<>();

    @BeforeEach
    void servidorLento() throws Exception {
        reset(servidorSmtp);
        destinatarios.clear();
        when(servidorSmtp.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> {
            MimeMessage m = i.getArgument(0);
            Thread.sleep(TARDANZA_DEL_SERVIDOR_MS);
            destinatarios.add(m.getRecipients(Message.RecipientType.TO)[0].toString());
            return null;
        }).when(servidorSmtp).send(any(MimeMessage.class));
    }

    @Test
    void laBienvenidaSaleAlCrearLaCuentaYElRegistroNoEsperaAlServidorDeCorreo() throws Exception {
        String email = "inmediato-" + UUID.randomUUID().toString().substring(0, 8) + "@empresa-cliente.com";

        long inicio = System.nanoTime();
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(registro(email, true)))
                .andExpect(status().isCreated());
        long milisegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(milisegundos).as("el registro responde sin esperar el correo").isLessThan(TARDANZA_DEL_SERVIDOR_MS - 500);
        // El correo sale por su cuenta, sin que nadie llame a la cola
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(destinatarios).contains(email));
        // Se marca como enviado justo después de que el servidor responde
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT n.enviada_email FROM notificaciones n JOIN usuarios u ON u.id = n.usuario_id
                WHERE u.email = ? AND n.tipo = 'CUENTA_CREADA'
                """, Boolean.class, email)).isTrue());
    }

    @Test
    void unRegistroRechazadoNoEnviaNada() throws Exception {
        String email = "rechazado-" + UUID.randomUUID().toString().substring(0, 8) + "@empresa-cliente.com";

        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(registro(email, false)))
                .andExpect(status().isUnprocessableEntity());

        Thread.sleep(1000);
        assertThat(destinatarios).doesNotContain(email);
    }

    private static String registro(String email, boolean acepta) {
        return """
                {"nombre":"Sofía Duarte","email":"%s","celular":"3142208815","empresa":"","password":"%s","confirmacion":"%s","aceptaDatos":%s}
                """.formatted(email, CLAVE, CLAVE, acepta);
    }
}
