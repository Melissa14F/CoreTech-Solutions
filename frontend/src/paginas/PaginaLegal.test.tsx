import { screen, within } from '@testing-library/react';

import { abrirEn, simularApi } from '../pruebas/aplicacion';

describe('avisos legales: la política que se acepta al registrarse (D-53)', () => {
  it('muestra la política con los correos del servicio y quién los envía', async () => {
    simularApi(null, { 'GET /contenido': { estado: 200, cuerpo: { textos: { 'contacto-correo': 'hola@coretech.co' } } } });
    abrirEn('/legal#datos');

    const politica = await screen.findByRole('region', { name: 'Política de tratamiento de datos personales' });
    expect(within(politica).getByRole('heading', { name: 'Correos que te enviamos' })).toBeInTheDocument();
    expect(within(politica).getByText(/te escribamos al correo electrónico con el que creaste tu cuenta/)).toBeInTheDocument();
    expect(within(politica).getByText(/No usamos tu correo para publicidad/)).toBeInTheDocument();
    expect(within(politica).getByText(/usamos Gmail, de Google, que actúa como encargado del tratamiento/)).toBeInTheDocument();
    expect(await within(politica).findByRole('link', { name: 'hola@coretech.co' })).toHaveAttribute('href', 'mailto:hola@coretech.co');
    expect(screen.getByRole('region', { name: 'Términos y condiciones' })).toBeInTheDocument();
    expect(document.title).toContain('Avisos legales');
  });

  it('la casilla del registro lleva a esta política, en otra pestaña para no perder lo escrito', async () => {
    simularApi(null);
    abrirEn('/registro');
    const enlace = await screen.findByRole('link', { name: 'política de tratamiento de datos personales' });
    expect(enlace).toHaveAttribute('href', '/legal#datos');
    expect(enlace).toHaveAttribute('target', '_blank');
  });
});
