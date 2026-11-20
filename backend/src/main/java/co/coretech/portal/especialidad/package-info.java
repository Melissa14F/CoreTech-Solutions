/**
 * Módulo <b>Especialidad</b>.
 * <p>
 * Desarrolladores con su especialidad, equipos identificados por su desarrollador (Product Owner) e integrantes. Tablas: desarrolladores, equipos, integrantes_equipo. HU-25, HU-26.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Otros módulos solo usan lo que está en la raíz de este paquete (servicio público o eventos).
 */
@ApplicationModule(displayName = "Especialidad")
package co.coretech.portal.especialidad;

import org.springframework.modulith.ApplicationModule;
