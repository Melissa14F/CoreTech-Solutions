package co.coretech.portal.compartido;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas programadas (@Scheduled): limpieza diaria de datos (RNF-09) y, desde P10, el envío de correos
 * pendientes. Cada módulo declara las suyas; siempre con la zona America/Bogota.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ConfiguracionTareas {
}
