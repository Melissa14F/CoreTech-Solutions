import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

import { ANDRES, LAURA, abrirEn, cuerpoEnviado, errores, simularApi, type RespuestaSimulada } from '../../pruebas/aplicacion';
import type { Usuario } from '../../sesion/ContextoSesion';

/** /auth/yo responde 401 hasta que el login sale bien; después, el usuario. */
function apiConLogin(usuario: Usuario, login: RespuestaSimulada = { estado: 200, cuerpo: {} }) {
  let conectado = false;
  return simularApi(null, {
    'GET /auth/yo': () => (conectado ? { estado: 200, cuerpo: usuario } : { estado: 401 }),
    'POST /auth/login': () => {
      conectado = login.estado === 200;
      return login;
    },
  });
}

async function ingresar(email: string, clave: string) {
  await userEvent.type(await screen.findByLabelText('Correo electrónico'), email);
  await userEvent.type(screen.getByLabelText('Contraseña'), clave);
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
}

describe('Iniciar sesión (HU-06)', () => {
  it('sin datos marca los campos y no llama a la API', async () => {
    const fetchSimulado = simularApi(null);
    abrirEn('/ingresar');
    await userEvent.click(await screen.findByRole('button', { name: 'Ingresar' }));

    expect(screen.getByText('Escribe el correo con el que creaste tu cuenta.')).toBeInTheDocument();
    expect(screen.getByText('Escribe tu contraseña.')).toBeInTheDocument();
    expect(screen.getByLabelText('Correo electrónico')).toHaveFocus();
    expect(fetchSimulado).not.toHaveBeenCalledWith('/api/v1/auth/login', expect.anything());
  });

  it('con credenciales incorrectas muestra el mensaje sin decir cuál falló', async () => {
    apiConLogin(LAURA, { estado: 401, cuerpo: errores({ mensaje: 'Correo o contraseña incorrectos' }) });
    const enrutador = abrirEn('/ingresar');
    await ingresar('laura.gomez@correo.com', 'Equivocada1!');

    expect(await screen.findByText('Correo o contraseña incorrectos')).toBeInTheDocument();
    expect(enrutador.state.location.pathname).toBe('/ingresar');
  });

  it('con la cuenta bloqueada muestra el aviso de 15 minutos (423)', async () => {
    apiConLogin(LAURA, { estado: 423, cuerpo: errores({ mensaje: 'Cuenta bloqueada 15 minutos por seguridad' }) });
    abrirEn('/ingresar');
    await ingresar('laura.gomez@correo.com', 'Equivocada1!');
    expect(await screen.findByText('Cuenta bloqueada 15 minutos por seguridad')).toBeInTheDocument();
  });

  it('lleva a cada rol a su panel y envía "Recordarme"', async () => {
    const fetchSimulado = apiConLogin(LAURA);
    const enrutador = abrirEn('/ingresar');
    await userEvent.click(await screen.findByLabelText('Recordarme'));
    await ingresar('laura.gomez@correo.com', 'Demo2026!');

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/proyectos'));
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/login')).toEqual({
      email: 'laura.gomez@correo.com', password: 'Demo2026!', recordarme: true,
    });
  });

  it('vuelve a la página que pedía antes de iniciar sesión', async () => {
    apiConLogin(LAURA);
    const enrutador = abrirEn('/ingresar?volver=%2Fcliente%2Fcitas');
    await ingresar('laura.gomez@correo.com', 'Demo2026!');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/citas'));
  });

  it('no sigue un "volver" hacia otro sitio', async () => {
    apiConLogin(LAURA);
    const enrutador = abrirEn('/ingresar?volver=%2F%2Fotro-sitio.com');
    await ingresar('laura.gomez@correo.com', 'Demo2026!');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/proyectos'));
  });

  it('con la contraseña temporal lleva a cambiarla', async () => {
    apiConLogin({ ...ANDRES, debeCambiarPassword: true });
    const enrutador = abrirEn('/ingresar');
    await ingresar('andres.perez@coretechsolutions.co', 'Temporal2026!');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cambiar-contrasena'));
  });

  it('"¿Olvidaste tu contraseña?" pide el enlace por correo', async () => {
    const fetchSimulado = simularApi(null, {
      'POST /auth/recuperacion': { estado: 202, cuerpo: { mensaje: 'Si el correo existe, te enviamos un enlace' } },
    });
    abrirEn('/ingresar');
    await userEvent.click(await screen.findByText('¿Olvidaste tu contraseña?'));
    await userEvent.type(screen.getByLabelText('Correo de tu cuenta'), 'laura.gomez@correo.com');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }));

    expect(await screen.findByText(/Si el correo existe, te enviamos un enlace/)).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/recuperacion')).toEqual({ email: 'laura.gomez@correo.com' });
  });

  it('muestra las cuentas de demostración solo en desarrollo', async () => {
    simularApi(null);
    abrirEn('/ingresar');
    expect(await screen.findByRole('button', { name: /Laura Gómez/ })).toBeInTheDocument();
  });

  it('fuera de desarrollo no muestra las cuentas de demostración', async () => {
    vi.stubEnv('DEV', false);
    simularApi(null);
    abrirEn('/ingresar');
    await screen.findByRole('heading', { level: 1, name: 'Inicia sesión' });
    expect(screen.queryByRole('button', { name: /Laura Gómez/ })).not.toBeInTheDocument();
  });
});
