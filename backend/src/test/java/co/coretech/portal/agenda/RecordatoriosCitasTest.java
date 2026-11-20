package co.coretech.portal.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.agenda.aplicacion.RecordatoriosCitas;
import co.coretech.portal.agenda.datos.CitaRepositorio;
import co.coretech.portal.agenda.datos.CuestionarioCitaRepositorio;
import co.coretech.portal.compartido.aplicacion.CalendarioHabil;
import co.coretech.portal.especialidad.EquipoConsultas;

/**
 * Recordatorios de las citas a medias (PENDIENTE sin confirmar, CONFIRMADA sin cuestionario) con un reloj fijo: el
 * miércoles 5 de marzo de 2031, 8:00 a. m. en Colombia. Cada aviso sale una sola vez por cita, el día hábil exacto.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class RecordatoriosCitasTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final ZonedDateTime HOY = ZonedDateTime.of(2031, 3, 5, 8, 0, 0, 0, COLOMBIA);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CitaRepositorio citas;
    @Autowired
    CuestionarioCitaRepositorio cuestionarios;
    @Autowired
    EquipoConsultas equipos;
    @Autowired
    DirectorioUsuarios usuarios;
    @Autowired
    CalendarioHabil calendario;
    @Autowired
    ApplicationEventPublisher eventos;
    @Autowired
    org.springframework.transaction.support.TransactionTemplate transaccion;

    private RecordatoriosCitas recordatorios() {
        return new RecordatoriosCitas(citas, cuestionarios, equipos, usuarios, calendario, eventos,
                Clock.fixed(HOY.toInstant(), COLOMBIA));
    }

    private int recordar() {
        return transaccion.execute(estado -> recordatorios().recordar());
    }

    @Test
    void unaCitaSinConfirmarAvisaAlProductOwnerAlPrimerDiaHabilYALaAdministracionAlSegundo() {
        Equipo equipo = equipo();
        UUID admin = administrador();
        UUID cita = cita(null, equipo.id(), "PENDIENTE", "2031-03-20T10:00-05:00", "2031-03-04T10:00-05:00");

        recordar();
        assertThat(avisos(equipo.usuario(), "CITA_POR_CONFIRMAR")).isEqualTo(1);
        assertThat(avisos(admin, "CITA_SIN_CONFIRMAR")).isZero();

        // Un día hábil más tarde (la misma cita, asignada el lunes 3): ahora le toca a la administración
        jdbc.update("UPDATE citas SET updated_at = ? WHERE id = ?", Timestamp.from(Instant.parse("2031-03-03T15:00:00Z")), cita);
        recordar();
        assertThat(avisos(admin, "CITA_SIN_CONFIRMAR")).isEqualTo(1);
        assertThat(avisos(equipo.usuario(), "CITA_POR_CONFIRMAR")).isEqualTo(1);
    }

    @Test
    void unaCitaPendienteSinEquipoAvisaALaAdministracionSoloAlPrimerDiaHabil() {
        UUID admin = administrador();
        UUID cita = cita(null, null, "PENDIENTE", "2031-03-21T10:00-05:00", "2031-03-04T10:00-05:00");

        recordar();
        assertThat(avisos(admin, "CITA_SIN_EQUIPO")).isEqualTo(1);
        // Hoy cumple dos días hábiles: ya no se repite
        jdbc.update("UPDATE citas SET updated_at = ? WHERE id = ?", Timestamp.from(Instant.parse("2031-03-03T15:00:00Z")), cita);
        recordar();
        assertThat(avisos(admin, "CITA_SIN_EQUIPO")).isEqualTo(1);
    }

    @Test
    void unaCitaConfirmadaQueYaPasoSinCuestionarioAvisaAlProductOwnerYLuegoALaAdministracion() {
        Equipo equipo = equipo();
        UUID admin = administrador();
        UUID cita = cita("https://meet.google.com/abc-defg-hij", equipo.id(), "CONFIRMADA", "2031-03-04T10:00-05:00", "2031-03-01T10:00-05:00");

        recordar();
        assertThat(avisos(equipo.usuario(), "CITA_SIN_CUESTIONARIO")).isEqualTo(1);

        jdbc.update("UPDATE citas SET fecha_hora = ?::timestamptz WHERE id = ?", "2031-03-03T10:00-05:00", cita);
        recordar();
        assertThat(avisos(admin, "CITA_SIN_CUESTIONARIO")).isEqualTo(1);
    }

    @Test
    void laCitaConCuestionarioOQueAunNoOcurrioNoGeneraAvisos() {
        Equipo equipo = equipo();
        UUID atendida = cita("https://meet.google.com/abc-defg-hij", equipo.id(), "CONFIRMADA", "2031-03-04T11:00-05:00", "2031-03-01T10:00-05:00");
        jdbc.update("""
                INSERT INTO cuestionarios_cita (cita_id, resumen, descripcion_cliente, presupuesto, idioma, urgencia, registrado_por)
                VALUES (?, ?, ?, 'MAS_30M', 'ESPANOL', 'BAJA', ?)
                """, atendida, "x".repeat(40), "y".repeat(40), equipo.usuario());
        cita("https://meet.google.com/abc-defg-hij", equipo.id(), "CONFIRMADA", "2031-03-07T10:00-05:00", "2031-03-01T10:00-05:00");

        recordar();
        assertThat(avisos(equipo.usuario(), "CITA_SIN_CUESTIONARIO")).isZero();
    }

    // ---------------------------------------------------------------- ayudas

    record Equipo(UUID id, UUID usuario) {
    }

    private Equipo equipo() {
        String email = "rec-" + UUID.randomUUID().toString().substring(0, 12) + "@coretechsolutions.co";
        UUID usuario = usuario(email, "PRODUCT_OWNER");
        UUID desarrollador = jdbc.queryForObject("""
                INSERT INTO desarrolladores (nombre, correo, celular, especialidad, usuario_id)
                VALUES ('Product Owner de Prueba', ?, '3001234567', 'FULL_STACK', ?) RETURNING id
                """, UUID.class, email, usuario);
        UUID equipo = jdbc.queryForObject("INSERT INTO equipos (max_proyectos, activo) VALUES (3, true) RETURNING id", UUID.class);
        jdbc.update("INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol) VALUES (?, ?, 'PRODUCT_OWNER')", equipo, desarrollador);
        return new Equipo(equipo, usuario);
    }

    private UUID administrador() {
        return usuario("rec-" + UUID.randomUUID().toString().substring(0, 12) + "@coretechsolutions.co", "ADMINISTRADOR");
    }

    private UUID usuario(String email, String rol) {
        return jdbc.queryForObject("""
                INSERT INTO usuarios (id, rol_id, nombre, email, celular, password_hash, activo, acepta_datos_at)
                SELECT gen_random_uuid(), id, 'Usuario de Prueba', ?, '3001234567', 'no-se-usa', true, now() FROM roles WHERE codigo = ?
                RETURNING id
                """, UUID.class, email, rol);
    }

    /** Cita con su fecha, y {@code actualizada} (cuándo se agendó o asignó, que es lo que cuentan los recordatorios). */
    private UUID cita(String enlace, UUID equipo, String estado, String fecha, String actualizada) {
        UUID cliente = usuario("rec-" + UUID.randomUUID().toString().substring(0, 12) + "@correo.com", "CLIENTE");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO citas (id, codigo, cliente_id, servicio_id, equipo_id, fecha_hora, modalidad, descripcion_necesidad, estado,
                                   enlace_reunion, confirmada_at, updated_at)
                VALUES (?, fn_siguiente_codigo_cita(), ?, (SELECT id FROM servicios ORDER BY orden LIMIT 1), ?, ?::timestamptz,
                        ?::modalidad, 'Necesito una tienda en línea para vender mis productos.', ?::estado_cita, ?, ?, ?::timestamptz)
                """, id, cliente, equipo, fecha, enlace == null ? "PRESENCIAL" : "VIRTUAL", estado, enlace,
                estado.equals("CONFIRMADA") ? Timestamp.from(Instant.parse("2031-03-01T15:00:00Z")) : null, actualizada);
        return id;
    }

    private int avisos(UUID usuario, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = ?", Integer.class, usuario, tipo);
    }
}
