package co.coretech.portal.compartido;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del sistema en la zona del negocio (America/Bogota).
 * <p>
 * Todo el código pide la hora a este {@link Clock} ({@code Instant.now(reloj)}, {@code LocalDate.now(reloj)})
 * y nunca a {@code now()} sin argumentos: así las pruebas fijan la fecha y revisan los plazos de
 * 3 y 10 días hábiles, las 24 horas de anticipación de una cita o los 30 días de garantía.
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionTiempo {

    @Bean
    Clock reloj(PropiedadesCoretech propiedades) {
        return Clock.system(propiedades.zonaNegocio());
    }
}
