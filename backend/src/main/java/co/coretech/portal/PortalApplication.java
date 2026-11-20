package co.coretech.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API del portal de CoreTech Solutions.
 * Monolito modular: cada paquete directo de {@code co.coretech.portal} es un módulo
 * (ver {@code docs/06-arquitectura.md}).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class) // la sesión es el JWT de la cookie, no hay usuario en memoria
@ConfigurationPropertiesScan
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
