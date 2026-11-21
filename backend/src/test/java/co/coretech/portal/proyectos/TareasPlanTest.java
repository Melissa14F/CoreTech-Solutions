package co.coretech.portal.proyectos;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.compartido.aplicacion.CalendarioHabil;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.proyectos.aplicacion.TareasPlan;
import co.coretech.portal.proyectos.datos.PlanesJdbc;

/**
 * La tarea diaria de las propuestas sin respuesta (HU-29, RN-29) con un reloj fijo en 2031 y un festivo en medio: recordatorio
 * un día hábil después del plazo de 3 días hábiles, vencimiento a los 10 días hábiles y aviso de cancelación a los 30 días
 * calendario del vencimiento y del rechazo. El plan nunca se aprueba solo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class TareasPlanTest extends BaseProyectosPrueba {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    /** Lunes 2 de junio de 2031: el día en que se envía el plan. */
    static final LocalDate ENVIO = LocalDate.of(2031, 6, 2);
    static final LocalDate FESTIVO = LocalDate.of(2031, 6, 4);

    @Autowired
    PlanesJdbc planes;
    @Autowired
    EquipoConsultas equipos;
    @Autowired
    DirectorioUsuarios usuarios;
    @Autowired
    CalendarioHabil calendario;
    @Autowired
    RegistroAuditoria auditoria;
    @Autowired
    ApplicationEventPublisher eventos;
    @Autowired
    TransactionTemplate transaccion;

    private boolean festivoNuevo;

    @BeforeEach
    void festivoEnMedio() {
        festivoNuevo = jdbc.update("INSERT INTO dias_no_habiles (fecha, motivo) VALUES (?, 'Festivo de prueba del plan') ON CONFLICT DO NOTHING",
                FESTIVO) > 0;
    }

    @AfterEach
    void quitarFestivo() {
        if (festivoNuevo) {
            jdbc.update("DELETE FROM dias_no_habiles WHERE fecha = ?", FESTIVO);
        }
    }

    private void revisarEl(LocalDate dia) {
        TareasPlan tarea = new TareasPlan(planes, equipos, usuarios, calendario, auditoria, eventos,
                Clock.fixed(dia.atTime(8, 0).atZone(COLOMBIA).toInstant(), COLOMBIA));
        transaccion.executeWithoutResult(t -> tarea.revisar());
    }

    /** Un proyecto con el plan ENVIADO el lunes 2 de junio de 2031: 3 días hábiles de plazo y 10 de vigencia. */
    private Proyecto enviadoElLunes2DeJunio() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        jdbc.update("""
                UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 5000000,
                       condiciones_cotizacion = 'Incluye diseño y desarrollo; no incluye hosting.',
                       plan_enviado_at = ?, plan_plazo_respuesta = ?, plan_vence_el = ? WHERE codigo = ?
                """, Timestamp.from(ENVIO.atTime(10, 0).atZone(COLOMBIA).toInstant()), calendario.sumarDiasHabiles(ENVIO, 3),
                calendario.sumarDiasHabiles(ENVIO, 10), p.codigo());
        return p;
    }

    /** Avisos de cancelación del proyecto dado: otras pruebas dejan proyectos vencidos el mismo día y avisan a todos los administradores. */
    private int avisosDeProyecto(UUID usuario, String codigo) {
        return jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ? AND tipo = 'PLAN_PARA_CANCELAR' AND titulo LIKE ?",
                Integer.class, usuario, "%" + codigo);
    }

    private String estadoPlan(String codigo) {
        return jdbc.queryForObject("SELECT estado::text || '/' || estado_plan::text FROM proyectos WHERE codigo = ?", String.class, codigo);
    }

    /** Regresión: la tarea que corre sola (con el reloj real) debe llevar su propia transacción, o la auditoría falla. */
    @Autowired
    TareasPlan tareaDelSistema;

    @Test
    void laTareaQueCorreSolaVenceUnPlanYDejaLaAuditoriaSinTransaccionPrevia() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        LocalDate hoy = LocalDate.now(COLOMBIA);
        jdbc.update("""
                UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 5000000,
                       condiciones_cotizacion = 'Incluye diseño y desarrollo; no incluye hosting.',
                       plan_enviado_at = now() - interval '14 days', plan_plazo_respuesta = ?, plan_vence_el = ? WHERE codigo = ?
                """, hoy.minusDays(3), hoy, p.codigo());

        tareaDelSistema.ejecutarCadaDia();

        assertThat(estadoPlan(p.codigo())).isEqualTo("PAUSADO/VENCIDO");
        assertThat(avisos(p.clienteId(), "PLAN_VENCIDO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = 'La propuesta venció sin respuesta'",
                Integer.class, p.codigo())).isEqualTo(1);
    }

    @Test
    void elRecordatorioSaleUnDiaHabilDespuesDelPlazoDeTresDiasHabilesSinContarElFestivo() throws Exception {
        Proyecto p = enviadoElLunes2DeJunio();
        LocalDate plazo = calendario.sumarDiasHabiles(ENVIO, 3);
        // El miércoles 4 es festivo: martes 3, jueves 5 y viernes 6
        assertThat(plazo).isEqualTo(LocalDate.of(2031, 6, 6));

        revisarEl(plazo.minusDays(1));
        revisarEl(plazo);
        assertThat(avisos(p.clienteId(), "PLAN_RECORDATORIO")).isZero();

        revisarEl(calendario.sumarDiasHabiles(plazo, 1));
        assertThat(avisos(p.clienteId(), "PLAN_RECORDATORIO")).isEqualTo(1);

        revisarEl(calendario.sumarDiasHabiles(plazo, 2));
        assertThat(avisos(p.clienteId(), "PLAN_RECORDATORIO")).isEqualTo(1);
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/ENVIADO");
    }

    @Test
    void siElServidorEstuvoApagadoElRecordatorioSaleElPrimerDiaQueVuelveYUnaSolaVez() throws Exception {
        Proyecto p = enviadoElLunes2DeJunio();
        LocalDate plazo = calendario.sumarDiasHabiles(ENVIO, 3);

        // Nadie corrió la tarea el día que tocaba (plazo + 1 día hábil): vuelve tres días hábiles después
        LocalDate vuelve = calendario.sumarDiasHabiles(plazo, 3);
        revisarEl(vuelve);
        assertThat(avisos(p.clienteId(), "PLAN_RECORDATORIO")).isEqualTo(1);
        revisarEl(vuelve);
        assertThat(avisos(p.clienteId(), "PLAN_RECORDATORIO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT plan_recordado_at IS NOT NULL FROM proyectos WHERE codigo = ?", Boolean.class, p.codigo())).isTrue();
    }

    @Test
    void siElServidorEstuvoApagadoElAvisoDeTreintaDiasSaleDespuesYUnaSolaVez() throws Exception {
        UUID admin = idDe(crearUsuario("ADMINISTRADOR"));
        Proyecto p = enviadoElLunes2DeJunio();
        LocalDate vence = calendario.sumarDiasHabiles(ENVIO, 10);
        revisarEl(vence);

        revisarEl(vence.plusDays(35));
        assertThat(avisosDeProyecto(admin, p.codigo())).isEqualTo(1);
        revisarEl(vence.plusDays(36));
        assertThat(avisosDeProyecto(admin, p.codigo())).isEqualTo(1);
    }

    @Test
    void aLosDiezDiasHabilesElPlanVenceElProyectoSePausaYElPlanNuncaSeApruebaSolo() throws Exception {
        Proyecto p = enviadoElLunes2DeJunio();
        LocalDate vence = calendario.sumarDiasHabiles(ENVIO, 10);
        // Sin el festivo habría vencido un día antes
        assertThat(calendario.diasHabilesEntre(ENVIO, vence)).isEqualTo(10);
        int activosAntes = jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE equipo_id = ? AND estado = 'ACTIVO'", Integer.class, p.equipo().id());

        revisarEl(vence.minusDays(1));
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/ENVIADO");

        revisarEl(vence);
        assertThat(estadoPlan(p.codigo())).isEqualTo("PAUSADO/VENCIDO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE equipo_id = ? AND estado = 'ACTIVO'", Integer.class, p.equipo().id()))
                .isEqualTo(activosAntes - 1);
        assertThat(avisos(p.clienteId(), "PLAN_VENCIDO")).isEqualTo(1);
        assertThat(avisos(p.equipo().usuario(), "PLAN_VENCIDO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM respuestas_plan r JOIN proyectos p ON p.id = r.proyecto_id WHERE p.codigo = ?",
                Integer.class, p.codigo())).isZero();

        // Ya vencido, no vuelve a vencer ni a avisar
        revisarEl(vence.plusDays(1));
        assertThat(avisos(p.clienteId(), "PLAN_VENCIDO")).isEqualTo(1);
    }

    @Test
    void aLosTreintaDiasCalendarioDelVencimientoSeAvisaALaAdministracionYNoSeCancela() throws Exception {
        UUID admin = idDe(crearUsuario("ADMINISTRADOR"));
        Proyecto p = enviadoElLunes2DeJunio();
        LocalDate vence = calendario.sumarDiasHabiles(ENVIO, 10);
        revisarEl(vence);

        revisarEl(vence.plusDays(29));
        assertThat(avisosDeProyecto(admin, p.codigo())).isZero();
        revisarEl(vence.plusDays(30));
        assertThat(avisosDeProyecto(admin, p.codigo())).isEqualTo(1);
        revisarEl(vence.plusDays(31));
        assertThat(avisosDeProyecto(admin, p.codigo())).isEqualTo(1);
        assertThat(estadoPlan(p.codigo())).isEqualTo("PAUSADO/VENCIDO");
    }

    @Test
    void aLosTreintaDiasCalendarioDelRechazoSeAvisaALaAdministracionYNoSeCancela() throws Exception {
        UUID admin = idDe(crearUsuario("ADMINISTRADOR"));
        Proyecto p = enviadoElLunes2DeJunio();
        jdbc.update("UPDATE proyectos SET estado_plan = 'RECHAZADO' WHERE codigo = ?", p.codigo());
        jdbc.update("""
                INSERT INTO respuestas_plan (proyecto_id, cliente_id, respuesta, comentario, created_at)
                VALUES ((SELECT id FROM proyectos WHERE codigo = ?), ?, 'RECHAZADO', 'El valor supera nuestro presupuesto de este año.', ?)
                """, p.codigo(), p.clienteId(), Timestamp.from(LocalDate.of(2031, 6, 10).atTime(15, 0).atZone(COLOMBIA).toInstant()));

        revisarEl(LocalDate.of(2031, 7, 9));
        assertThat(avisosDeProyecto(admin, p.codigo())).isZero();
        revisarEl(LocalDate.of(2031, 7, 10));
        assertThat(avisosDeProyecto(admin, p.codigo())).isEqualTo(1);
        assertThat(estadoPlan(p.codigo())).isEqualTo("PAUSADO/RECHAZADO");
    }
}
