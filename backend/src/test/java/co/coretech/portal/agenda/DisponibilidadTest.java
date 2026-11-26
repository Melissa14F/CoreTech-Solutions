package co.coretech.portal.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.agenda.service.ServicioDisponibilidad;
import co.coretech.portal.agenda.service.ServicioDisponibilidad.Estado;
import co.coretech.portal.agenda.service.ServicioDisponibilidad.Franja;
import co.coretech.portal.agenda.repository.CitaRepositorio;
import co.coretech.portal.agenda.repository.HorarioLaboral;
import co.coretech.portal.compartido.repository.DiasNoHabilesRepositorio;

/**
 * Las franjas de las citas con el reloj fijo (HU-01, RN-01, RN-02, RN-03, RN-23). Hoy es el martes 2031-03-04 a las
 * 10:00 en Colombia; se usa un año en que ninguna otra prueba tiene datos, y el festivo del lunes 2031-03-17 se
 * agrega y se quita en cada prueba. Los límites quedan así: la primera franja es el miércoles 03-05 a las 10:00
 * (justo 24 h), el día 30 es el jueves 04-03 y el 31, el viernes 04-04.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class DisponibilidadTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final Instant AHORA = ZonedDateTime.of(2031, 3, 4, 10, 0, 0, 0, COLOMBIA).toInstant();


    @Autowired
    HorarioLaboral horario;

    @Autowired
    DiasNoHabilesRepositorio festivos;

    @Autowired
    CitaRepositorio citas;

    @Autowired
    JdbcTemplate jdbc;

    ServicioDisponibilidad servicio;
    UUID cliente;

    @BeforeEach
    void preparar() {
        cliente = crearCliente();
        jdbc.update("INSERT INTO dias_no_habiles (fecha, motivo) VALUES ('2031-03-17', 'Festivo de prueba') ON CONFLICT DO NOTHING");
        servicio = new ServicioDisponibilidad(horario, festivos, citas, Clock.fixed(AHORA, COLOMBIA));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM citas WHERE fecha_hora >= '2031-01-01'");
        jdbc.update("DELETE FROM dias_no_habiles WHERE fecha = '2031-03-17'");
    }

    private UUID crearCliente() {
        return jdbc.queryForObject("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Cliente de Prueba', ?, '3001234567', 'no-se-usa', true, now() FROM roles WHERE codigo = 'CLIENTE'
                RETURNING id
                """, UUID.class, "disp-" + UUID.randomUUID().toString().substring(0, 12) + "@correo.com");
    }

    private static Instant en(int anio, int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(anio, mes, dia, hora, minuto, 0, 0, COLOMBIA).toInstant();
    }

    private Franja franja(List<Franja> franjas, String fecha, String hora) {
        return franjas.stream().filter(f -> f.fecha().equals(LocalDate.parse(fecha)) && f.hora().equals(LocalTime.parse(hora)))
                .findFirst().orElseThrow(() -> new AssertionError("No hay franja el " + fecha + " a las " + hora));
    }

    // ---------------------------------------------------------------- RN-01 · bordes de la ventana

    @Nested
    class Ventana {

        @Test
        void justo24HorasAntesSePuedeAgendarYUnMinutoAntesNo() {
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 5, 10, 0))).isTrue();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 5, 9, 0))).isFalse();
        }

        @Test
        void elDia30SeAgendaYElDia31No() {
            assertThat(servicio.esFranjaAgendable(en(2031, 4, 3, 17, 0))).isTrue();
            assertThat(servicio.esFranjaAgendable(en(2031, 4, 4, 8, 0))).isFalse();
        }

        @Test
        void unFestivoUnSabadoYUnDomingoNoSeAgendan() {
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 17, 9, 0))).as("festivo").isFalse();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 8, 9, 0))).as("sábado").isFalse();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 9, 9, 0))).as("domingo").isFalse();
        }

        @Test
        void lasDoceYLasDieciochoNoSonFranjaPeroLasOnceYLasDiecisieteSi() {
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 12, 12, 0))).as("12:00").isFalse();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 12, 13, 0))).as("13:00 (almuerzo)").isFalse();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 12, 18, 0))).as("18:00").isFalse();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 12, 7, 0))).as("07:00").isFalse();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 12, 11, 0))).as("11:00").isTrue();
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 12, 17, 0))).as("17:00").isTrue();
        }

        @Test
        void unaHoraQueNoEmpiezaEnPuntoNoEsFranja() {
            assertThat(servicio.esFranjaAgendable(en(2031, 3, 12, 9, 30))).isFalse();
        }

        @Test
        void cadaDiaHabilTieneOchoFranjasDeUnaHora() {
            List<Franja> franjas = servicio.deMes(cliente, YearMonth.of(2031, 3), COLOMBIA);
            List<String> horas = franjas.stream().filter(f -> f.fecha().equals(LocalDate.of(2031, 3, 12)))
                    .map(f -> f.hora().toString()).toList();
            assertThat(horas).containsExactly("08:00", "09:00", "10:00", "11:00", "14:00", "15:00", "16:00", "17:00");
        }

        @Test
        void elCalendarioMarcaLibreOcupadoYNoHabilSegunLaVentana() {
            List<Franja> franjas = servicio.deMes(cliente, YearMonth.of(2031, 3), COLOMBIA);
            assertThat(franja(franjas, "2031-03-04", "11:00").estado()).as("hoy, a menos de 24 h").isEqualTo(Estado.NO_HABIL);
            assertThat(franja(franjas, "2031-03-05", "09:00").estado()).as("24 h menos una hora").isEqualTo(Estado.NO_HABIL);
            assertThat(franja(franjas, "2031-03-05", "10:00").estado()).as("justo 24 h").isEqualTo(Estado.LIBRE);
            assertThat(franja(franjas, "2031-03-17", "09:00").estado()).as("festivo").isEqualTo(Estado.NO_HABIL);
            assertThat(franjas).as("el fin de semana no tiene franjas")
                    .noneMatch(f -> f.fecha().equals(LocalDate.of(2031, 3, 8)) || f.fecha().equals(LocalDate.of(2031, 3, 9)));
            List<Franja> abril = servicio.deMes(cliente, YearMonth.of(2031, 4), COLOMBIA);
            assertThat(franja(abril, "2031-04-03", "17:00").estado()).as("día 30").isEqualTo(Estado.LIBRE);
            assertThat(franja(abril, "2031-04-04", "08:00").estado()).as("día 31").isEqualTo(Estado.NO_HABIL);
        }
    }

    // ---------------------------------------------------------------- RN-02, RN-03 · franjas ocupadas

    @Nested
    class Ocupadas {

        @Test
        void unaCitaPendienteOConfirmadaOcupaLaFranjaYUnaCanceladaNo() {
            UUID otro = crearCliente();
            insertarCita(otro, en(2031, 3, 12, 9, 0), "PENDIENTE");
            insertarCita(otro, en(2031, 3, 12, 10, 0), "CONFIRMADA");
            insertarCita(otro, en(2031, 3, 12, 11, 0), "CANCELADA");
            List<Franja> franjas = servicio.deMes(cliente, YearMonth.of(2031, 3), COLOMBIA);
            assertThat(franja(franjas, "2031-03-12", "09:00").estado()).isEqualTo(Estado.OCUPADO);
            assertThat(franja(franjas, "2031-03-12", "10:00").estado()).isEqualTo(Estado.OCUPADO);
            assertThat(franja(franjas, "2031-03-12", "11:00").estado()).isEqualTo(Estado.LIBRE);
            assertThat(franja(franjas, "2031-03-12", "09:00").tuya()).isFalse();
        }

        @Test
        void laCitaDelPropioClienteSeMarcaTuya() {
            insertarCita(cliente, en(2031, 3, 13, 14, 0), "PENDIENTE");
            List<Franja> franjas = servicio.deMes(cliente, YearMonth.of(2031, 3), COLOMBIA);
            Franja f = franja(franjas, "2031-03-13", "14:00");
            assertThat(f.estado()).isEqualTo(Estado.OCUPADO);
            assertThat(f.tuya()).isTrue();
            assertThat(franja(servicio.deMes(crearCliente(), YearMonth.of(2031, 3), COLOMBIA), "2031-03-13", "14:00").tuya()).isFalse();
        }

        private void insertarCita(UUID cliente, Instant hora, String estado) {
            jdbc.update("""
                    INSERT INTO citas (codigo, cliente_id, servicio_id, fecha_hora, modalidad, descripcion_necesidad, estado, enlace_reunion, cancelada_at)
                    SELECT fn_siguiente_codigo_cita(), ?, (SELECT id FROM servicios LIMIT 1), ?, 'VIRTUAL',
                           'Necesito una tienda para vender mis productos.', ?::estado_cita, 'https://meet.example.com/prueba',
                           CASE WHEN ? = 'CANCELADA' THEN now() END
                    """, cliente, java.sql.Timestamp.from(hora), estado, estado);
        }
    }

    // ---------------------------------------------------------------- RN-23 · zonas horarias

    @Nested
    class Zonas {

        private Franja deMadrid(String fecha, String hora) {
            return franja(servicio.deMes(cliente, YearMonth.of(2031, 3), ZoneId.of("Europe/Madrid")), fecha, hora);
        }

        private Franja deLosAngeles(String fecha, String hora) {
            return franja(servicio.deMes(cliente, YearMonth.of(2031, 3), ZoneId.of("America/Los_Angeles")), fecha, hora);
        }

        @Test
        void lasOchoDeColombiaSonLas14EnMadridEnInvierno() {
            Franja f = deMadrid("2031-03-28", "14:00");
            assertThat(f.horaColombia()).startsWith("2031-03-28T08:00").endsWith("-05:00");
            assertThat(f.fechaHora()).startsWith("2031-03-28T14:00").endsWith("+01:00");
        }

        @Test
        void lasOchoDeColombiaSonLas15EnMadridConElHorarioDeVerano() {
            // España cambia el domingo 2031-03-30: el lunes 31 la diferencia es de 6 horas, no de 5 más una.
            Franja f = deMadrid("2031-03-31", "15:00");
            assertThat(f.horaColombia()).startsWith("2031-03-31T08:00");
            assertThat(f.fechaHora()).startsWith("2031-03-31T15:00").endsWith("+02:00");
        }

        @Test
        void losAngelesCambiaElHorarioAntesQueEspana() {
            // Estados Unidos cambia el domingo 2031-03-09.
            assertThat(deLosAngeles("2031-03-07", "05:00").fechaHora()).endsWith("-08:00");
            assertThat(deLosAngeles("2031-03-10", "06:00").fechaHora()).endsWith("-07:00");
            assertThat(deLosAngeles("2031-03-10", "06:00").horaColombia()).startsWith("2031-03-10T08:00");
        }

        @Test
        void nuevaYorkCambiaElHorarioElMismoDiaQueLosAngeles() {
            // RNF-12 pide probar Nueva York. Con la hora de invierno (UTC-5) coincide con Colombia; desde el domingo 2031-03-09,
            // con el horario de verano (UTC-4), las 8:00 de Colombia son las 9:00 allá.
            List<Franja> marzo = servicio.deMes(cliente, YearMonth.of(2031, 3), ZoneId.of("America/New_York"));
            Franja antes = franja(marzo, "2031-03-07", "08:00");
            assertThat(antes.fechaHora()).endsWith("-05:00");
            assertThat(antes.horaColombia()).startsWith("2031-03-07T08:00");
            Franja despues = franja(marzo, "2031-03-10", "09:00");
            assertThat(despues.fechaHora()).startsWith("2031-03-10T09:00").endsWith("-04:00");
            assertThat(despues.horaColombia()).startsWith("2031-03-10T08:00").endsWith("-05:00");
        }

        @Test
        void unaHoraQueCaeEnOtroDiaApareceEnElDiaLocalCorrecto() {
            // Sídney (UTC+11): las 08:00 del miércoles en Colombia son las 00:00 del jueves allá.
            List<Franja> franjas = servicio.deMes(cliente, YearMonth.of(2031, 3), ZoneId.of("Australia/Sydney"));
            Franja f = franja(franjas, "2031-03-13", "00:00");
            assertThat(f.horaColombia()).startsWith("2031-03-12T08:00");
        }

        @Test
        void elMesSeArmaConElDiaLocalDelCliente() {
            // Las 17:00 del viernes 2031-03-28 en Colombia ya son sábado 29 en Sídney; el 31 de marzo no entra a abril.
            List<Franja> abril = servicio.deMes(cliente, YearMonth.of(2031, 4), ZoneId.of("Australia/Sydney"));
            assertThat(abril).allMatch(f -> f.fecha().getMonthValue() == 4);
            assertThat(abril).anyMatch(f -> f.fecha().equals(LocalDate.of(2031, 4, 1)));
        }
    }
}
