package co.coretech.portal.compartido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import co.coretech.portal.compartido.service.ServidorCorreo.CorreoNoEnviadoException;

/** D-49: los correos directos (enlace de recuperación, contraseña temporal) se reintentan en memoria, 3 intentos. */
class CorreoSalienteSmtpTest {

    static final Instant AHORA = Instant.parse("2026-11-19T15:00:00Z");

    ServidorCorreo servidor = mock(ServidorCorreo.class);
    TaskScheduler planificador = mock(TaskScheduler.class);
    /** Tareas que el planificador "programó", con su hora. */
    Deque<Runnable> programadas = new ArrayDeque<>();
    List<Instant> horas = new ArrayList<>();
    CorreoSalienteSmtp saliente;

    @BeforeEach
    void preparar() {
        when(planificador.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(i -> {
            programadas.add(i.getArgument(0));
            horas.add(i.getArgument(1));
            return null;
        });
        saliente = new CorreoSalienteSmtp(servidor, planificador, Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    @Test
    void elPrimerIntentoSaleDeInmediatoYNoBloquea() {
        saliente.enviar("laura@correo.com", "Asunto", "Texto");

        verify(servidor, times(0)).enviar(any());
        assertThat(horas).containsExactly(AHORA);

        correrSiguiente();
        verify(servidor).enviar(Correo.soloTexto("laura@correo.com", "Asunto", "Texto"));
        assertThat(programadas).as("si sale bien, no hay reintentos").isEmpty();
    }

    @Test
    void siFallaReintentaAlMinutoYALosCincoMinutosYLuegoSeRinde() {
        doThrow(new CorreoNoEnviadoException("Connection refused", null)).when(servidor).enviar(any());

        saliente.enviar("laura@correo.com", "Asunto", "Texto");
        correrSiguiente();
        correrSiguiente();
        correrSiguiente();

        verify(servidor, times(3)).enviar(any());
        assertThat(horas).containsExactly(AHORA, AHORA.plus(Duration.ofMinutes(1)), AHORA.plus(Duration.ofMinutes(5)));
        assertThat(programadas).as("después del tercer intento no se programa otro").isEmpty();
    }

    @Test
    void siElSegundoIntentoFuncionaNoHayTercero() {
        doThrow(new CorreoNoEnviadoException("Timeout", null)).doNothing().when(servidor).enviar(any());

        saliente.enviar("laura@correo.com", "Asunto", "Texto");
        correrSiguiente();
        correrSiguiente();

        verify(servidor, times(2)).enviar(any());
        assertThat(programadas).isEmpty();
    }

    private void correrSiguiente() {
        assertThat(programadas).as("debería haber un intento programado").isNotEmpty();
        programadas.poll().run();
    }
}
