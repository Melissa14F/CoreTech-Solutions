import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { MATRIZ } from '../../pruebas/administracion';

function api(extra = {}) {
  return simularApi(PATRICIA, { 'GET /roles/permisos': { estado: 200, cuerpo: MATRIZ }, ...extra });
}

const resumen = (rol: string) => screen.getByText(rol, { selector: '.rol-resumen__nombre' }).closest('li') as HTMLElement;

describe('Configurar roles y permisos (HU-21)', () => {
  it('muestra la matriz con los permisos fijos con candado', async () => {
    api();
    abrirEn('/admin/roles');
    expect(await screen.findByRole('switch', { name: 'Cliente: Agendar citas' })).toBeChecked();
    expect(screen.getByRole('switch', { name: 'Desarrollador (Product Owner): Agendar citas' })).not.toBeChecked();
    // Los fijos no son interruptores: explican la regla
    expect(screen.queryByRole('switch', { name: 'Administrador: Registrar avances' })).not.toBeInTheDocument();
    expect(screen.getByText('No permitido. Fijo por RN-07 (Administrador: Registrar avances)')).toBeInTheDocument();
    expect(screen.getByText('Permitido. Fijo por RN-10 y RN-11 (Cliente: Ver sus proyectos y avances)')).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: /Agendar citas\s*RN-01 a RN-03/ })).toBeInTheDocument();
    expect(within(resumen('Cliente')).getByText('2')).toBeInTheDocument();
    expect(within(resumen('Cliente')).getByRole('link', { name: '9 usuarios' })).toHaveAttribute('href', '/admin/usuarios');
  });

  it('guarda los cambios de varios roles juntos y los aplica de inmediato', async () => {
    const guardada = {
      ...MATRIZ,
      permisos: MATRIZ.permisos.map((p) => (p.codigo === 'citas.agendar'
        ? { ...p, celdas: p.celdas.map((c) => (c.rol === 'PRODUCT_OWNER' ? { ...c, permitido: true } : c)) } : p)),
    };
    const fetchSimulado = api({ 'PUT /roles/permisos': { estado: 200, cuerpo: guardada } });
    abrirEn('/admin/roles');
    await userEvent.click(await screen.findByRole('switch', { name: 'Desarrollador (Product Owner): Agendar citas' }));

    expect(screen.getByText('1 cambio')).toBeInTheDocument();
    expect(within(resumen('Desarrollador (Product Owner)')).getByText('2')).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: /Agendar citas/ }).closest('tr')).toHaveClass('esta-cambiada');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('1 permiso actualizado. Aplican de inmediato.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/roles/permisos')).toEqual({
      cambios: [{ rol: 'PRODUCT_OWNER', permiso: 'citas.agendar', permitido: true }],
    });
    expect(screen.queryByRole('region', { name: 'Cambios sin guardar' })).not.toBeInTheDocument();
  });

  it('descarta los cambios y vuelve a como estaba', async () => {
    api();
    abrirEn('/admin/roles');
    const interruptor = await screen.findByRole('switch', { name: 'Cliente: Agendar citas' });
    await userEvent.click(interruptor);
    expect(interruptor).not.toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(interruptor).toBeChecked();
    expect(screen.getByText('Se descartaron los cambios.')).toBeInTheDocument();
  });

  it('si la API rechaza un permiso fijo muestra su mensaje', async () => {
    api({ 'PUT /roles/permisos': { estado: 422, cuerpo: errores({ campo: 'permisos', mensaje: 'Este permiso lo exige una regla del negocio' }) } });
    abrirEn('/admin/roles');
    await userEvent.click(await screen.findByRole('switch', { name: 'Cliente: Agendar citas' }));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    expect(await screen.findByText('Este permiso lo exige una regla del negocio')).toBeInTheDocument();
    // Los cambios siguen ahí para corregirlos
    expect(screen.getByText('1 cambio')).toBeInTheDocument();
  });
});
