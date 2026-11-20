import { render } from '@testing-library/react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';

import { rutas } from '../rutas/enrutador';
import type { Usuario } from '../sesion/ContextoSesion';

/** Usuarios de demostración, como los devuelve GET /api/v1/auth/yo. */
export const LAURA: Usuario = {
  usuarioId: 'u-laura', nombre: 'Laura Gómez', email: 'laura.gomez@correo.com', rol: 'CLIENTE',
  permisos: ['citas.agendar', 'proyectos.ver_propios'], debeCambiarPassword: false,
};
export const ANDRES: Usuario = {
  usuarioId: 'u-andres', nombre: 'Andrés Pérez', email: 'andres.perez@coretechsolutions.co', rol: 'PRODUCT_OWNER',
  permisos: ['proyectos.registrar_avance'], debeCambiarPassword: false,
};
export const PATRICIA: Usuario = {
  usuarioId: 'u-patricia', nombre: 'Patricia León', email: 'patricia.leon@coretechsolutions.co', rol: 'ADMINISTRADOR',
  permisos: ['seguridad.cuentas'], debeCambiarPassword: false,
};

export interface RespuestaSimulada {
  estado: number;
  cuerpo?: unknown;
}

type Manejador = RespuestaSimulada | ((cuerpo: unknown) => RespuestaSimulada);

const NO_AUTENTICADO: RespuestaSimulada = { estado: 401, cuerpo: { errores: [{ mensaje: 'Tu sesión venció, ingresa de nuevo' }] } };

/**
 * Simula la API con un manejador por "MÉTODO /ruta" (sin /api/v1). GET /auth/yo responde con `sesion`
 * (o 401 si es null) salvo que se pase otro manejador. Devuelve el fetch simulado para revisar las llamadas.
 */
export function simularApi(sesion: Usuario | null, manejadores: Record<string, Manejador> = {}) {
  const todos: Record<string, Manejador> = {
    'GET /auth/yo': sesion ? { estado: 200, cuerpo: sesion } : NO_AUTENTICADO,
    'POST /auth/logout': { estado: 204 },
    ...manejadores,
  };
  const fetchSimulado = vi.fn(async (url: string, opciones?: RequestInit) => {
    const ruta = url.replace('/api/v1', '').split('?')[0];
    const metodo = opciones?.method ?? 'GET';
    const cuerpo: unknown = typeof opciones?.body === 'string' ? JSON.parse(opciones.body) : undefined;
    const manejador = todos[`${metodo} ${ruta}`];
    const respuesta = typeof manejador === 'function' ? manejador(cuerpo) : manejador ?? { estado: 404 };
    return new Response(respuesta.cuerpo === undefined ? null : JSON.stringify(respuesta.cuerpo), {
      status: respuesta.estado,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchSimulado);
  return fetchSimulado;
}

/** Cuerpo JSON enviado en la llamada `METODO /ruta` (la primera que coincida). */
export function cuerpoEnviado(fetchSimulado: ReturnType<typeof simularApi>, metodo: string, ruta: string): unknown {
  const llamada = fetchSimulado.mock.calls.find(([url, op]) => url === `/api/v1${ruta}` && (op?.method ?? 'GET') === metodo);
  return llamada && typeof llamada[1]?.body === 'string' ? JSON.parse(llamada[1].body) : undefined;
}

/** Abre la aplicación completa en una ruta (enrutador en memoria). */
export function abrirEn(ruta: string) {
  const enrutador = createMemoryRouter(rutas, { initialEntries: [ruta] });
  render(<RouterProvider router={enrutador} />);
  return enrutador;
}

/** Error de la API con el formato de docs/04-api.md. */
export const errores = (...lista: { campo?: string; mensaje: string }[]) => ({ errores: lista });
