package co.coretech.portal.agenda.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.agenda.datos.CitaRepositorio;
import co.coretech.portal.agenda.datos.CuestionarioCitaRepositorio;
import co.coretech.portal.agenda.dominio.Cita;
import co.coretech.portal.agenda.dominio.EstadoCita;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.aplicacion.CalendarioHabil;
import co.coretech.portal.especialidad.EquipoConsultas;

/**
 * Recordatorios de las citas que se quedan a medias, una vez por día hábil. Cada aviso sale una sola vez por cita,
 * porque se compara el número exacto de días hábiles transcurridos:
 * <ul>
 *   <li>PENDIENTE con equipo, sin confirmar desde la asignación: al Product Owner a 1 día hábil y a la administración a 2.</li>
 *   <li>PENDIENTE sin equipo desde que se agendó: a la administración a 1 día hábil.</li>
 *   <li>CONFIRMADA que ya pasó sin cuestionario: al Product Owner a 1 día hábil de la reunión y a la administración a 2
 *       (si el cliente no llegó, la administración la cancela).</li>
 * </ul>
 */
@Service
public class RecordatoriosCitas {

    private static final Logger LOG = LoggerFactory.getLogger(RecordatoriosCitas.class);
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    private final CitaRepositorio citas;
    private final CuestionarioCitaRepositorio cuestionarios;
    private final EquipoConsultas equipos;
    private final DirectorioUsuarios usuarios;
    private final CalendarioHabil calendario;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public RecordatoriosCitas(CitaRepositorio citas, CuestionarioCitaRepositorio cuestionarios, EquipoConsultas equipos,
            DirectorioUsuarios usuarios, CalendarioHabil calendario, ApplicationEventPublisher eventos, Clock reloj) {
        this.citas = citas;
        this.cuestionarios = cuestionarios;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.calendario = calendario;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Lunes a viernes a las 8:00 a. m. de Colombia. */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "America/Bogota")
    void ejecutarCadaDia() {
        int avisos = recordar();
        if (avisos > 0) {
            LOG.info("Se enviaron {} recordatorios de citas sin confirmar o sin cuestionario", avisos);
        }
    }

    /** @return cuántos avisos publicó (uno por destinatario y cita) */
    @Transactional
    public int recordar() {
        Instant ahora = Instant.now(reloj);
        LocalDate hoy = LocalDate.now(reloj);
        List<Cita> pendientes = citas.deEstado(EstadoCita.PENDIENTE);
        List<Cita> confirmadas = citas.deEstado(EstadoCita.CONFIRMADA).stream().filter(c -> !c.getFechaHora().isAfter(ahora)).toList();
        Set<UUID> conCuestionario = cuestionarios.conCuestionario(confirmadas.stream().map(Cita::getId).toList());
        var porEquipo = equipos.porIds(Stream.concat(pendientes.stream(), confirmadas.stream())
                .map(Cita::getEquipoId).filter(Objects::nonNull).distinct().toList());
        List<UUID> administradores = usuarios.administradoresActivos();
        int avisos = 0;

        for (Cita c : pendientes) {
            int dias = calendario.diasHabilesEntre(c.getActualizadaEn().atZone(COLOMBIA).toLocalDate(), hoy);
            var equipo = c.getEquipoId() == null ? null : porEquipo.get(c.getEquipoId());
            if (equipo == null) {
                if (dias == 1) {
                    avisos += avisarAdministradores(administradores, c, "CITA_SIN_EQUIPO", "La cita " + c.getCodigo() + " sigue sin equipo",
                            "Lleva un día hábil sin asignar. Elige el equipo que la atenderá.");
                }
            } else if (dias == 1) {
                eventos.publishEvent(AvisoSolicitado.para(equipo.productOwnerId(), "CITA_POR_CONFIRMAR", "Confirma la cita " + c.getCodigo(),
                        "Lleva un día hábil esperando tu confirmación. El cliente aún no sabe si puedes atenderlo.", "/equipo/citas"));
                avisos++;
            } else if (dias == 2) {
                avisos += avisarAdministradores(administradores, c, "CITA_SIN_CONFIRMAR", "La cita " + c.getCodigo() + " sigue sin confirmar",
                        "El equipo de " + equipo.productOwner() + " no la confirma desde hace dos días hábiles.");
            }
        }
        for (Cita c : confirmadas) {
            if (conCuestionario.contains(c.getId()) || c.getEquipoId() == null) {
                continue;
            }
            var equipo = porEquipo.get(c.getEquipoId());
            if (equipo == null) {
                continue;
            }
            int dias = calendario.diasHabilesEntre(c.getFechaHora().atZone(COLOMBIA).toLocalDate(), hoy);
            if (dias == 1) {
                eventos.publishEvent(AvisoSolicitado.para(equipo.productOwnerId(), "CITA_SIN_CUESTIONARIO", "Falta el cuestionario de " + c.getCodigo(),
                        "La reunión ya pasó. Llena el cuestionario para cerrar la cita: la administración lo necesita para crear el proyecto.", "/equipo/citas"));
                avisos++;
            } else if (dias == 2) {
                avisos += avisarAdministradores(administradores, c, "CITA_SIN_CUESTIONARIO", "La cita " + c.getCodigo() + " sigue sin cerrar",
                        "El equipo de " + equipo.productOwner() + " no ha llenado el cuestionario desde hace dos días hábiles. "
                                + "Si el cliente no asistió, cancélala.");
            }
        }
        return avisos;
    }

    private int avisarAdministradores(List<UUID> administradores, Cita cita, String tipo, String titulo, String texto) {
        if (administradores.isEmpty()) {
            return 0;
        }
        eventos.publishEvent(new AvisoSolicitado(administradores, tipo, titulo, texto, "/admin/citas?buscar=" + cita.getCodigo()));
        return administradores.size();
    }
}
