import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { AHORA_PRUEBA, CANCELADA, CON_EQUIPO, CONFIRMADA_PASADA, EQUIPOS, LISTA_ADMIN, SIN_EQUIPO } from '../../pruebas/gestionCitas';
import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';

function api(extra = {}) {
  return simularApi(PATRICIA, {
    'GET /citas/gestion': { estado: 200, cuerpo: LISTA_ADMIN },
    'GET /citas/equipos-disponibles': { estado: 200, cuerpo: EQUIPOS },
    ...extra,
  });
}

const fila = async (codigo: string) => (await screen.findByText(codigo)).closest('tr') as HTMLElement;

describe('Citas del administrador (HU-09)', () => {
  it('lista las citas con la hora de Colombia, el equipo y los números de cada filtro', async () => {
    api();
    abrirEn('/admin/citas');
    const sinEquipo = await fila('C-0061');

    expect(within(sinEquipo).getByText('Sin asignar')).toBeInTheDocument();
    expect(within(sinEquipo).getByText('Pendiente')).toBeInTheDocument();
    expect(within(sinEquipo).getByRole('button', { name: 'Asignar' })).toBeInTheDocument();
    expect(within(await fila('C-0060')).getByText('Equipo de Andrés Pérez')).toBeInTheDocument();
    expect(within(await fila('C-0060')).getByRole('button', { name: 'Reasignar' })).toBeInTheDocument();
    // Un cliente fuera de Colombia: su país y su hora, junto a la de Colombia (RN-23)
    expect(within(await fila('C-0062')).getByText('México · para el cliente son las 8:00 a. m.')).toBeInTheDocument();
    expect(within(await fila('C-0062')).getByText('Jue 13 mar · 9:00 a. m.')).toBeInTheDocument();

    expect(screen.getByRole('button', { name: /Todas/ })).toHaveTextContent('6');
    expect(screen.getByRole('button', { name: /Sin asignar/ })).toHaveTextContent('1');
    expect(screen.getByRole('button', { name: /Pendientes/ })).toHaveTextContent('3');
    // Atendida: crear proyecto y ver el cuestionario; confirmada: sin botón de asignar si ya pasó no aplica
    expect(within(await fila('C-0052')).getByRole('link', { name: 'Crear proyecto' })).toHaveAttribute('href', '/admin/proyectos/nuevo?cita=C-0052');
    expect(within(await fila('C-0052')).queryByRole('button', { name: 'Asignar' })).not.toBeInTheDocument();
    expect(within(await fila('C-0055')).getByText('Motivo: El cliente pidió cambiar la fecha.')).toBeInTheDocument();
  });

  it('pide al servidor las citas del filtro elegido y busca por cliente o código', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/citas');
    await fila('C-0061');

    await userEvent.click(screen.getByRole('button', { name: /Sin asignar/ }));
    await waitFor(() => expect(fetchSimulado.mock.calls.some(([url]) => String(url).includes('/citas/gestion?sinEquipo=true'))).toBe(true));
    await userEvent.click(screen.getByRole('button', { name: /Confirmadas/ }));
    await waitFor(() => expect(fetchSimulado.mock.calls.some(([url]) => String(url).includes('estado=CONFIRMADA'))).toBe(true));

    await userEvent.click(screen.getByRole('button', { name: /Todas/ }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar cita' }), 'villamizar');
    // La búsqueda se pide al servidor cuando se deja de escribir
    await waitFor(() => expect(fetchSimulado.mock.calls.some(([url]) => String(url).includes('q=villamizar'))).toBe(true));
  });

  it('cuando hay más de una página, pide la siguiente al servidor', async () => {
    const fetchSimulado = api({
      'GET /citas/gestion': { estado: 200, cuerpo: { ...LISTA_ADMIN, totalFiltradas: 45, totalPaginas: 3 } },
    });
    abrirEn('/admin/citas');
    await fila('C-0061');
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Siguiente/ }));
    await waitFor(() => expect(fetchSimulado.mock.calls.some(([url]) => String(url).includes('pagina=1'))).toBe(true));
  });

  it('dice quién canceló y deja recordar al Product Owner de una cita que ya pasó sin cuestionario', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(AHORA_PRUEBA);
    try {
      api({ 'GET /citas/gestion': { estado: 200, cuerpo: { ...LISTA_ADMIN, citas: [CONFIRMADA_PASADA, CANCELADA] } } });
      abrirEn('/admin/citas');
      expect(within(await fila('C-0055')).getByText('Canceló el cliente')).toBeInTheDocument();
      const recordar = within(await fila('C-0058')).getByRole('link', { name: 'Recordar a Andrés Pérez que llene el cuestionario de C-0058' });
      expect(recordar.getAttribute('href')).toContain('mailto:andres.perez@coretechsolutions.co');
    } finally {
      vi.useRealTimers();
    }
  });

  it('asigna el equipo elegido, con su carga a la vista, y avisa que le enviaron un correo', async () => {
    const fetchSimulado = api({
      'PATCH /citas/c-61/asignar': { estado: 200, cuerpo: { ...SIN_EQUIPO, equipo: { equipoId: 'e-maria', productOwner: 'María Ruiz', integrantes: 4, proyectosActivos: 1, maxProyectos: 3 } } },
    });
    abrirEn('/admin/citas');
    await userEvent.click(within(await fila('C-0061')).getByRole('button', { name: 'Asignar' }));

    const dialogo = await screen.findByRole('dialog', { name: 'Asignar equipo' });
    expect(within(dialogo).getByText('Equipo de 6 · 3 de 3 proyectos · 4 citas próximas')).toBeInTheDocument();
    const maria = within(dialogo).getByRole('radio', { name: /María Ruiz/ });
    // De menor a mayor carga: María primero
    expect(within(dialogo).getAllByRole('radio')[0]).toBe(maria);
    await userEvent.click(maria);
    await userEvent.type(within(dialogo).getByLabelText(/Nota para el Product Owner/), 'Revisa su tienda antes.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar asignación' }));

    expect(await screen.findByText('C-0061 asignada al equipo de María Ruiz. Le enviamos un correo.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/citas/c-61/asignar')).toEqual({ equipoId: 'e-maria', nota: 'Revisa su tienda antes.' });
  });

  it('sin elegir equipo marca el campo y no envía nada', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/citas');
    await userEvent.click(within(await fila('C-0061')).getByRole('button', { name: 'Asignar' }));
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Asignar equipo' })).getByRole('button', { name: 'Guardar asignación' }));

    expect(screen.getByText('Elige quién atenderá la cita.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'PATCH')).toBe(false);
  });

  it('una cita que ya se atendió: muestra el mensaje de la API en el diálogo', async () => {
    api({ 'PATCH /citas/c-60/asignar': { estado: 409, cuerpo: errores({ mensaje: 'Esta cita ya no se puede modificar' }) } });
    abrirEn('/admin/citas');
    await userEvent.click(within(await fila('C-0060')).getByRole('button', { name: 'Reasignar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Reasignar equipo' });
    expect(within(dialogo).getByRole('radio', { name: /Andrés Pérez/ })).toBeChecked();
    await userEvent.click(within(dialogo).getByRole('radio', { name: /María Ruiz/ }));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar asignación' }));

    expect(await within(dialogo).findByText('Esta cita ya no se puede modificar')).toBeInTheDocument();
  });

  it('cancela con motivo de 15 caracteres o más y avisa al cliente', async () => {
    const fetchSimulado = api({
      'PATCH /citas/c-60/cancelar-admin': { estado: 200, cuerpo: { ...CON_EQUIPO, estado: 'CANCELADA', motivoCancelacion: 'El cliente pidió cambiar la fecha por teléfono.' } },
    });
    abrirEn('/admin/citas');
    await userEvent.click(within(await fila('C-0060')).getByRole('button', { name: 'Cancelar la cita C-0060' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Cancelar cita' });

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar la cita' }));
    expect(within(dialogo).getByText('Escribe el motivo: quedará en la auditoría.')).toBeInTheDocument();
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'muy corto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar la cita' }));
    expect(within(dialogo).getByText('Escribe al menos 15 caracteres.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'PATCH')).toBe(false);

    await userEvent.clear(within(dialogo).getByLabelText('Motivo'));
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'El cliente pidió cambiar la fecha por teléfono.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar la cita' }));

    expect(await screen.findByText('Cita C-0060 cancelada. Avisamos al cliente.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/citas/c-60/cancelar-admin')).toEqual({ motivo: 'El cliente pidió cambiar la fecha por teléfono.' });
  });

  it('muestra el cuestionario de una cita atendida', async () => {
    api({
      'GET /citas/c-52/cuestionario': {
        estado: 200,
        cuerpo: {
          cuestionarioId: 'q-1', citaId: 'c-52', resumen: 'Laura quiere una app para que sus clientes reserven turnos.',
          descripcionCliente: 'Dueña de un salón de belleza con tres sedes.', presupuesto: 'DE_5_A_15M', fechaDeseada: null,
          idioma: 'ESPANOL', urgencia: 'ALTA', registradoAt: '2031-02-25T16:00:00Z',
        },
      },
    });
    abrirEn('/admin/citas');
    await userEvent.click(within(await fila('C-0052')).getByRole('button', { name: 'Ver el cuestionario de la cita C-0052' }));

    const dialogo = await screen.findByRole('dialog', { name: 'Cuestionario de la cita' });
    expect(await within(dialogo).findByText('Entre $5 y $15 millones')).toBeInTheDocument();
    expect(within(dialogo).getByText('Sin fecha definida')).toBeInTheDocument();
    expect(within(dialogo).getByText('Alta')).toBeInTheDocument();
  });

  it('si la lista no carga, explica y deja reintentar', async () => {
    simularApi(PATRICIA, { 'GET /citas/gestion': { estado: 500, cuerpo: errores({ mensaje: 'Algo salió mal' }) } });
    abrirEn('/admin/citas');
    expect(await screen.findByText('No pudimos cargar las citas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
