/** Mensajes entre el cliente y el desarrollador (Product Owner) de un proyecto (HU-14, RN-14). */
import { api } from '../api/cliente';

export const MIN_MENSAJE = 10;
export const MAX_MENSAJE = 1000;
/** Pasados estos minutos desde el envío, el autor ya no edita su mensaje. */
export const MINUTOS_PARA_EDITAR = 5;

export type RolAutor = 'CLIENTE' | 'PRODUCT_OWNER';

export interface Mensaje {
  id: string;
  autor: string;
  rol: RolAutor;
  /** Lo escribió quien está viendo el hilo. */
  esMio: boolean;
  /** Solo los mensajes del cliente llevan tema (D-41). */
  tema: string | null;
  contenido: string;
  creadoAt: string;
  editadoAt: string | null;
  /** Es del autor y aún no pasan los 5 minutos. */
  puedeEditar: boolean;
}

export interface Hilo {
  proyecto: string;
  /** Falso con el proyecto pausado, finalizado o cancelado: el hilo queda en solo lectura (RN-14). */
  puedeEscribir: boolean;
  /** Verdadero para el cliente: él elige el tema de la lista; el desarrollador responde sin tema. */
  escribeConTema: boolean;
  temas: string[];
  mensajes: Mensaje[];
}

export interface CuerpoMensaje {
  tema: string | null;
  contenido: string;
}

export const mensajesApi = {
  hilo: (codigo: string) => api.obtener<Hilo>(`/proyectos/${codigo}/mensajes`),
  enviar: (codigo: string, cuerpo: CuerpoMensaje) => api.crear<Mensaje>(`/proyectos/${codigo}/mensajes`, cuerpo),
  editar: (id: string, cuerpo: CuerpoMensaje) => api.modificar<Mensaje>(`/mensajes/${id}`, cuerpo),
};
