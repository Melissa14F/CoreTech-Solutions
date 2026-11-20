import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ErrorApi, api, configurarSesionVencida } from './cliente';

function responder(estado: number, cuerpo?: unknown) {
  return vi.fn().mockResolvedValue(
    new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
      status: estado,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
}

describe('cliente de la API', () => {
  const sesionVencida = vi.fn();

  beforeEach(() => configurarSesionVencida(sesionVencida));
  afterEach(() => {
    vi.unstubAllGlobals();
    sesionVencida.mockReset();
  });

  it('usa el prefijo /api/v1, envía JSON y la cookie de sesión', async () => {
    const fetchSimulado = responder(201, { citaId: 'a1', codigo: 'C-0064' });
    vi.stubGlobal('fetch', fetchSimulado);

    const cita = await api.crear<{ codigo: string }>('/citas', { modalidad: 'VIRTUAL' });

    expect(cita.codigo).toBe('C-0064');
    const [url, opciones] = fetchSimulado.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/citas');
    expect(opciones.method).toBe('POST');
    expect(opciones.credentials).toBe('include');
    expect(opciones.body).toBe('{"modalidad":"VIRTUAL"}');
    expect(new Headers(opciones.headers).get('Content-Type')).toBe('application/json');
  });

  it('convierte un 422 en errores por campo', async () => {
    vi.stubGlobal('fetch', responder(422, {
      errores: [
        { campo: 'descripcionNecesidad', mensaje: 'Cuéntanos un poco más (mínimo 20 caracteres)' },
        { campo: 'fechaHora', mensaje: 'Las citas se agendan de lunes a viernes, con mínimo 24 horas y máximo 30 días de anticipación' },
      ],
    }));

    const error = await api.crear('/citas', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ErrorApi);
    const errorApi = error as ErrorApi;
    expect(errorApi.estado).toBe(422);
    expect(errorApi.porCampo).toEqual({
      descripcionNecesidad: 'Cuéntanos un poco más (mínimo 20 caracteres)',
      fechaHora: 'Las citas se agendan de lunes a viernes, con mínimo 24 horas y máximo 30 días de anticipación',
    });
    expect(errorApi.message).toBe('Revisa los datos marcados.');
  });

  it('un 409 trae el mensaje del servidor', async () => {
    vi.stubGlobal('fetch', responder(409, { errores: [{ mensaje: 'La hora que elegiste ya no está disponible, elige otra' }] }));

    await expect(api.crear('/citas', {})).rejects.toMatchObject({
      estado: 409,
      message: 'La hora que elegiste ya no está disponible, elige otra',
    });
  });

  it('un 401 lleva a iniciar sesión', async () => {
    vi.stubGlobal('fetch', responder(401, { errores: [{ mensaje: 'Tu sesión venció, ingresa de nuevo' }] }));

    await expect(api.obtener('/proyectos')).rejects.toMatchObject({ estado: 401 });
    expect(sesionVencida).toHaveBeenCalledOnce();
  });

  it('un 401 del formulario de inicio de sesión no redirige', async () => {
    vi.stubGlobal('fetch', responder(401, { errores: [{ mensaje: 'Correo o contraseña incorrectos' }] }));

    await expect(api.crear('/auth/login', {}, { sinRedirigirEn401: true }))
      .rejects.toMatchObject({ message: 'Correo o contraseña incorrectos' });
    expect(sesionVencida).not.toHaveBeenCalled();
  });

  it('sin cuerpo de error usa un mensaje claro según el código', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 })));

    await expect(api.obtener('/servicios')).rejects.toMatchObject({
      estado: 502,
      message: 'Ocurrió un error inesperado. Intenta de nuevo en unos minutos.',
    });
  });

  it('sin conexión avisa al usuario', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(api.obtener('/servicios')).rejects.toMatchObject({
      estado: 0,
      message: 'No pudimos conectarnos. Revisa tu conexión a internet e intenta de nuevo.',
    });
  });

  it('una respuesta 204 no tiene cuerpo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(api.eliminar('/dias-no-habiles/2026-12-08')).resolves.toBeUndefined();
  });
});
