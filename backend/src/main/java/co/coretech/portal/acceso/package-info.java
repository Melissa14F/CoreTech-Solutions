/**
 * Módulo <b>Acceso y seguridad</b>.
 * <p>
 * Registro, inicio y cierre de sesión, recuperación de contraseña, usuarios, roles y permisos, y consulta de la auditoría. Tablas: roles, permisos, rol_permisos, usuarios, tokens_recuperacion. HU-05, HU-06, HU-20, HU-21, HU-22.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Otros módulos solo usan lo que está en la raíz de este paquete (servicio público o eventos).
 */
@ApplicationModule(displayName = "Acceso y seguridad")
package co.coretech.portal.acceso;

import org.springframework.modulith.ApplicationModule;
