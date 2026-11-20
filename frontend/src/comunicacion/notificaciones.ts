/** Notificaciones de la campana (HU-24): GET /notificaciones y marcar como leídas. */
import { api } from '../api/cliente';

export interface Notificacion {
  notificacionId: string;
  tipo: string;
  titulo: string;
  mensaje: string;
  /** Ruta de la SPA donde se ve el cambio ("/cliente/mis-citas"), o null. */
  enlace: string | null;
  leida: boolean;
  fecha: string;
}

export interface Bandeja {
  noLeidas: number;
  /** Las no leídas primero y, dentro de cada grupo, de la más nueva a la más vieja (máximo 30). */
  notificaciones: Notificacion[];
}

/**
 * La campana se refresca sola cada minuto: si la sesión venció no saca a la persona de lo que está haciendo
 * (sin redirigir en el 401); la siguiente acción suya sí la llevará a iniciar sesión.
 */
export const notificacionesApi = {
  bandeja: () => api.obtener<Bandeja>('/notificaciones', { sinRedirigirEn401: true }),
  marcarLeida: (id: string) => api.modificar<void>(`/notificaciones/${id}/leida`),
  marcarTodas: () => api.modificar<void>('/notificaciones/leidas'),
};
