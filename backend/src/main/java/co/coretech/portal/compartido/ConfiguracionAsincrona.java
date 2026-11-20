package co.coretech.portal.compartido;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Tareas en segundo plano (@Async), por ejemplo el envío de correos: no bloquean la petición. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class ConfiguracionAsincrona {
}
