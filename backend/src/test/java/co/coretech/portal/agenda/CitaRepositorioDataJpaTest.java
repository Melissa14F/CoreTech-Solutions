package co.coretech.portal.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.agenda.repository.CitaRepositorio;
import co.coretech.portal.agenda.domain.Cita;
import co.coretech.portal.agenda.domain.EstadoCita;
import co.coretech.portal.agenda.domain.Modalidad;

/**
 * Pruebas de PERSISTENCIA con {@code @DataJpaTest}: las consultas {@code @Query} de {@link CitaRepositorio} contra una base de
 * datos real y la regla RN-02 («una franja solo admite una cita viva») que garantiza el índice único parcial
 * {@code ux_cita_franja}.
 * <p>
 * La guía propone H2; aquí la base es <b>PostgreSQL 15 en un contenedor</b> (Testcontainers) porque el esquema usa enums de
 * PostgreSQL ({@code estado_cita}), funciones propias ({@code fn_siguiente_codigo_cita}) e índices parciales, que H2 no
 * reproduce: una prueba contra H2 pasaría con consultas que fallarían en producción. Flyway crea el esquema real (V1…V16).
 * Cada prueba corre dentro de una transacción que se revierte, así que no deja datos.
 * <p>
 * Una cita ocupa UNA HORA exacta que empieza en punto (RN-01). Por eso el «cruce parcial» (19:00-21:00 contra 18:00-20:00) no
 * puede darse: el servicio rechaza cualquier hora que no sea en punto (ver {@code ServicioCitasUnitarioTest}) y aquí se
 * comprueba lo que sí vive en la base: misma hora = choque, hora contigua = libre, cita cancelada = se ignora.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class CitaRepositorioDataJpaTest {

    static final Instant SEIS_PM = OffsetDateTime.parse("2031-03-05T18:00:00-05:00").toInstant();
    static final Instant SIETE_PM = OffsetDateTime.parse("2031-03-05T19:00:00-05:00").toInstant();
    static final Instant SEIS_Y_MEDIA = OffsetDateTime.parse("2031-03-05T18:30:00-05:00").toInstant();

    @Autowired
    CitaRepositorio citas;

    @Autowired
    JdbcTemplate jdbc;

    final UUID cliente = UUID.randomUUID();
    final UUID otroCliente = UUID.randomUUID();
    final UUID servicio = UUID.randomUUID();

    @BeforeEach
    void sinLlavesForaneas() {
        // El cliente y el servicio no son lo que se prueba: se difiere la revisión de llaves foráneas hasta el final de la
        // transacción de la prueba (que se revierte). Los índices únicos y los CHECK sí se revisan al momento.
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
    }

    private Cita guardar(UUID quien, Instant hora) {
        Cita c = Cita.nueva(citas.siguienteCodigo(), quien, servicio, hora, Modalidad.VIRTUAL,
                "Necesito un sistema para llevar el inventario de mi salón de belleza.", "Colombia", "America/Bogota",
                Instant.parse("2031-03-04T13:00:00Z"));
        return citas.saveAndFlush(c);
    }

    private Cita guardarCancelada(UUID quien, Instant hora) {
        Cita c = guardar(quien, hora);
        c.cancelar(quien, null, Instant.parse("2031-03-04T14:00:00Z"));
        return citas.saveAndFlush(c);
    }

    @Test
    @DisplayName("una cita PENDIENTE ocupa su franja: la consulta detecta la MISMA hora")
    void detectaLaMismaFranja() {
        // Arrange: cita de 18:00 a 19:00
        guardar(cliente, SEIS_PM);

        // Act + Assert
        assertThat(citas.franjaOcupada(SEIS_PM)).isTrue();
    }

    @Test
    @DisplayName("un horario contiguo (19:00, justo cuando termina la de 18:00) está libre")
    void permiteElHorarioContiguo() {
        // Arrange
        guardar(cliente, SEIS_PM);

        // Act + Assert
        assertThat(citas.franjaOcupada(SIETE_PM)).isFalse();
        assertThat(citas.saveAndFlush(Cita.nueva(citas.siguienteCodigo(), otroCliente, servicio, SIETE_PM, Modalidad.PRESENCIAL,
                "Quiero una página web para mi empresa de transporte.", "Colombia", "America/Bogota", Instant.parse("2031-03-04T13:00:00Z"))))
                .isNotNull();
    }

    @Test
    @DisplayName("las citas CANCELADAS se ignoran: la franja queda libre y se puede volver a tomar")
    void ignoraLasCitasCanceladas() {
        // Arrange
        guardarCancelada(cliente, SEIS_PM);

        // Act + Assert
        assertThat(citas.franjaOcupada(SEIS_PM)).isFalse();
        assertThat(citas.clienteTieneCita(cliente, SEIS_PM)).isFalse();
        Cita nueva = guardar(otroCliente, SEIS_PM); // el índice parcial no cuenta la cancelada
        assertThat(nueva.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        assertThat(citas.franjaOcupada(SEIS_PM)).isTrue();
    }

    @Test
    @DisplayName("dos citas vivas en la misma franja: la base rechaza la segunda (índice ux_cita_franja)")
    void elIndiceUnicoRechazaLaSegundaCitaEnLaMismaFranja() {
        // Arrange
        guardar(cliente, SEIS_PM);

        // Act + Assert
        assertThatThrownBy(() -> guardar(otroCliente, SEIS_PM))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_cita_franja");
    }

    @Test
    @DisplayName("clienteTieneCita responde por cliente y por hora: sí a su hora, no a otra hora ni para otro cliente (RN-03)")
    void consultaSiElClienteYaTieneCitaAEsaHora() {
        // Arrange
        guardar(cliente, SEIS_PM);

        // Act + Assert
        assertThat(citas.clienteTieneCita(cliente, SEIS_PM)).isTrue();
        assertThat(citas.clienteTieneCita(otroCliente, SEIS_PM)).isFalse();
        assertThat(citas.clienteTieneCita(cliente, SIETE_PM)).isFalse();
    }

    @Test
    @DisplayName("una hora que no es en punto (18:30) no coincide con la franja de 18:00: esa protección es del servicio, no de la base")
    void unaHoraFueraDeLaRejillaNoChocaEnLaBase() {
        // Arrange
        guardar(cliente, SEIS_PM);

        // Act + Assert: la consulta y el índice comparan instantes exactos; por eso ServicioDisponibilidad.esFranjaAgendable
        // rechaza todo lo que no empiece en punto (RN-01) y ese rechazo se prueba en ServicioCitasUnitarioTest.
        assertThat(citas.franjaOcupada(SEIS_Y_MEDIA)).isFalse();
    }

    @Test
    @DisplayName("franjasOcupadas(desde, hasta) devuelve solo las vivas dentro del rango [desde, hasta)")
    void listaLasFranjasOcupadasDelRango() {
        // Arrange
        guardar(cliente, SEIS_PM);
        guardarCancelada(otroCliente, SIETE_PM);

        // Act
        List<Instant> enElRango = citas.franjasOcupadas(SEIS_PM, SIETE_PM.plusSeconds(3600));
        List<Instant> fueraDelRango = citas.franjasOcupadas(SIETE_PM, SIETE_PM.plusSeconds(3600));

        // Assert
        assertThat(enElRango).containsExactly(SEIS_PM); // la cancelada de las 19:00 no cuenta
        assertThat(fueraDelRango).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndClienteId solo devuelve la cita a su dueño (la ajena no existe para los demás)")
    void soloElDuenoEncuentraSuCita() {
        // Arrange
        Cita propia = guardar(cliente, SEIS_PM);

        // Act + Assert
        assertThat(citas.findByIdAndClienteId(propia.getId(), cliente)).isPresent();
        assertThat(citas.findByIdAndClienteId(propia.getId(), otroCliente)).isEmpty();
    }
}
