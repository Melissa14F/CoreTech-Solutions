package co.coretech.portal.agenda.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.agenda.datos.CitaRepositorio;
import co.coretech.portal.agenda.datos.HorarioLaboral;
import co.coretech.portal.agenda.datos.HorarioLaboral.Tramo;
import co.coretech.portal.compartido.datos.DiasNoHabilesRepositorio;

/**
 * Franjas de las citas (HU-01, RN-01, RN-02, RN-03, RN-23). Todo se calcula en hora de Colombia (el horario de
 * atención es el de la empresa) y después se convierte a la zona del cliente.
 * <p>
 * Una franja se puede agendar si: cae en un tramo de {@code horario_laboral} y empieza en punto; el día es hábil
 * (lunes a viernes sin festivos); empieza dentro de 24 horas o más; y su fecha en Colombia no pasa de hoy + 30 días.
 */
@Service
@Transactional(readOnly = true)
public class ServicioDisponibilidad {

    /** RN-01: antelación mínima y máxima. */
    static final Duration ANTELACION_MINIMA = Duration.ofHours(24);
    static final int DIAS_MAXIMOS = 30;
    static final Duration DURACION = Duration.ofHours(1);
    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    public enum Estado { LIBRE, OCUPADO, NO_HABIL }

    /**
     * Una franja de una hora.
     *
     * @param fecha        día en la zona del cliente (AAAA-MM-DD)
     * @param hora         hora de inicio en la zona del cliente (HH:mm)
     * @param fechaHora    el instante con el desplazamiento del cliente: es lo que se envía al crear la cita
     * @param horaColombia el mismo instante con el desplazamiento de Colombia (el día puede ser otro)
     * @param estado       LIBRE, OCUPADO (otra cita, o la tuya) o NO_HABIL (festivo, antes de 24 h o después de 30 días)
     * @param tuya         true si el cliente ya tiene una cita a esa hora ("Ya tienes una cita", RN-03)
     */
    public record Franja(LocalDate fecha, LocalTime hora, String fechaHora, String horaColombia, Estado estado, boolean tuya) {
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioLaboral horario;
    private final DiasNoHabilesRepositorio festivos;
    private final CitaRepositorio citas;
    private final Clock reloj;

    public ServicioDisponibilidad(HorarioLaboral horario, DiasNoHabilesRepositorio festivos, CitaRepositorio citas, Clock reloj) {
        this.horario = horario;
        this.festivos = festivos;
        this.citas = citas;
        this.reloj = reloj;
    }

    /**
     * Las franjas cuyo día, en la zona del cliente, está en {@code mes}. Los fines de semana no tienen franjas
     * (el horario laboral no los incluye); un festivo sí, marcado NO_HABIL.
     */
    public List<Franja> deMes(UUID clienteId, YearMonth mes, ZoneId zona) {
        Instant desde = mes.atDay(1).atStartOfDay(zona).toInstant();
        Instant hasta = mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant();
        LocalDate primeraFecha = desde.atZone(COLOMBIA).toLocalDate();
        LocalDate ultimaFecha = hasta.atZone(COLOMBIA).toLocalDate();

        Set<LocalDate> noHabiles = festivos.entre(primeraFecha, ultimaFecha);
        Set<Instant> ocupadas = new HashSet<>(citas.franjasOcupadas(desde, hasta));
        Set<Instant> mias = new HashSet<>(citas.franjasDelCliente(clienteId, desde, hasta));
        Map<Integer, List<Tramo>> tramos = horario.tramos();
        Instant ahora = Instant.now(reloj);
        LocalDate hoy = LocalDate.now(reloj);

        List<Franja> franjas = new ArrayList<>();
        for (LocalDate dia = primeraFecha; !dia.isAfter(ultimaFecha); dia = dia.plusDays(1)) {
            for (Tramo tramo : tramos.getOrDefault(dia.getDayOfWeek().getValue(), List.of())) {
                for (LocalTime h = tramo.inicio(); !h.plus(DURACION).isAfter(tramo.fin()); h = h.plus(DURACION)) {
                    Instant inicio = dia.atTime(h).atZone(COLOMBIA).toInstant();
                    if (inicio.isBefore(desde) || !inicio.isBefore(hasta)) {
                        continue;
                    }
                    boolean tuya = mias.contains(inicio);
                    Estado estado;
                    if (noHabiles.contains(dia) || !dentroDeLaVentana(inicio, dia, ahora, hoy)) {
                        estado = Estado.NO_HABIL;
                    } else if (ocupadas.contains(inicio) || tuya) {
                        estado = Estado.OCUPADO;
                    } else {
                        estado = Estado.LIBRE;
                    }
                    ZonedDateTime local = inicio.atZone(zona);
                    franjas.add(new Franja(local.toLocalDate(), local.toLocalTime(),
                            local.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            inicio.atZone(COLOMBIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), estado, tuya));
                }
            }
        }
        return franjas; // ya en orden: días, tramos y horas se recorren de menor a mayor
    }

    /**
     * RN-01 comprobada en el servidor: ¿es {@code inicio} una franja que se puede agendar (sin mirar si otra cita
     * ya la ocupa)? Nunca se confía en lo que mostró el calendario.
     */
    public boolean esFranjaAgendable(Instant inicio) {
        ZonedDateTime enColombia = inicio.atZone(COLOMBIA);
        LocalDate dia = enColombia.toLocalDate();
        LocalTime hora = enColombia.toLocalTime();
        if (hora.getMinute() != 0 || hora.getSecond() != 0 || hora.getNano() != 0) {
            return false;
        }
        boolean enHorario = horario.tramos().getOrDefault(dia.getDayOfWeek().getValue(), List.of()).stream()
                .anyMatch(t -> !hora.isBefore(t.inicio()) && !hora.plus(DURACION).isAfter(t.fin()));
        if (!enHorario || !festivos.entre(dia, dia).isEmpty()) {
            return false;
        }
        return dentroDeLaVentana(inicio, dia, Instant.now(reloj), LocalDate.now(reloj));
    }

    /** Desde 24 horas después de ahora, y hasta hoy + 30 días (fecha de Colombia). */
    private static boolean dentroDeLaVentana(Instant inicio, LocalDate dia, Instant ahora, LocalDate hoy) {
        return !inicio.isBefore(ahora.plus(ANTELACION_MINIMA)) && !dia.isAfter(hoy.plusDays(DIAS_MAXIMOS));
    }
}
