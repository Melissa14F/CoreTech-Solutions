import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ANDRES, abrirEn, cuerpoEnviado, errores, simularApi, type RespuestaSimulada } from '../../pruebas/aplicacion';

const TEMPORAL = { ...ANDRES, debeCambiarPassword: true };

function apiConCambio(cambio: RespuestaSimulada = { estado: 200, cuerpo: {} }) {
  let cambiada = false;
  return simularApi(null, {
    'GET /auth/yo': () => ({ estado: 200, cuerpo: cambiada ? ANDRES : TEMPORAL }),
    'POST /auth/cambiar-password': () => {
      cambiada = cambio.estado === 200;
      return cambio;
    },
  });
}

async function cambiar(actual: string, nueva: string) {
  await userEvent.type(await screen.findByLabelText('Contraseña actual'), actual);
  await userEvent.type(screen.getByLabelText('Nueva contraseña'), nueva);
  await userEvent.type(screen.getByLabelText('Confirma la nueva contraseña'), nueva);
  await userEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
}

describe('Cambiar la contraseña temporal (HU-06, RF-05)', () => {
  it('sin sesión lleva a iniciar sesión', async () => {
    simularApi(null);
    const enrutador = abrirEn('/cambiar-contrasena');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/ingresar'));
  });

  it('saluda por su nombre y explica que es el primer ingreso', async () => {
    apiConCambio();
    abrirEn('/cambiar-contrasena');
    expect(await screen.findByText(/Hola, Andrés\. Es tu primer ingreso/)).toBeInTheDocument();
    expect(screen.getByText('Es la contraseña temporal del correo de bienvenida.')).toBeInTheDocument();
  });

  it('la nueva debe ser distinta de la temporal', async () => {
    const fetchSimulado = apiConCambio();
    abrirEn('/cambiar-contrasena');
    await cambiar('Temporal2026!', 'Temporal2026!');
    expect(screen.getByText('La nueva contraseña debe ser distinta de la temporal.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/cambiar-password')).toBeUndefined();
  });

  it('si la contraseña actual no es la temporal, lo dice bajo ese campo (422)', async () => {
    apiConCambio({ estado: 422, cuerpo: errores({ campo: 'passwordActual', mensaje: 'La contraseña actual no es correcta' }) });
    abrirEn('/cambiar-contrasena');
    await cambiar('NoEsLaTemporal1!', 'Nueva2026!');
    expect(await screen.findByText('La contraseña actual no es correcta')).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña actual')).toHaveAttribute('aria-invalid', 'true');
  });

  it('al cambiarla entra a su panel', async () => {
    const fetchSimulado = apiConCambio();
    const enrutador = abrirEn('/cambiar-contrasena');
    await cambiar('Temporal2026!', 'Nueva2026!');

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/equipo/proyectos'));
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/cambiar-password')).toEqual({
      passwordActual: 'Temporal2026!', passwordNueva: 'Nueva2026!', confirmacion: 'Nueva2026!',
    });
  });

  it('puede cerrar sesión si no es su cuenta', async () => {
    apiConCambio();
    const enrutador = abrirEn('/cambiar-contrasena');
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/ingresar'));
  });
});
