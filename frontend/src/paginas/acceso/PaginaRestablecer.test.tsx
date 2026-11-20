import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';

async function guardar(clave: string) {
  await userEvent.type(await screen.findByLabelText('Nueva contraseña'), clave);
  await userEvent.type(screen.getByLabelText('Confirma la nueva contraseña'), clave);
  await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
}

describe('Restablecer la contraseña (HU-06, D-37)', () => {
  it('sin token dice que el enlace no es válido y ofrece pedir otro', async () => {
    simularApi(null);
    abrirEn('/restablecer');
    expect(await screen.findByText('Este enlace ya no es válido, solicita uno nuevo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Pedir un enlace nuevo' })).toHaveAttribute('href', '/ingresar');
  });

  it('guarda la contraseña nueva con el token del enlace', async () => {
    const fetchSimulado = simularApi(null, {
      'POST /auth/restablecer': { estado: 200, cuerpo: { mensaje: 'Listo, tu contraseña quedó cambiada. Ya puedes iniciar sesión.' } },
    });
    abrirEn('/restablecer?token=abc123');
    await guardar('Nueva2026!');

    expect(await screen.findByText('Listo, tu contraseña quedó cambiada. Ya puedes iniciar sesión.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/ingresar');
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/restablecer')).toEqual({
      token: 'abc123', passwordNueva: 'Nueva2026!', confirmacion: 'Nueva2026!',
    });
  });

  it('con un enlace vencido o ya usado (422 de token) dice que pida otro', async () => {
    simularApi(null, {
      'POST /auth/restablecer': { estado: 422, cuerpo: errores({ campo: 'token', mensaje: 'Este enlace ya no es válido, solicita uno nuevo' }) },
    });
    abrirEn('/restablecer?token=vencido');
    await guardar('Nueva2026!');
    expect(await screen.findByRole('link', { name: 'Pedir un enlace nuevo' })).toBeInTheDocument();
  });

  it('no envía una contraseña que no cumple los requisitos', async () => {
    const fetchSimulado = simularApi(null);
    abrirEn('/restablecer?token=abc123');
    await guardar('corta');
    expect(screen.getByText('La contraseña aún no cumple todos los requisitos.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/restablecer')).toBeUndefined();
  });
});
