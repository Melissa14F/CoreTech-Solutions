/**
 * Módulo <b>Proyectos y planificación</b>.
 * <p>
 * Proyectos creados desde una cita atendida, plan de trabajo, plantillas de fases, cotización y cambios de un plan aprobado. Tablas: proyectos, plantilla_fases, plantilla_pasos, respuestas_plan, cambios_plan. HU-02, HU-17, HU-27 a HU-30.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Otros módulos solo usan lo que está en la raíz de este paquete (servicio público o eventos).
 */
@ApplicationModule(displayName = "Proyectos y planificación")
package co.coretech.portal.proyectos;

import org.springframework.modulith.ApplicationModule;
