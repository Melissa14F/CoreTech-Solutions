/** Correo del sistema (D-51): la cuenta de Gmail que envía los correos del portal. */
import { api } from '../api/cliente';

export interface EstadoCorreo {
  /** El cliente OAuth de Google está configurado en el servidor (CORETECH_GOOGLE_ID y _SECRETO). */
  configurado: boolean;
  conectado: boolean;
  cuenta: string | null;
  /** CONECTADO, o REVOCADO si Google retiró el permiso. */
  estado: 'CONECTADO' | 'REVOCADO' | null;
  conectadoEn: string | null;
  ultimoError: string | null;
  ultimoErrorEn: string | null;
}

export interface ResultadoPrueba {
  canal: 'GMAIL' | 'SMTP';
  mensaje: string;
}

export const correoSistemaApi = {
  estado: () => api.obtener<EstadoCorreo>('/correo-sistema'),
  /** Devuelve la URL de Google a la que se lleva el navegador para iniciar sesión y aceptar el permiso. */
  conectar: () => api.crear<{ url: string }>('/correo-sistema/gmail/conexion'),
  enviarPrueba: (para: string) => api.crear<ResultadoPrueba>('/correo-sistema/prueba', { para }),
  desconectar: () => api.eliminar<void>('/correo-sistema/gmail'),
};

/** Lleva el navegador a otra página (Google). Aparte para poder simularlo en las pruebas. */
export const navegador = {
  ir: (url: string) => window.location.assign(url),
};
