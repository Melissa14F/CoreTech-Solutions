package co.coretech.portal.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import co.coretech.portal.agenda.service.ServicioCitas;
import co.coretech.portal.agenda.service.ServicioCitas.DatosCita;
import co.coretech.portal.agenda.service.ServicioDisponibilidad;
import co.coretech.portal.agenda.repository.CitaRepositorio;
import co.coretech.portal.agenda.repository.HorarioLaboral;
import co.coretech.portal.agenda.repository.HorarioLaboral.Tramo;
import co.coretech.portal.agenda.domain.Cita;
import co.coretech.portal.agenda.domain.EstadoCita;
import co.coretech.portal.agenda.domain.Modalidad;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.repository.DiasNoHabilesRepositorio;

/**
 * Pruebas UNITARIAS de {@link ServicioCitas} (HU-01 y HU-08): la lógica y las reglas de negocio (RN-01, RN-02, RN-03, RN-11)
 * aisladas de la base de datos. Los repositorios, el catálogo, la auditoría y el publicador de eventos son simulados con
 * Mockito (@Mock); no hay contexto de Spring ni base de datos. Cada prueba sigue el patrón AAA:
 * <b>Arrange</b> (preparar), <b>Act</b> (ejecutar) y <b>Assert</b> (comprobar).
 * <p>
 * El reloj es fijo: martes 2031-03-04 a las 08:00:00 hora de Colombia. {@link ServicioDisponibilidad} (la regla RN-01) NO se
 * simula: se usa la real con su horario de lunes a viernes de 8 a 12 y de 14 a 18, para probar los valores límite de verdad.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioCitasUnitarioTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    /** Martes 4 de marzo de 2031, 8:00:00 a. m. en Colombia. */
    static final Instant AHORA = OffsetDateTime.of(2031, 3, 4, 8, 0, 0, 0, ZoneOffset.ofHours(-5)).toInstant();
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID SERVICIO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final String DESCRIPCION = "Necesito un sistema para llevar el inventario de mi salón de belleza.";
    static final String MIERCOLES_8AM = "2031-03-05T08:00:00-05:00";

    @Mock
    CitaRepositorio citas;
    @Mock
    DirectorioServicios catalogo;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    ApplicationEventPublisher eventos;
    @Mock
    HorarioLaboral horario;
    @Mock
    DiasNoHabilesRepositorio festivos;

    @BeforeEach
    void preparaLoComun() {
        List<Tramo> jornada = List.of(new Tramo(LocalTime.of(8, 0), LocalTime.of(12, 0)), new Tramo(LocalTime.of(14, 0), LocalTime.of(18, 0)));
        when(horario.tramos()).thenReturn(Map.of(1, jornada, 2, jornada, 3, jornada, 4, jornada, 5, jornada));
        when(festivos.entre(any(), any())).thenReturn(Set.of());
        when(catalogo.nombreSiVisible(SERVICIO)).thenReturn(Optional.of("Sistema de inventario"));
        when(citas.siguienteCodigo()).thenReturn("C-0099");
        when(citas.saveAndFlush(any(Cita.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** El servicio bajo prueba, con el reloj fijo en {@code ahora} y la regla RN-01 real. */
    private ServicioCitas servicio(Instant ahora) {
        Clock reloj = Clock.fixed(ahora, COLOMBIA);
        ServicioDisponibilidad disponibilidad = new ServicioDisponibilidad(horario, festivos, citas, reloj);
        return new ServicioCitas(citas, catalogo, disponibilidad, auditoria, eventos, reloj);
    }

    private static DatosCita datos(String fechaHora) {
        return new DatosCita(SERVICIO, fechaHora, "VIRTUAL", DESCRIPCION, "Colombia", "America/Bogota");
    }

    // ------------------------------------------------------------------------------------------------ camino feliz

    @Nested
    @DisplayName("Camino feliz (201)")
    class CaminoFeliz {

        @Test
        @DisplayName("agendar crea la cita PENDIENTE, la guarda una vez, la audita y publica un solo aviso")
        void agendaLaCitaPendienteYAvisaUnaSolaVez() {
            // Arrange
            ServicioCitas servicio = servicio(AHORA);

            // Act
            ServicioCitas.CitaConServicio resultado = servicio.agendar(CLIENTE, datos(MIERCOLES_8AM));

            // Assert: estado inicial y datos de la cita
            assertThat(resultado.cita().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
            assertThat(resultado.cita().getCodigo()).isEqualTo("C-0099");
            assertThat(resultado.cita().getFechaHora()).isEqualTo(OffsetDateTime.parse(MIERCOLES_8AM).toInstant());
            assertThat(resultado.servicio()).isEqualTo("Sistema de inventario");
            // Assert: efectos
            verify(citas, times(1)).saveAndFlush(any(Cita.class));
            verify(auditoria, times(1)).registrar(any(), anyString(), anyString(), anyString(), any());
            ArgumentCaptor<AvisoSolicitado> aviso = ArgumentCaptor.forClass(AvisoSolicitado.class);
            verify(eventos, times(1)).publishEvent(aviso.capture());
            assertThat(aviso.getValue().tipo()).isEqualTo("CITA_REGISTRADA");
            assertThat(aviso.getValue().destinatarios()).containsExactly(CLIENTE);
        }
    }

    // ------------------------------------------------------------------------------------------------ RN-03

    @Nested
    @DisplayName("RN-03: no dos citas a la misma hora para un cliente (409)")
    class LimiteDeOperaciones {

        @Test
        @DisplayName("si el cliente ya tiene una cita a esa hora lanza ConflictoException y NO guarda")
        void rechazaSiElClienteYaTieneCitaALaMismaHora() {
            // Arrange
            when(citas.clienteTieneCita(CLIENTE, OffsetDateTime.parse(MIERCOLES_8AM).toInstant())).thenReturn(true);
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, datos(MIERCOLES_8AM)))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessage("Ya tienes una cita");
            verify(citas, never()).saveAndFlush(any(Cita.class));
            verify(eventos, never()).publishEvent(any(Object.class));
        }
    }

    // ------------------------------------------------------------------------------------------------ RN-01

    @Nested
    @DisplayName("RN-01: anticipación mínima de 24 h y máxima de 30 días (422), valores límite")
    class ReglasDeTiempo {

        @Test
        @DisplayName("exactamente 24 horas antes SÍ se acepta (límite inferior)")
        void aceptaUnaFranjaExactamente24HorasDespues() {
            // Arrange: ahora = martes 08:00:00; la franja es el miércoles 08:00:00 (24 h 00 min)
            ServicioCitas servicio = servicio(AHORA);

            // Act
            ServicioCitas.CitaConServicio resultado = servicio.agendar(CLIENTE, datos(MIERCOLES_8AM));

            // Assert
            assertThat(resultado.cita().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
            verify(citas, times(1)).saveAndFlush(any(Cita.class));
        }

        @Test
        @DisplayName("23 h 59 min 59 s antes se rechaza (un segundo por debajo del límite)")
        void rechazaUnaFranjaA23Horas59Minutos59Segundos() {
            // Arrange: ahora = martes 08:00:01; la franja del miércoles 08:00:00 queda a 23 h 59 min 59 s
            ServicioCitas servicio = servicio(AHORA.plusSeconds(1));

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, datos(MIERCOLES_8AM)))
                    .isInstanceOf(ValidacionException.class)
                    .satisfies(e -> assertThat(((ValidacionException) e).fallas())
                            .extracting(ValidacionException.Falla::campo).containsExactly("fechaHora"));
            verify(citas, never()).saveAndFlush(any(Cita.class));
            verify(eventos, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("24 h 01 min antes se acepta y 1 h 01 min antes se rechaza")
        void comparaUnaFranjaAceptadaYOtraRechazada() {
            // Arrange: ahora = martes 07:59:00. El miércoles 08:00 queda a 24 h 01 min; el martes 09:00, a 1 h 01 min.
            ServicioCitas servicio = servicio(AHORA.minusSeconds(60));

            // Act
            ServicioCitas.CitaConServicio aceptada = servicio.agendar(CLIENTE, datos(MIERCOLES_8AM));

            // Assert
            assertThat(aceptada.cita().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, datos("2031-03-04T09:00:00-05:00")))
                    .isInstanceOf(ValidacionException.class);
            verify(citas, times(1)).saveAndFlush(any(Cita.class));
        }

        @Test
        @DisplayName("más de 30 días hacia adelante se rechaza")
        void rechazaUnaFranjaAMasDeTreintaDias() {
            // Arrange: martes 2031-04-08 está a 35 días de ahora
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, datos("2031-04-08T08:00:00-05:00")))
                    .isInstanceOf(ValidacionException.class);
            verify(citas, never()).saveAndFlush(any(Cita.class));
        }

        @Test
        @DisplayName("una hora que no empieza en punto (08:30) se rechaza: las franjas son de 1 hora exacta")
        void rechazaUnaHoraQueNoEmpiezaEnPunto() {
            // Arrange
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, datos("2031-03-05T08:30:00-05:00")))
                    .isInstanceOf(ValidacionException.class);
            verify(citas, never()).saveAndFlush(any(Cita.class));
        }

        @Test
        @DisplayName("un sábado se rechaza")
        void rechazaUnFinDeSemana() {
            // Arrange: 2031-03-08 es sábado
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, datos("2031-03-08T09:00:00-05:00")))
                    .isInstanceOf(ValidacionException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ RN-02

    @Nested
    @DisplayName("RN-02: una franja solo admite una cita viva (409)")
    class ChoqueDeDisponibilidad {

        @Test
        @DisplayName("si la franja ya está ocupada lanza ConflictoException con el mensaje de la pantalla y NO guarda")
        void lanzaConflictoSiLaFranjaYaEstaOcupada() {
            // Arrange
            when(citas.franjaOcupada(OffsetDateTime.parse(MIERCOLES_8AM).toInstant())).thenReturn(true);
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, datos(MIERCOLES_8AM)))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessage("La hora que elegiste ya no está disponible, elige otra");
            verify(citas, never()).saveAndFlush(any(Cita.class));
        }
    }

    // ------------------------------------------------------------------------------------------------ 404 y 422

    @Nested
    @DisplayName("Recurso inexistente (404) y datos inválidos (422)")
    class RecursoInexistente {

        @Test
        @DisplayName("un servicio que no existe o está oculto lanza NoEncontradoException")
        void servicioInexistenteOOculto() {
            // Arrange
            UUID oculto = UUID.randomUUID();
            when(catalogo.nombreSiVisible(oculto)).thenReturn(Optional.empty());
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, new DatosCita(oculto, MIERCOLES_8AM, "VIRTUAL", DESCRIPCION, "Colombia", "America/Bogota")))
                    .isInstanceOf(NoEncontradoException.class)
                    .hasMessage("Este servicio ya no está disponible");
            verify(citas, never()).saveAndFlush(any(Cita.class));
        }

        @Test
        @DisplayName("cancelar una cita que no es del cliente (o no existe) responde 404 y no revela que existe")
        void cancelarUnaCitaAjenaEs404() {
            // Arrange
            UUID ajena = UUID.randomUUID();
            when(citas.findByIdAndClienteId(ajena, CLIENTE)).thenReturn(Optional.empty());
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.cancelar(CLIENTE, ajena, null))
                    .isInstanceOf(NoEncontradoException.class)
                    .hasMessage("No encontramos esa cita");
            verify(eventos, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("la descripción corta, sin modalidad ni país da un error por campo (422)")
        void datosInvalidosDanUnErrorPorCampo() {
            // Arrange
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.agendar(CLIENTE, new DatosCita(SERVICIO, MIERCOLES_8AM, null, "corta", null, null)))
                    .isInstanceOf(ValidacionException.class)
                    .satisfies(e -> assertThat(((ValidacionException) e).fallas())
                            .extracting(ValidacionException.Falla::campo)
                            .containsExactlyInAnyOrder("modalidad", "descripcionNecesidad", "pais"));
            verify(citas, never()).saveAndFlush(any(Cita.class));
        }
    }

    // ------------------------------------------------------------------------------------------------ cancelar y eventos

    @Nested
    @DisplayName("RN-11 y publicación de eventos")
    class CancelarYEventos {

        private Cita citaDe(EstadoCita estado) {
            Cita c = Cita.nueva("C-0050", CLIENTE, SERVICIO, OffsetDateTime.parse(MIERCOLES_8AM).toInstant(), Modalidad.VIRTUAL,
                    DESCRIPCION, "Colombia", "America/Bogota", AHORA);
            if (estado == EstadoCita.CANCELADA) {
                c.cancelar(CLIENTE, null, AHORA);
            }
            return c;
        }

        @Test
        @DisplayName("cancelar una cita PENDIENTE la deja CANCELADA, la guarda y publica el aviso exactamente una vez")
        void cancelaUnaCitaPendienteYPublicaElAviso() {
            // Arrange
            Cita pendiente = citaDe(EstadoCita.PENDIENTE);
            when(citas.findByIdAndClienteId(pendiente.getId(), CLIENTE)).thenReturn(Optional.of(pendiente));
            ServicioCitas servicio = servicio(AHORA);

            // Act
            Cita resultado = servicio.cancelar(CLIENTE, pendiente.getId(), "Ya no necesito la cita, gracias.");

            // Assert
            assertThat(resultado.getEstado()).isEqualTo(EstadoCita.CANCELADA);
            verify(citas, times(1)).saveAndFlush(pendiente);
            ArgumentCaptor<AvisoSolicitado> aviso = ArgumentCaptor.forClass(AvisoSolicitado.class);
            verify(eventos, times(1)).publishEvent(aviso.capture());
            assertThat(aviso.getValue().tipo()).isEqualTo("CITA_CANCELADA");
        }

        @Test
        @DisplayName("una cita ya cancelada no se vuelve a cancelar (409) y NO se publica ningún evento")
        void unaCitaYaCanceladaDa409YNoPublicaEventos() {
            // Arrange
            Cita cancelada = citaDe(EstadoCita.CANCELADA);
            when(citas.findByIdAndClienteId(cancelada.getId(), CLIENTE)).thenReturn(Optional.of(cancelada));
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.cancelar(CLIENTE, cancelada.getId(), null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessage("Esta cita ya fue cancelada");
            verify(citas, never()).saveAndFlush(any(Cita.class));
            verify(eventos, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("un motivo de menos de 15 caracteres da 422 antes de buscar la cita")
        void motivoMuyCortoDa422() {
            // Arrange
            ServicioCitas servicio = servicio(AHORA);

            // Act + Assert
            assertThatThrownBy(() -> servicio.cancelar(CLIENTE, UUID.randomUUID(), "corto"))
                    .isInstanceOf(ValidacionException.class);
            verify(citas, never()).findByIdAndClienteId(any(), any());
        }
    }

}
