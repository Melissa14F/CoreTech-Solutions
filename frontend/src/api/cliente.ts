/**
 * Cliente HTTP de la API (/api/v1). Reglas de docs/04-api.md:
 * - La sesión viaja en una cookie HttpOnly: se envía con credentials 'include' y el JavaScript nunca la lee.
 * - Los errores llegan como { "errores": [{ "campo", "mensaje" }] }.
 * - 422 → errores por campo para mostrarlos bajo cada control; 401 → a iniciar sesión;
 *   los demás códigos → un mensaje claro para el usuario.
 */

export const PREFIJO_API = '/api/v1';

export interface ErrorDeCampo {
  campo?: string;
  mensaje: string;
}

/** Error de una petición, con lo necesario para mostrarlo en pantalla. */
export class ErrorApi extends Error {
  constructor(
    readonly estado: number,
    mensaje: string,
    readonly errores: ErrorDeCampo[] = [],
  ) {
    super(mensaje);
    this.name = 'ErrorApi';
  }

  /** Errores de un 422 agrupados por campo ({ fechaHora: "La hora que elegiste…" }), para el formulario. */
  get porCampo(): Record<string, string> {
    const resultado: Record<string, string> = {};
    for (const e of this.errores) {
      if (e.campo && !(e.campo in resultado)) resultado[e.campo] = e.mensaje;
    }
    return resultado;
  }

  /** Errores que no son de un campo concreto: se muestran en una alerta arriba del formulario. */
  get generales(): string[] {
    return this.errores.filter((e) => !e.campo).map((e) => e.mensaje);
  }
}

/** Mensajes cuando la respuesta no trae uno propio. */
const MENSAJES_POR_ESTADO: Record<number, string> = {
  401: 'Tu sesión venció, ingresa de nuevo.',
  403: 'No tienes permiso para hacer esta acción.',
  404: 'No encontramos lo que buscas.',
  409: 'Esta acción ya no se puede hacer. Recarga para ver lo último.',
  422: 'Revisa los datos marcados.',
  423: 'Tu cuenta está bloqueada por seguridad. Intenta más tarde.',
  429: 'Hiciste demasiados intentos. Espera unos minutos e intenta de nuevo.',
};
const MENSAJE_SIN_CONEXION = 'No pudimos conectarnos. Revisa tu conexión a internet e intenta de nuevo.';
const MENSAJE_INESPERADO = 'Ocurrió un error inesperado. Intenta de nuevo en unos minutos.';

type ManejadorSesionVencida = () => void;
let alVencerSesion: ManejadorSesionVencida = () => {
  const destino = encodeURIComponent(window.location.pathname + window.location.search);
  window.location.assign(`/ingresar?volver=${destino}`);
};

/**
 * Cambia lo que se hace ante un 401 (por defecto: ir a /ingresar y volver después a la misma página,
 * HU-04). La aplicación lo cambia para navegar con React Router; las pruebas, para no salir de la página.
 */
export function configurarSesionVencida(manejador: ManejadorSesionVencida) {
  alVencerSesion = manejador;
}

interface OpcionesPeticion extends Omit<RequestInit, 'body'> {
  /** Cuerpo que se envía como JSON. */
  cuerpo?: unknown;
  /** No redirige al login ante un 401 (por ejemplo, el propio formulario de inicio de sesión). */
  sinRedirigirEn401?: boolean;
}

export async function peticion<T>(ruta: string, { cuerpo, sinRedirigirEn401 = false, headers, ...opciones }: OpcionesPeticion = {}): Promise<T> {
  const encabezados = new Headers(headers);
  encabezados.set('Accept', 'application/json');
  // Un FormData (subir archivos) va tal cual: el navegador pone el Content-Type multipart con su separador.
  const esArchivo = cuerpo instanceof FormData;
  if (cuerpo !== undefined && !esArchivo) encabezados.set('Content-Type', 'application/json');

  let respuesta: Response;
  try {
    respuesta = await fetch(`${PREFIJO_API}${ruta}`, {
      ...opciones,
      headers: encabezados,
      credentials: 'include',
      body: cuerpo === undefined ? undefined : esArchivo ? cuerpo : JSON.stringify(cuerpo),
    });
  } catch {
    throw new ErrorApi(0, MENSAJE_SIN_CONEXION);
  }

  if (respuesta.ok) {
    if (respuesta.status === 204) return undefined as T;
    const texto = await respuesta.text();
    return (texto ? JSON.parse(texto) : undefined) as T;
  }

  const errores = await leerErrores(respuesta);
  if (respuesta.status === 401 && !sinRedirigirEn401) alVencerSesion();
  const mensaje = errores.find((e) => !e.campo)?.mensaje
    ?? (respuesta.status === 422 ? MENSAJES_POR_ESTADO[422] : errores[0]?.mensaje)
    ?? MENSAJES_POR_ESTADO[respuesta.status]
    ?? MENSAJE_INESPERADO;
  throw new ErrorApi(respuesta.status, mensaje, errores);
}

async function leerErrores(respuesta: Response): Promise<ErrorDeCampo[]> {
  try {
    const cuerpo: unknown = await respuesta.json();
    if (cuerpo && typeof cuerpo === 'object' && Array.isArray((cuerpo as { errores?: unknown }).errores)) {
      return (cuerpo as { errores: ErrorDeCampo[] }).errores.filter((e) => typeof e?.mensaje === 'string');
    }
  } catch {
    // Cuerpo vacío o que no es JSON (por ejemplo, un error del proxy): se usa el mensaje por estado.
  }
  return [];
}

export const api = {
  obtener: <T>(ruta: string, opciones?: OpcionesPeticion) => peticion<T>(ruta, { ...opciones, method: 'GET' }),
  crear: <T>(ruta: string, cuerpo?: unknown, opciones?: OpcionesPeticion) => peticion<T>(ruta, { ...opciones, method: 'POST', cuerpo }),
  reemplazar: <T>(ruta: string, cuerpo?: unknown, opciones?: OpcionesPeticion) => peticion<T>(ruta, { ...opciones, method: 'PUT', cuerpo }),
  modificar: <T>(ruta: string, cuerpo?: unknown, opciones?: OpcionesPeticion) => peticion<T>(ruta, { ...opciones, method: 'PATCH', cuerpo }),
  eliminar: <T>(ruta: string, opciones?: OpcionesPeticion) => peticion<T>(ruta, { ...opciones, method: 'DELETE' }),
};
