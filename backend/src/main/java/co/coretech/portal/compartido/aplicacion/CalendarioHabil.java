package co.coretech.portal.compartido.aplicacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.compartido.datos.DiasNoHabilesRepositorio;

/**
 * Días hábiles del negocio: lunes a viernes, sin los festivos de {@code dias_no_habiles}.
 * <p>
 * Lo usan los plazos de días hábiles: 3 para responder el plan o aprobar una fase (RN-17, RN-29),
 * 10 para que venza la propuesta (RN-29), 5 y 7 para el proyecto "en riesgo" (HU-17).
 * Las fechas son de la zona del negocio (America/Bogota); pídelas con {@code LocalDate.now(reloj)}.
 */
@Service
@Transactional(readOnly = true)
public class CalendarioHabil {

    /** Días calendario que se leen de festivos por consulta: más de un mes hábil, con margen. */
    private static final int VENTANA_DIAS = 62;

    private final DiasNoHabilesRepositorio festivos;

    public CalendarioHabil(DiasNoHabilesRepositorio festivos) {
        this.festivos = festivos;
    }

    /** Lunes a viernes y no festivo. */
    public boolean esDiaHabil(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha");
        return !esFinDeSemana(fecha) && festivos.entre(fecha, fecha).isEmpty();
    }

    /**
     * La fecha que queda {@code dias} días hábiles después de {@code desde}, sin contar {@code desde}.
     * Por ejemplo, un plan enviado el viernes con 3 días hábiles de plazo vence el miércoles siguiente
     * (o el jueves, si el lunes es festivo). Con 0 devuelve {@code desde}.
     */
    public LocalDate sumarDiasHabiles(LocalDate desde, int dias) {
        Objects.requireNonNull(desde, "desde");
        if (dias < 0) {
            throw new IllegalArgumentException("Los días hábiles a sumar no pueden ser negativos: " + dias);
        }
        LocalDate fecha = desde;
        int faltan = dias;
        LocalDate finVentana = desde;
        Set<LocalDate> noHabiles = Set.of();
        while (faltan > 0) {
            fecha = fecha.plusDays(1);
            if (fecha.isAfter(finVentana)) {
                finVentana = fecha.plusDays(VENTANA_DIAS);
                noHabiles = festivos.entre(fecha, finVentana);
            }
            if (!esFinDeSemana(fecha) && !noHabiles.contains(fecha)) {
                faltan--;
            }
        }
        return fecha;
    }

    /**
     * Días hábiles después de {@code desde} hasta {@code hasta} incluido, es decir, en el intervalo
     * ({@code desde}, {@code hasta}]: {@code diasHabilesEntre(d, sumarDiasHabiles(d, n)) == n}.
     * Si {@code hasta} es anterior a {@code desde}, el resultado es negativo.
     */
    public int diasHabilesEntre(LocalDate desde, LocalDate hasta) {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        if (hasta.isBefore(desde)) {
            return -diasHabilesEntre(hasta, desde);
        }
        if (hasta.equals(desde)) {
            return 0;
        }
        Set<LocalDate> noHabiles = festivos.entre(desde.plusDays(1), hasta);
        int dias = 0;
        for (LocalDate fecha = desde.plusDays(1); !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (!esFinDeSemana(fecha) && !noHabiles.contains(fecha)) {
                dias++;
            }
        }
        return dias;
    }

    private static boolean esFinDeSemana(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }
}
