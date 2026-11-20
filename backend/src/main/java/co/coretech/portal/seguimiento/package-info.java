/**
 * Módulo <b>Seguimiento</b>.
 * <p>
 * Fases y pasos del proyecto, entregas, aprobaciones, ajustes, correcciones, garantía y adjuntos. Tablas: proyecto_fases, pasos, adjuntos_paso, solicitudes_cambio. HU-03, HU-04, HU-11, HU-12, HU-13.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Otros módulos solo usan lo que está en la raíz de este paquete (servicio público o eventos).
 */
@ApplicationModule(displayName = "Seguimiento")
package co.coretech.portal.seguimiento;

import org.springframework.modulith.ApplicationModule;
