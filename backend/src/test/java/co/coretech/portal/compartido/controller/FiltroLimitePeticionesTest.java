package co.coretech.portal.compartido.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.coretech.portal.compartido.PropiedadesCoretech;

/** Límite de intentos por IP en las rutas públicas de acceso (RNF-10), con un reloj que se puede mover. */
class FiltroLimitePeticionesTest {

    /** Reloj de prueba: empieza el 19 de noviembre de 2026 y avanza cuando se le pide. */
    static final class RelojMovible extends Clock {
        private Instant ahora = Instant.parse("2026-11-19T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Bogota");
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    final RelojMovible reloj = new RelojMovible();
    final AtomicInteger pasaron = new AtomicInteger();
    final FiltroLimitePeticiones filtro = new FiltroLimitePeticiones(new PropiedadesCoretech.Limites(3, 2, null, null),
            new LimitadorPeticiones(reloj), new EscritorErrores(new ObjectMapper()));

    private MockHttpServletResponse enviar(String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setRemoteAddr(ip);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, (p, r) -> pasaron.incrementAndGet());
        return respuesta;
    }

    @Test
    void pasadoElLimiteDeInicioDeSesionResponde429HastaQuePasaElMinuto() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(enviar("POST", "/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse bloqueada = enviar("POST", "/api/v1/auth/login", "10.0.0.1");
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("60");
        assertThat(bloqueada.getContentAsString()).contains(FiltroLimitePeticiones.MENSAJE);
        assertThat(pasaron).hasValue(3);

        // Otra IP no se ve afectada, y al pasar el minuto vuelve a poder
        assertThat(enviar("POST", "/api/v1/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
        reloj.avanzar(Duration.ofSeconds(61));
        assertThat(enviar("POST", "/api/v1/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void cadaRutaTieneSuPropioLimiteYLasDemasNoSeCuentan() throws Exception {
        enviar("POST", "/api/v1/auth/recuperacion", "10.0.0.3");
        enviar("POST", "/api/v1/auth/recuperacion", "10.0.0.3");
        assertThat(enviar("POST", "/api/v1/auth/recuperacion", "10.0.0.3").getStatus()).isEqualTo(429);
        // El inicio de sesión de la misma IP sigue libre, y una ruta sin límite nunca se bloquea
        assertThat(enviar("POST", "/api/v1/auth/login", "10.0.0.3").getStatus()).isEqualTo(200);
        for (int i = 0; i < 20; i++) {
            assertThat(enviar("GET", "/api/v1/servicios", "10.0.0.3").getStatus()).isEqualTo(200);
        }
    }
}
