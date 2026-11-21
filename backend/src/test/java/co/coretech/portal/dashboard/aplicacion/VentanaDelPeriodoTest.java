package co.coretech.portal.dashboard.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import co.coretech.portal.compartido.aplicacion.ReglaNegocioException;
import co.coretech.portal.dashboard.aplicacion.ServicioDashboard.Ventana;

/** El periodo del dashboard (HU-23): semana de lunes a domingo, mes y trimestre calendario, cada uno con el anterior de su misma duración. */
class VentanaDelPeriodoTest {

    @Test
    void laSemanaVaDeLunesAlLunesSiguienteYLaAnteriorEsLaDeAtras() {
        Ventana v = ServicioDashboard.ventana("semana", LocalDate.of(2026, 11, 11)); // miércoles
        assertThat(v.desde()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(v.hasta()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(v.anteriorDesde()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(v.anteriorHasta()).isEqualTo(LocalDate.of(2026, 11, 9));
        // Un domingo todavía es de la misma semana
        assertThat(ServicioDashboard.ventana("semana", LocalDate.of(2026, 11, 15)).desde()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void elMesTomaElMesCalendarioYEnEneroLaComparacionEsDiciembre() {
        Ventana v = ServicioDashboard.ventana("mes", LocalDate.of(2027, 1, 20));
        assertThat(v.desde()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(v.hasta()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(v.anteriorDesde()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(v.etiqueta()).isEqualTo("Citas en enero");
        assertThat(v.etiquetaAnterior()).isEqualTo("diciembre");
    }

    @Test
    void elTrimestreTomaTresMesesCalendario() {
        Ventana cuarto = ServicioDashboard.ventana("trimestre", LocalDate.of(2026, 11, 11));
        assertThat(cuarto.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(cuarto.hasta()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(cuarto.anteriorDesde()).isEqualTo(LocalDate.of(2026, 7, 1));
        Ventana primero = ServicioDashboard.ventana("trimestre", LocalDate.of(2027, 2, 28));
        assertThat(primero.desde()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(primero.anteriorDesde()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void otroPeriodoEsUnErrorDeValidacionEnElCampoPeriodo() {
        assertThatThrownBy(() -> ServicioDashboard.ventana("año", LocalDate.of(2026, 11, 11))).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Elige semana, mes o trimestre.");
    }
}
