import { api } from '../api/cliente';

/** Tarjeta del catálogo público (GET /servicios). */
export interface ServicioPublico {
  servicioId: string;
  nombre: string;
  slug: string;
  descripcionCorta: string;
  imagenUrl: string;
  imagenAlt: string;
}

/** Servicio en el panel del administrador (GET /servicios/gestion). */
export interface ServicioGestion extends ServicioPublico {
  orden: number;
  visible: boolean;
  citasUltimos6Meses: number;
  fasesPlantilla: number;
}

export interface DatosServicio {
  nombre: string;
  descripcionCorta: string;
  imagenUrl: string;
  imagenAlt: string;
  visible: boolean;
}

export interface TextoEditable {
  clave: string;
  seccion: string;
  valor: string;
  minLongitud: number;
  maxLongitud: number;
}

export interface ContenidoEdicion {
  textos: TextoEditable[];
  ultimaPublicacion: { fecha: string; nombre: string } | null;
}

/** Imágenes de la galería (public/img/servicios), las mismas que acepta el backend. */
export const GALERIA = [
  { url: 'img/servicios/tienda-en-linea.webp', nombre: 'Desarrollo de tienda en línea' },
  { url: 'img/servicios/web-corporativa.webp', nombre: 'Página web corporativa' },
  { url: 'img/servicios/app-movil.webp', nombre: 'Aplicación móvil' },
  { url: 'img/servicios/sistema-inventario.webp', nombre: 'Sistema de inventario' },
  { url: 'img/servicios/integracion-pagos.webp', nombre: 'Integración de pagos' },
];

/** Dirección para <img>: las de la galería son archivos de la SPA; las subidas, de la API. */
export const srcImagen = (url: string) => (url.startsWith('/') ? url : `/${url}`);

export const catalogo = {
  visibles: () => api.obtener<ServicioPublico[]>('/servicios', { sinRedirigirEn401: true }),
  todos: () => api.obtener<ServicioGestion[]>('/servicios/gestion'),
  crear: (datos: DatosServicio) => api.crear<ServicioGestion>('/servicios', datos),
  editar: (id: string, datos: DatosServicio) => api.reemplazar<ServicioGestion>(`/servicios/${id}`, datos),
  cambiarVisibilidad: (id: string, visible: boolean) => api.modificar<ServicioGestion>(`/servicios/${id}/visible`, { visible }),
  mover: (id: string, direccion: -1 | 1) => api.modificar<ServicioGestion[]>(`/servicios/${id}/orden`, { direccion }),
  eliminar: (id: string) => api.eliminar<void>(`/servicios/${id}`),
  subirImagen: (archivo: File) => {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return api.crear<{ imagenUrl: string }>('/servicios/imagenes', datos);
  },
};

export const contenidoSitio = {
  publico: () => api.obtener<{ textos: Record<string, string> }>('/contenido', { sinRedirigirEn401: true }),
  edicion: () => api.obtener<ContenidoEdicion>('/contenido/edicion'),
  publicar: (textos: Record<string, string>) => api.reemplazar<ContenidoEdicion>('/contenido', { textos }),
};
