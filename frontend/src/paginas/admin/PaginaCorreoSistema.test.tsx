import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { navegador, type EstadoCorreo } from '../../administracion/correoSistema';
import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';

const SIN_CONECTAR: EstadoCorreo = {
  configurado: true, conectado: false, cuenta: null, estado: null, conectadoEn: null, ultimoError: null, ultimoErrorEn: null,
};
const CONECTADO: EstadoCorreo = {
  ...SIN_CONECTAR, conectado: true, cuenta: 'helix.jmdev2026@gmail.com', estado: 'CONECTADO',
  conectadoEn: new Date().toISOString(),
};

describe('correo del sistema (D-51)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sin conectar: «Conectar con Gmail» lleva a la pantalla de Google', async () => {
    const ir = vi.spyOn(navegador, 'ir').mockImplementation(() => undefined);
    simularApi(PATRICIA, {
      'GET /correo-sistema': { estado: 200, cuerpo: SIN_CONECTAR },
      'POST /correo-sistema/gmail/conexion': { estado: 200, cuerpo: { url: 'https://accounts.google.com/o/oauth2/v2/auth?x=1' } },
    });
    abrirEn('/admin/correo');

    expect(await screen.findByText('Sin conectar')).toBeInTheDocument();
    expect(screen.getByText(/no ve ni guarda tu\s+contraseña/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Conectar con Gmail' }));

    await waitFor(() => expect(ir).toHaveBeenCalledWith('https://accounts.google.com/o/oauth2/v2/auth?x=1'));
  });

  it('conectado: muestra la cuenta y envía el correo de prueba', async () => {
    const fetchSimulado = simularApi(PATRICIA, {
      'GET /correo-sistema': { estado: 200, cuerpo: CONECTADO },
      'POST /correo-sistema/prueba': { estado: 200, cuerpo: { canal: 'GMAIL', mensaje: 'Correo enviado. Revisa la bandeja de entrada de helix.jmdev2026@gmail.com.' } },
    });
    abrirEn('/admin/correo');

    expect(await screen.findByText('Conectado')).toBeInTheDocument();
    expect(screen.getByText('helix.jmdev2026@gmail.com')).toBeInTheDocument();
    expect(screen.getByLabelText('Enviar a')).toHaveValue('helix.jmdev2026@gmail.com');

    await userEvent.click(screen.getByRole('button', { name: 'Enviar correo de prueba' }));

    expect(await screen.findByText(/Correo enviado. Revisa la bandeja de entrada/)).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/correo-sistema/prueba')).toEqual({ para: 'helix.jmdev2026@gmail.com' });
  });

  it('un correo mal escrito muestra el error bajo el campo', async () => {
    simularApi(PATRICIA, {
      'GET /correo-sistema': { estado: 200, cuerpo: CONECTADO },
      'POST /correo-sistema/prueba': { estado: 422, cuerpo: errores({ campo: 'para', mensaje: 'Escribe un correo válido, por ejemplo nombre@empresa.com.' }) },
    });
    abrirEn('/admin/correo');
    const campo = await screen.findByLabelText('Enviar a');
    await userEvent.clear(campo);
    await userEvent.type(campo, 'no-es-correo');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar correo de prueba' }));

    expect(await screen.findByText('Escribe un correo válido, por ejemplo nombre@empresa.com.')).toBeInTheDocument();
    expect(campo).toHaveAttribute('aria-invalid', 'true');
  });

  it('desconectar pide confirmación', async () => {
    const fetchSimulado = simularApi(PATRICIA, {
      'GET /correo-sistema': { estado: 200, cuerpo: CONECTADO },
      'DELETE /correo-sistema/gmail': { estado: 204 },
    });
    abrirEn('/admin/correo');
    await userEvent.click(await screen.findByRole('button', { name: 'Desconectar' }));

    const dialogo = screen.getByRole('dialog');
    expect(within(dialogo).getByText(/deja de enviar correos desde helix.jmdev2026@gmail.com/)).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desconectar' }));

    await waitFor(() => expect(fetchSimulado.mock.calls.some(([url, op]) => url === '/api/v1/correo-sistema/gmail' && op?.method === 'DELETE')).toBe(true));
  });

  it('permiso retirado por Google: pide volver a conectar', async () => {
    simularApi(PATRICIA, {
      'GET /correo-sistema': { estado: 200, cuerpo: { ...CONECTADO, conectado: false, estado: 'REVOCADO', ultimoError: 'Google retiró el permiso' } },
    });
    abrirEn('/admin/correo');

    expect(await screen.findByText('Hay que reconectar')).toBeInTheDocument();
    expect(screen.getByText(/Google retiró el permiso de helix.jmdev2026@gmail.com/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver a conectar' })).toBeInTheDocument();
  });

  it('al volver de Google muestra el resultado', async () => {
    simularApi(PATRICIA, { 'GET /correo-sistema': { estado: 200, cuerpo: CONECTADO } });
    abrirEn('/admin/correo?resultado=conectado');
    expect(await screen.findByText(/Gmail quedó conectado/)).toBeInTheDocument();
  });

  it('sin el cliente de Google configurado lo explica', async () => {
    simularApi(PATRICIA, { 'GET /correo-sistema': { estado: 200, cuerpo: { ...SIN_CONECTAR, configurado: false } } });
    abrirEn('/admin/correo');
    expect(await screen.findByText('Falta configurar el cliente de Google en el servidor')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Conectar con Gmail' })).not.toBeInTheDocument();
  });
});
