package co.coretech.portal.acceso.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import co.coretech.portal.compartido.exception.ReglaNegocioException;

/**
 * Pruebas UNITARIAS de {@link PoliticaContrasena} (RN-22): mínimo 8 caracteres, con mayúscula, número y símbolo, confirmada
 * en un segundo campo y de máximo 72 bytes (límite de BCrypt). Sin Spring ni base de datos.
 */
class PoliticaContrasenaTest {

    final PoliticaContrasena politica = new PoliticaContrasena();

    @ParameterizedTest(name = "«{0}» cumple")
    @ValueSource(strings = { "Abcdef1!", "Coretech#2031", "Ñandú9?mañana", "A1_aaaaa" })
    @DisplayName("Acepta una contraseña con 8 o más caracteres, mayúscula, número y símbolo")
    void aceptaLasQueCumplen(String clave) {
        assertThat(PoliticaContrasena.cumple(clave)).isTrue();
    }

    @ParameterizedTest(name = "«{0}» no cumple")
    @ValueSource(strings = { "Abcde1!", "abcdefg1!", "abcdefg1!x", "Abcdefgh!", "Abcdefg12", "Abcd efg 1", "        " })
    @DisplayName("Rechaza la que le falta algún requisito (corta, sin mayúscula, sin número o sin símbolo)")
    void rechazaLasQueNoCumplen(String clave) {
        assertThat(PoliticaContrasena.cumple(clave)).isFalse();
    }

    @Test
    @DisplayName("Un espacio no cuenta como símbolo")
    void elEspacioNoEsSimbolo() {
        assertThat(PoliticaContrasena.cumple("Abcdefg1 ")).isFalse();
    }

    @Test
    @DisplayName("Valor límite: 8 caracteres exactos sí; 7 no")
    void valorLimiteDeLaLongitud() {
        assertThat(PoliticaContrasena.cumple("Abcde1!x")).isTrue();
        assertThat(PoliticaContrasena.cumple("Abcd1!x")).isFalse();
    }

    @Test
    @DisplayName("Una contraseña nula no cumple y no lanza error")
    void nulaNoCumple() {
        assertThat(PoliticaContrasena.cumple(null)).isFalse();
    }

    @Test
    @DisplayName("validar: la que cumple y coincide con su confirmación pasa")
    void validaCuandoTodoEstaBien() {
        assertThatCode(() -> politica.validar("Abcdef1!", "Abcdef1!", "password", "confirmacion")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validar: si no cumple, el error va en el campo de la contraseña")
    void noCumpleDaErrorEnElCampoDeLaContrasena() {
        assertThatThrownBy(() -> politica.validar("abc", "abc", "passwordNueva", "confirmacion"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.campo()).contains("passwordNueva");
                    assertThat(e.getMessage()).isEqualTo(PoliticaContrasena.NO_CUMPLE);
                });
    }

    @Test
    @DisplayName("validar: si la confirmación es distinta, el error va en el campo de la confirmación")
    void confirmacionDistintaDaErrorEnLaConfirmacion() {
        assertThatThrownBy(() -> politica.validar("Abcdef1!", "Abcdef1?", "password", "confirmacion"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.campo()).contains("confirmacion");
                    assertThat(e.getMessage()).isEqualTo(PoliticaContrasena.NO_COINCIDEN);
                });
    }

    @Test
    @DisplayName("validar: más de 72 bytes es 422 y no un 500 de BCrypt")
    void masDe72BytesSeRechaza() {
        String larga = "Aa1!" + "x".repeat(69);
        assertThatThrownBy(() -> politica.validar(larga, larga, "password", "confirmacion"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.getMessage()).isEqualTo(PoliticaContrasena.DEMASIADO_LARGA));
    }

    @Test
    @DisplayName("validar: 72 bytes exactos todavía pasa")
    void setentaYDosBytesPasan() {
        String justa = "Aa1!" + "x".repeat(68);
        assertThatCode(() -> politica.validar(justa, justa, "password", "confirmacion")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validar: los bytes cuentan, no los caracteres (una «ñ» pesa 2)")
    void cuentanLosBytesNoLosCaracteres() {
        String conEnies = "Aa1!" + "ñ".repeat(35);
        assertThatThrownBy(() -> politica.validar(conEnies, conEnies, "password", "confirmacion"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
