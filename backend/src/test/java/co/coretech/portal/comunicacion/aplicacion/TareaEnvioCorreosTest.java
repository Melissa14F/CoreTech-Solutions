package co.coretech.portal.comunicacion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.compartido.aplicacion.Correo;
import co.coretech.portal.compartido.aplicacion.PlantillasCorreo;
import co.coretech.portal.compartido.aplicacion.ServidorCorreo;
import co.coretech.portal.compartido.aplicacion.ServidorCorreo.CorreoSinConexionException;
import co.coretech.portal.comunicacion.datos.NotificacionRepositorio;
import co.coretech.portal.comunicacion.dominio.Notificacion;

/** D-51: sin el correo del sistema conectado, la cola no gasta los intentos; los correos esperan. */
class TareaEnvioCorreosTest {

    static final Instant AHORA = Instant.parse("2026-11-20T15:00:00Z");

    NotificacionRepositorio repositorio = mock(NotificacionRepositorio.class);
    DirectorioUsuarios usuarios = mock(DirectorioUsuarios.class);
    PlantillasCorreo plantillas = mock(PlantillasCorreo.class);
    ServidorCorreo servidor = mock(ServidorCorreo.class);
    TareaEnvioCorreos cola = new TareaEnvioCorreos(repositorio, usuarios, plantillas, servidor,
            Clock.fixed(AHORA, ZoneId.of("America/Bogota")));

    @Test
    void sinConexionNoGastaIntentosYDejaDeIntentarEnEsaPasada() {
        Notificacion a = Notificacion.nueva(UUID.randomUUID(), "PRUEBA", "Uno", "Texto", null, AHORA);
        Notificacion b = Notificacion.nueva(UUID.randomUUID(), "PRUEBA", "Dos", "Texto", null, AHORA);
        when(repositorio.correosPorEnviar(any(), any(), any())).thenReturn(List.of(a, b));
        when(usuarios.contactoCorreo(any())).thenReturn(Optional.of(new ContactoCorreo("Laura", "laura@correo.com")));
        when(plantillas.aviso(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Correo.soloTexto("laura@correo.com", "Uno", "Texto"));
        doThrow(new CorreoSinConexionException("El correo del sistema no está conectado")).when(servidor).enviar(any());

        assertThat(cola.enviarPendientes()).isZero();

        verify(servidor, times(1)).enviar(any());
        verify(repositorio, never()).registrarFallo(any(), any());
        verify(repositorio, never()).registrarEnvio(any());
    }
}
