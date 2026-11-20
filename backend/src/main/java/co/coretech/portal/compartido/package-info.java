/**
 * Módulo <b>Compartido</b>.
 * <p>
 * Piezas transversales que usan todos los módulos: seguridad (SecurityFilterChain), registro de auditoría, manejador global de errores, calendario hábil, correo y notificaciones asíncronas, almacenamiento de archivos y tareas programadas. Tabla: auditoria. Es un módulo abierto: sus subpaquetes los pueden usar los demás módulos.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Por ser transversal, no depende de ningún módulo de negocio.
 */
@ApplicationModule(displayName = "Compartido", type = ApplicationModule.Type.OPEN)
package co.coretech.portal.compartido;

import org.springframework.modulith.ApplicationModule;
