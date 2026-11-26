package co.coretech.portal.compartido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import co.coretech.portal.compartido.repository.DiasNoHabilesRepositorio;

/**
 * Días hábiles con un festivo de prueba: martes 8 de diciembre de 2026 (Inmaculada Concepción).
 * Semana: vie 4 · sáb 5 · dom 6 · lun 7 · mar 8 (festivo) · mié 9 · jue 10 · vie 11.
 */
class CalendarioHabilTest {

    static final LocalDate VIERNES_4 = LocalDate.of(2026, 12, 4);
    static final LocalDate SABADO_5 = LocalDate.of(2026, 12, 5);
    static final LocalDate DOMINGO_6 = LocalDate.of(2026, 12, 6);
    static final LocalDate LUNES_7 = LocalDate.of(2026, 12, 7);
    static final LocalDate FESTIVO_MARTES_8 = LocalDate.of(2026, 12, 8);
    static final LocalDate MIERCOLES_9 = LocalDate.of(2026, 12, 9);
    static final LocalDate JUEVES_10 = LocalDate.of(2026, 12, 10);
    static final LocalDate VIERNES_11 = LocalDate.of(2026, 12, 11);

    /** Repositorio en memoria: devuelve los festivos del conjunto que caen en el rango. */
    static DiasNoHabilesRepositorio festivos(LocalDate... fechas) {
        Set<LocalDate> todos = Set.of(fechas);
        return (desde, hasta) -> todos.stream()
                .filter(f -> !f.isBefore(desde) && !f.isAfter(hasta))
                .collect(Collectors.toSet());
    }

    final CalendarioHabil calendario = new CalendarioHabil(festivos(FESTIVO_MARTES_8));

    @Test
    void entreSemanaSinFestivoEsHabil() {
        assertThat(calendario.esDiaHabil(LUNES_7)).isTrue();
        assertThat(calendario.esDiaHabil(VIERNES_4)).isTrue();
    }

    @Test
    void elFinDeSemanaNoEsHabil() {
        assertThat(calendario.esDiaHabil(SABADO_5)).isFalse();
        assertThat(calendario.esDiaHabil(DOMINGO_6)).isFalse();
    }

    @Test
    void unFestivoNoEsHabil() {
        assertThat(calendario.esDiaHabil(FESTIVO_MARTES_8)).isFalse();
    }

    @Test
    void sumarSaltaElFinDeSemanaYElFestivo() {
        assertThat(calendario.sumarDiasHabiles(VIERNES_4, 1)).isEqualTo(LUNES_7);
        assertThat(calendario.sumarDiasHabiles(VIERNES_4, 2)).isEqualTo(MIERCOLES_9);
        // Plazo de 3 días hábiles de un plan enviado el viernes 4 (RN-29)
        assertThat(calendario.sumarDiasHabiles(VIERNES_4, 3)).isEqualTo(JUEVES_10);
    }

    @Test
    void sumarDesdeUnDiaNoHabilCuentaDesdeElSiguienteHabil() {
        assertThat(calendario.sumarDiasHabiles(SABADO_5, 1)).isEqualTo(LUNES_7);
        assertThat(calendario.sumarDiasHabiles(FESTIVO_MARTES_8, 1)).isEqualTo(MIERCOLES_9);
    }

    @Test
    void sumarCeroDevuelveLaMismaFecha() {
        assertThat(calendario.sumarDiasHabiles(SABADO_5, 0)).isEqualTo(SABADO_5);
    }

    @Test
    void sumarMuchosDiasCruzaVariasVentanasDeFestivos() {
        CalendarioHabil conFestivoLejano = new CalendarioHabil(festivos(LocalDate.of(2027, 3, 22)));
        // 60 días hábiles desde el viernes 1 de enero de 2027: 12 semanas exactas (lunes a viernes)
        // terminan el viernes 26 de marzo; el festivo del lunes 22 de marzo corre un día más.
        assertThat(conFestivoLejano.sumarDiasHabiles(LocalDate.of(2027, 1, 1), 60))
                .isEqualTo(LocalDate.of(2027, 3, 29));
    }

    @Test
    void noSeSumanDiasNegativos() {
        assertThatThrownBy(() -> calendario.sumarDiasHabiles(VIERNES_4, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entreCuentaDespuesDelInicioHastaElFinIncluido() {
        // lun 7, mié 9, jue 10 y vie 11: el sábado, el domingo y el festivo no cuentan
        assertThat(calendario.diasHabilesEntre(VIERNES_4, VIERNES_11)).isEqualTo(4);
        assertThat(calendario.diasHabilesEntre(VIERNES_4, VIERNES_4)).isZero();
        assertThat(calendario.diasHabilesEntre(VIERNES_4, DOMINGO_6)).isZero();
    }

    @Test
    void entreConLasFechasAlRevesEsNegativo() {
        assertThat(calendario.diasHabilesEntre(VIERNES_11, VIERNES_4)).isEqualTo(-4);
    }

    @Test
    void sumarYContarSonInversos() {
        for (int n = 0; n <= 15; n++) {
            LocalDate fin = calendario.sumarDiasHabiles(VIERNES_4, n);
            assertThat(calendario.diasHabilesEntre(VIERNES_4, fin)).as("n = %d", n).isEqualTo(n);
        }
    }
}
