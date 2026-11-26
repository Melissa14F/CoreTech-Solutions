package co.coretech.portal.seguimiento.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas UNITARIAS de {@link TipoAdjunto} (HU-03, RNF-01): el tipo de un adjunto se reconoce por su CONTENIDO, nunca por la
 * extensión ni por lo que declare el navegador, y un SVG con código ejecutable se rechaza. Sin Spring ni base de datos.
 */
class TipoAdjuntoTest {

    static byte[] png() {
        return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
    }

    static byte[] texto(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Reconoce PNG, JPG y PDF por sus primeros bytes y da su extensión y su tipo MIME")
    void reconocePorLosPrimerosBytes() {
        assertThat(TipoAdjunto.reconocer(png())).contains(TipoAdjunto.PNG);
        assertThat(TipoAdjunto.reconocer(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0 })).contains(TipoAdjunto.JPEG);
        assertThat(TipoAdjunto.reconocer(texto("%PDF-1.7\n..."))).contains(TipoAdjunto.PDF);
        assertThat(TipoAdjunto.PNG.extension()).isEqualTo("png");
        assertThat(TipoAdjunto.PNG.tipoMime()).isEqualTo("image/png");
        assertThat(TipoAdjunto.JPEG.extension()).isEqualTo("jpg");
        assertThat(TipoAdjunto.SVG.tipoMime()).isEqualTo("image/svg+xml");
        assertThat(TipoAdjunto.PDF.tipoMime()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("Un ejecutable renombrado como .png (empieza con «MZ») no pasa: el nombre no cuenta")
    void unExeRenombradoNoPasa() {
        byte[] exe = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 };

        assertThat(TipoAdjunto.reconocer(exe)).isEmpty();
    }

    @Test
    @DisplayName("Valores límite: un PNG con 7 bytes de firma, un JPG con 2 y un PDF con 4 están incompletos y no pasan")
    void firmasIncompletas() {
        assertThat(TipoAdjunto.reconocer(Arrays.copyOf(png(), 7))).isEmpty();
        assertThat(TipoAdjunto.reconocer(new byte[] { (byte) 0xFF, (byte) 0xD8 })).isEmpty();
        assertThat(TipoAdjunto.reconocer(texto("%PDF"))).isEmpty();
        assertThat(TipoAdjunto.reconocer(Arrays.copyOf(png(), 8))).contains(TipoAdjunto.PNG);
    }

    @Test
    @DisplayName("Un archivo vacío no es ningún tipo y no lanza error")
    void vacio() {
        assertThat(TipoAdjunto.reconocer(new byte[0])).isEmpty();
    }

    @ParameterizedTest(name = "SVG válido que empieza con «{0}»")
    @ValueSource(strings = {
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>",
            "  \n<svg viewBox=\"0 0 10 10\"><circle r=\"4\"/></svg>",
            "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0\"/></svg>",
            "<!-- boceto -->\n<svg><g/></svg>",
            "<!DOCTYPE svg>\n<svg><g/></svg>"
    })
    void aceptaUnSvgLimpio(String svg) {
        assertThat(TipoAdjunto.reconocer(texto(svg))).contains(TipoAdjunto.SVG);
    }

    @Test
    @DisplayName("Un SVG con BOM al inicio (lo que guardan algunos editores) se acepta")
    void svgConBom() {
        assertThat(TipoAdjunto.reconocer(texto("﻿<svg><g/></svg>"))).contains(TipoAdjunto.SVG);
    }

    @ParameterizedTest(name = "SVG con código peligroso: {0}")
    @ValueSource(strings = {
            "<svg><script>alert(1)</script></svg>",
            "<svg onload=\"alert(1)\"><g/></svg>",
            "<svg><g onclick = 'x()'/></svg>",
            "<svg><a href=\"javascript:alert(1)\"><g/></a></svg>",
            "<svg><foreignObject><div>hola</div></foreignObject></svg>",
            "<svg><iframe src=\"https://otro.com\"/></svg>",
            "<svg><embed src=\"x\"/></svg>",
            "<svg><object data=\"x\"/></svg>",
            "<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><svg>&xxe;</svg>",
            "<SVG><SCRIPT>alert(1)</SCRIPT></SVG>"
    })
    void rechazaUnSvgConCodigo(String svg) {
        assertThat(TipoAdjunto.reconocer(texto(svg))).isEmpty();
    }

    @Test
    @DisplayName("Un texto cualquiera, un HTML o un JSON no son un SVG")
    void textoQueNoEsSvg() {
        assertThat(TipoAdjunto.reconocer(texto("hola, esto es un texto"))).isEmpty();
        assertThat(TipoAdjunto.reconocer(texto("<html><body><svg></svg></body></html>"))).isEmpty();
        assertThat(TipoAdjunto.reconocer(texto("{\"a\":1}"))).isEmpty();
    }

    @Test
    @DisplayName("Bytes que no son UTF-8 válido no pasan como SVG")
    void noEsUtf8() {
        byte[] malos = { '<', 's', 'v', 'g', '>', (byte) 0xC3, (byte) 0x28, '<', '/', 's', 'v', 'g', '>' };

        assertThat(TipoAdjunto.reconocer(malos)).isEmpty();
    }

    @Test
    @DisplayName("El «<svg» debe aparecer en los primeros 4096 caracteres: un comentario enorme antes lo vuelve sospechoso")
    void svgMuyDespues() {
        String relleno = "<!-- " + "x".repeat(5000) + " -->";

        assertThat(TipoAdjunto.reconocer(texto(relleno + "<svg><g/></svg>"))).isEmpty();
    }
}
