/**
 * Módulo <b>Comunicación</b>.
 * <p>
 * Mensajes de un proyecto, solicitudes de reunión de seguimiento y notificaciones. Tablas: mensajes, solicitudes_reunion, notificaciones. HU-14, HU-15, HU-16, HU-24.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Otros módulos solo usan lo que está en la raíz de este paquete (servicio público o eventos).
 */
@ApplicationModule(displayName = "Comunicación")
package co.coretech.portal.comunicacion;

import org.springframework.modulith.ApplicationModule;
