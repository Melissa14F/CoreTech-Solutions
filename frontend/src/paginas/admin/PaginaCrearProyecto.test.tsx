import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { CITA_CON_PROYECTO, CITA_SIN_PROYECTO, CUESTIONARIO, EQUIPOS, HOY_PRUEBA, LISTA, PLANTILLA } from '../../pruebas/proyectosAdmin';

function api(extra = {}) {
  return simularApi(PATRICIA, {
    'GET /proyectos/citas-atendidas': { estado: 200, cuerpo: [CITA_SIN_PROYECTO, CITA_CON_PROYECTO] },
    'GET /proyectos/equipos': { estado: 200, cuerpo: EQUIPOS },
    'GET /citas/c-57/cuestionario': { estado: 200, cuerpo: CUESTIONARIO },
    'GET /servicios/s-web/plantilla': { estado: 200, cuerpo: PLANTILLA },
    'GET /proyectos': { estado: 200, cuerpo: LISTA },
    ...extra,
  });
}

async function elegirLaCita() {
  await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Cita atendida' }), 'c-57');
}

describe('Crear proyecto (HU-02)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(HOY_PRUEBA);
  });
  afterEach(() => vi.useRealTimers());

  it('separa las citas sin proyecto de las que ya lo generaron, que no se pueden elegir (RN-04)', async () => {
    api();
    abrirEn('/admin/proyectos/nuevo');
    const lista = await screen.findByRole('combobox', { name: 'Cita atendida' });

    expect(within(lista).getByRole('option', { name: 'C-0057 · Miguel Ángel Duarte · Página web corporativa' })).toBeEnabled();
    expect(within(lista).getByRole('option', { name: 'C-0049 · Laura Gómez · ya es P-0035' })).toBeDisabled();
  });

  it('al elegir la cita muestra sus datos, el cuestionario y la plantilla que se copiará', async () => {
    api();
    abrirEn('/admin/proyectos/nuevo');
    await elegirLaCita();

    expect(await screen.findByText('Camila Jaimes')).toBeInTheDocument();
    expect(await screen.findByText(/Miguel quiere renovar la web/)).toBeInTheDocument();
    expect(screen.getByText('Entre $5 y $15 millones')).toBeInTheDocument();
    expect(await screen.findByText('Se copiarán 3 fases como plan en borrador; el equipo las ajusta antes de enviarlas al cliente.')).toBeInTheDocument();
    expect(screen.getByText('Requerimientos y contenido · la aprueba el cliente')).toBeInTheDocument();
  });

  it('llegar desde Citas con ?cita= deja la cita elegida', async () => {
    api();
    abrirEn('/admin/proyectos/nuevo?cita=C-0057');
    expect(await screen.findByText(/Miguel quiere renovar la web/)).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Cita atendida' })).toHaveValue('c-57');
  });

  it('los equipos sin cupo aparecen deshabilitados (RN-05)', async () => {
    api();
    abrirEn('/admin/proyectos/nuevo');
    expect(await screen.findByRole('radio', { name: /Camila Jaimes/ })).toBeEnabled();
    expect(screen.getByRole('radio', { name: /Andrés Pérez/ })).toBeDisabled();
    expect(screen.getByText('Product Owner: Andrés Pérez · Sin cupo: ya tiene 3 proyectos activos')).toBeInTheDocument();
  });

  it('sin llenar nada marca los campos y no envía', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/proyectos/nuevo');
    await screen.findByRole('combobox', { name: 'Cita atendida' });
    await userEvent.click(screen.getByRole('button', { name: 'Crear proyecto' }));

    expect(screen.getByText('Elige la cita de la que nace el proyecto.')).toBeInTheDocument();
    expect(screen.getByText('Escribe el nombre que verá el cliente.')).toBeInTheDocument();
    expect(screen.getByText('Describe lo que se acordó en la cita.')).toBeInTheDocument();
    expect(screen.getByText('Elige la fecha estimada de entrega.')).toBeInTheDocument();
    expect(screen.getByText('Elige el equipo responsable.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('crea el proyecto con los datos del formulario y vuelve a la lista con su código', async () => {
    const fetchSimulado = api({ 'POST /proyectos': { estado: 201, cuerpo: { proyectoId: 'p-36', codigo: 'P-0036', estadoPlan: 'BORRADOR', fases: 3 } } });
    abrirEn('/admin/proyectos/nuevo');
    await elegirLaCita();
    await screen.findByText(/Se copiarán 3 fases/);
    await userEvent.type(screen.getByLabelText('Nombre del proyecto'), 'Web corporativa Duarte');
    await userEvent.type(screen.getByLabelText('Alcance acordado'), 'Sitio de 5 páginas con formulario de contacto y WhatsApp.');
    await userEvent.click(screen.getByRole('radio', { name: /Camila Jaimes/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Crear proyecto' }));

    expect(await screen.findByText(/Proyecto P-0036 creado con su plan en borrador/)).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/proyectos')).toEqual({
      citaId: 'c-57', nombre: 'Web corporativa Duarte', alcance: 'Sitio de 5 páginas con formulario de contacto y WhatsApp.',
      fechaInicio: '2026-11-11', fechaEstimadaEntrega: '2026-12-09', equipoId: 'e-camila',
    });
    expect(await screen.findByRole('heading', { name: 'Proyectos' })).toBeInTheDocument();
  });

  it('una entrega a menos de 2 semanas del inicio se marca sin enviar', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/proyectos/nuevo');
    await elegirLaCita();
    await userEvent.type(screen.getByLabelText('Nombre del proyecto'), 'Web corporativa Duarte');
    await userEvent.type(screen.getByLabelText('Alcance acordado'), 'Sitio de 5 páginas con formulario de contacto y WhatsApp.');
    await userEvent.click(await screen.findByRole('radio', { name: /Camila Jaimes/ }));
    const entrega = screen.getByLabelText('Entrega estimada inicial');
    await userEvent.clear(entrega);
    await userEvent.type(entrega, '2026-11-20');
    await userEvent.click(screen.getByRole('button', { name: 'Crear proyecto' }));

    expect(screen.getByText('Debe ser al menos 2 semanas después del inicio.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('el 409 de un equipo que se llenó se muestra bajo el equipo', async () => {
    api({ 'POST /proyectos': { estado: 409, cuerpo: errores({ campo: 'equipoId', mensaje: 'El equipo ya tiene 3 proyectos activos, elige otro' }) } });
    abrirEn('/admin/proyectos/nuevo');
    await elegirLaCita();
    await userEvent.type(screen.getByLabelText('Nombre del proyecto'), 'Web corporativa Duarte');
    await userEvent.type(screen.getByLabelText('Alcance acordado'), 'Sitio de 5 páginas con formulario de contacto y WhatsApp.');
    await userEvent.click(await screen.findByRole('radio', { name: /Camila Jaimes/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Crear proyecto' }));

    expect(await screen.findByText('El equipo ya tiene 3 proyectos activos, elige otro')).toBeInTheDocument();
  });

  it('si el servicio no tiene una plantilla válida avisa y no deja crear', async () => {
    api({ 'GET /servicios/s-web/plantilla': { estado: 200, cuerpo: { fases: [] } } });
    abrirEn('/admin/proyectos/nuevo');
    await elegirLaCita();

    expect(await screen.findByText('Completa la plantilla de fases de este servicio antes de crear el proyecto')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear proyecto' })).toBeDisabled();
  });
});
