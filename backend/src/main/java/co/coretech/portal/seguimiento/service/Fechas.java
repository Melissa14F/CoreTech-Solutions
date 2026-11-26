package co.coretech.portal.seguimiento.service;

import java.time.LocalDate;

/** Fechas como las escribe el prototipo en los avisos: «martes 17 nov». */
final class Fechas {

    private static final String[] MESES = { "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic" };
    private static final String[] DIAS = { "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo" };

    private Fechas() {
    }

    /** «martes 17 nov». */
    static String conDia(LocalDate fecha) {
        return DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1];
    }

    /** «16 nov 2026». */
    static String corta(LocalDate fecha) {
        return fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1] + " " + fecha.getYear();
    }
}
