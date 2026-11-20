/**
 * Módulo <b>Catálogo y sitio</b>.
 * <p>
 * Servicios que ofrece la empresa y textos editables de la página principal. Tablas: servicios, contenido_sitio. HU-07, HU-18, HU-19.
 * <p>
 * Capas: {@code web} (controladores y DTO) → {@code aplicacion} (servicios con las reglas)
 * → {@code datos} (repositorios); {@code dominio} guarda entidades y enums.
 * Otros módulos solo usan lo que está en la raíz de este paquete (servicio público o eventos).
 */
@ApplicationModule(displayName = "Catálogo y sitio")
package co.coretech.portal.catalogo;

import org.springframework.modulith.ApplicationModule;
