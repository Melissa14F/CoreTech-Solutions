package co.coretech.portal.proyectos.service;

import java.time.LocalDate;

/** Fechas como las escribe el prototipo en los textos y avisos: «17 nov 2026» y «martes 17 nov». */
final class Fechas {

    private static final String[] MESES = { "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic" };
    private static final String[] DIAS = { "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo" };

    private Fechas() {
    }

    /** «16 nov 2026». */
    static String corta(LocalDate fecha) {
        return fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1] + " " + fecha.getYear();
    }

    /** «martes 17 nov». */
    static String conDia(LocalDate fecha) {
        return DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1];
    }
}
