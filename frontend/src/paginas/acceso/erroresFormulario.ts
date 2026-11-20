import { ErrorApi } from '../../api/cliente';

const MENSAJE_INESPERADO = 'Ocurrió un error inesperado. Intenta de nuevo en unos minutos.';

/**
 * Reparte el error de una petición entre los campos del formulario (422 o 409 con campo) y una alerta
 * general arriba del formulario (401, 403, 423, sin conexión…).
 */
export function separarErrores(error: unknown): { porCampo: Record<string, string>; general: string | null } {
  if (!(error instanceof ErrorApi)) return { porCampo: {}, general: MENSAJE_INESPERADO };
  const porCampo = error.porCampo;
  const general = error.generales[0] ?? (Object.keys(porCampo).length > 0 ? null : error.message);
  return { porCampo, general };
}
