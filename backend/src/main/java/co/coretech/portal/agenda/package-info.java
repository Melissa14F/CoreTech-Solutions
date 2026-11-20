/**
 * Módulo <b>Agenda</b>.
 * <p>
 * Citas con la empresa, disponibilidad, cuestionario de la reunión, horario laboral y días no hábiles. Tablas: horario_laboral, dias_no_habiles, citas, cuestionarios_cita. HU-01, HU-08, HU-09, HU-10.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Otros módulos solo usan lo que está en la raíz de este paquete (servicio público o eventos).
 */
@ApplicationModule(displayName = "Agenda")
package co.coretech.portal.agenda;

import org.springframework.modulith.ApplicationModule;
