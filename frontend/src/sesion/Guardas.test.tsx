import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { api } from '../api/cliente';
import { ANDRES, LAURA, PATRICIA, abrirEn, simularApi } from '../pruebas/aplicacion';

describe('guardas de sesión (RNF-01)', () => {
  it('sin sesión, un panel lleva a iniciar sesión y recuerda la página pedida', async () => {
    simularApi(null);
    const enrutador = abrirEn('/cliente/citas');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/ingresar'));
    expect(enrutador.state.location.search).toBe('?volver=%2Fcliente%2Fcitas');
  });

  it('un cliente que escribe la dirección del administrador vuelve a su panel', async () => {
    simularApi(LAURA);
    const enrutador = abrirEn('/admin/usuarios');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/proyectos'));
  });

  it('el desarrollador (Product Owner) no entra al panel del cliente', async () => {
    simularApi(ANDRES);
    const enrutador = abrirEn('/cliente/proyectos');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/equipo/proyectos'));
  });

  it('con la contraseña temporal cualquier panel lleva a cambiarla (RF-05)', async () => {
    simularApi({ ...ANDRES, debeCambiarPassword: true });
    const enrutador = abrirEn('/equipo/proyectos');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cambiar-contrasena'));
  });

  it('con sesión, crear cuenta e iniciar sesión llevan a su panel', async () => {
    simularApi(PATRICIA);
    const enrutador = abrirEn('/ingresar');
    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/admin'));
  });

  it('el panel muestra el nombre del usuario', async () => {
    simularApi(LAURA);
    abrirEn('/cliente/proyectos');
    expect(await screen.findAllByText('Laura Gómez')).not.toHaveLength(0);
    expect(screen.getByText('laura.gomez@correo.com')).toBeInTheDocument();
  });

  it('cerrar sesión llama a la API y vuelve a iniciar sesión', async () => {
    const fetchSimulado = simularApi(PATRICIA);
    const enrutador = abrirEn('/admin');
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/ingresar'));
    expect(fetchSimulado).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({ method: 'POST' }));
  });

  it('si la sesión vence mientras se usa, lleva a iniciar sesión y vuelve después', async () => {
    simularApi(LAURA, { 'GET /citas': { estado: 401 } });
    const enrutador = abrirEn('/cliente/proyectos');
    await screen.findByRole('heading', { level: 1, name: 'Mis proyectos' });

    await act(async () => {
      await api.obtener('/citas').catch(() => undefined);
    });

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/ingresar'));
    expect(enrutador.state.location.search).toBe('?volver=%2Fcliente%2Fproyectos');
  });
});
