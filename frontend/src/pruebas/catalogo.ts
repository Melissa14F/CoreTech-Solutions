import type { ServicioGestion, ServicioPublico } from '../catalogo/api';

/** Servicios de ejemplo, como los devuelve la API. */
export const TIENDA: ServicioGestion = {
  servicioId: 's-tienda', nombre: 'Desarrollo de tienda en línea', slug: 'tienda-en-linea',
  descripcionCorta: 'Catálogo, carrito de compras y pagos en línea para vender por internet.',
  imagenUrl: 'img/servicios/tienda-en-linea.webp', imagenAlt: 'Ilustración de una tienda en línea con productos y carrito de compras',
  orden: 1, visible: true, citasUltimos6Meses: 26, fasesPlantilla: 5,
};
export const WEB: ServicioGestion = {
  servicioId: 's-web', nombre: 'Página web corporativa', slug: 'web-corporativa',
  descripcionCorta: 'Sitio institucional adaptable a celular, rápido y fácil de actualizar.',
  imagenUrl: 'img/servicios/web-corporativa.webp', imagenAlt: 'Ilustración de un sitio web en computador y en celular',
  orden: 2, visible: true, citasUltimos6Meses: 1, fasesPlantilla: 5,
};
export const APP_OCULTA: ServicioGestion = {
  servicioId: 's-app', nombre: 'Aplicación móvil', slug: 'app-movil',
  descripcionCorta: 'Apps para Android e iOS conectadas a los procesos de tu negocio.',
  imagenUrl: '/api/v1/servicios/imagenes/0f8c2a1e-1111-2222-3333-444455556666.png', imagenAlt: 'Ilustración de dos pantallas de una aplicación',
  orden: 3, visible: false, citasUltimos6Meses: 0, fasesPlantilla: 4,
};

export const publico = (s: ServicioGestion): ServicioPublico => ({
  servicioId: s.servicioId, nombre: s.nombre, slug: s.slug, descripcionCorta: s.descripcionCorta, imagenUrl: s.imagenUrl, imagenAlt: s.imagenAlt,
});
