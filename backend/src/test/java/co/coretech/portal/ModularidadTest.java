package co.coretech.portal;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Falla si un módulo usa clases internas de otro (subpaquetes web, aplicacion, dominio, datos)
 * o si hay ciclos entre módulos. Ver reglas de dependencia en docs/06-arquitectura.md.
 */
class ModularidadTest {

    static final ApplicationModules MODULOS = ApplicationModules.of(PortalApplication.class);

    @Test
    void losModulosRespetanSusLimites() {
        MODULOS.verify();
    }

    @Test
    void documentaLosModulos() {
        // Diagramas y fichas de cada módulo en target/spring-modulith-docs
        new Documenter(MODULOS).writeDocumentation();
    }
}
