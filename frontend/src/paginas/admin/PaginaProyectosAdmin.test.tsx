import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import {
  BORRADOR, EN_CURSO, EN_GARANTIA, EQUIPOS, HOY_PRUEBA, LISTA, PAUSADO_RECHAZADO, PLAN_ENVIADO, SIN_AVANCES, VENCIDO,
} from '../../pruebas/proyectosAdmin';

function api(extra = {}) {
  return simularApi(PATRICIA, {
    'GET /proyectos': { estado: 200, cuerpo: LISTA },
    'GET /proyectos/equipos': { estado: 200, cuerpo: EQUIPOS },
    ...extra,
  });
}

const fila = async (codigo: string) => (await screen.findByText(codigo)).closest('tr') as HTMLElement;

describe('Proyectos del administrador (HU-17)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(HOY_PRUEBA);
  });
  afterEach(() => vi.useRealTimers());

  it('lista los proyectos con el avance, la entrega, el estado y los números de cada filtro', async () => {
    api();
    abrirEn('/admin/proyectos');
    const vencido = await fila(VENCIDO.codigo);

    expect(within(vencido).getByText('María Ruiz')).toBeInTheDocument();
    expect(within(vencido).getByText('60 %')).toBeInTheDocument();
    expect(within(vencido).getByText('Fase 4/5 · Pruebas')).toBeInTheDocument();
    expect(within(vencido).getByText('30 oct 2026')).toBeInTheDocument();
    expect(within(vencido).getByText('Vencida hace 12 días')).toBeInTheDocument();
    expect(within(vencido).getByText('Fuera de plazo')).toBeInTheDocument();
    expect(within(await fila(SIN_AVANCES.codigo)).getByText('Sin avances hace 15 días')).toBeInTheDocument();
    expect(within(await fila(PLAN_ENVIADO.codigo)).getByText('Plan por aprobar')).toBeInTheDocument();
    expect(within(await fila(PLAN_ENVIADO.codigo)).getByText('Laura responde hasta el 17 nov · vence el 26 nov')).toBeInTheDocument();
    expect(within(await fila(PLAN_ENVIADO.codigo)).getByText('6 fases · $7.800.000 COP')).toBeInTheDocument();
    expect(within(await fila(BORRADOR.codigo)).getByText('Plan en borrador')).toBeInTheDocument();
    expect(within(await fila(BORRADOR.codigo)).getByText('Creado hoy · Faltan 79 días')).toBeInTheDocument();
    expect(within(await fila(EN_CURSO.codigo)).getByText('Faltan 23 días')).toBeInTheDocument();
    expect(within(await fila(PAUSADO_RECHAZADO.codigo)).getByText('Pausado desde el 2 nov · el cliente rechazó el plan')).toBeInTheDocument();
    expect(within(await fila(EN_GARANTIA.codigo)).getByText('Garantía hasta el 3 dic')).toBeInTheDocument();
    expect(within(await fila(EN_GARANTIA.codigo)).getByText('Solo correcciones')).toBeInTheDocument();

    expect(screen.getByRole('button', { name: /Todos/ })).toHaveTextContent('7');
    expect(screen.getByRole('button', { name: /En riesgo/ })).toHaveTextContent('2');
    expect(screen.getByRole('button', { name: /Pausados/ })).toHaveTextContent('1');
  });

  it('pide al servidor el filtro, el equipo y la búsqueda elegidos', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/proyectos');
    await fila(VENCIDO.codigo);
    const pidio = (texto: string) => fetchSimulado.mock.calls.some(([url]) => String(url).includes(texto));

    await userEvent.click(screen.getByRole('button', { name: /En riesgo/ }));
    await waitFor(() => expect(pidio('/proyectos?estado=riesgo')).toBe(true));
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Filtrar por equipo' }), 'e-maria');
    await waitFor(() => expect(pidio('equipoId=e-maria')).toBe(true));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar proyecto' }), 'peñaranda');
    await waitFor(() => expect(pidio('q=pe%C3%B1aranda')).toBe(true));
  });

  it('un proyecto en garantía no tiene acciones y uno activo ofrece editar, plan y más acciones', async () => {
    api();
    abrirEn('/admin/proyectos');
    const activo = await fila(EN_CURSO.codigo);
    expect(within(activo).getByRole('button', { name: 'Editar P-0027' })).toBeInTheDocument();
    expect(within(activo).getByRole('link', { name: 'Plan de trabajo de P-0027' })).toHaveAttribute('href', '/admin/proyectos/P-0027/plan');
    expect(within(activo).getByRole('menuitem', { name: 'Pausar' })).toBeInTheDocument();
    expect(within(await fila(PAUSADO_RECHAZADO.codigo)).getByRole('menuitem', { name: 'Reanudar' })).toBeInTheDocument();
    expect(within(await fila(EN_GARANTIA.codigo)).queryByRole('button', { name: /Editar/ })).not.toBeInTheDocument();
  });

  it('edita el nombre y la fecha, enviando solo lo que cambió', async () => {
    const fetchSimulado = api({
      'PATCH /proyectos/P-0027': { estado: 200, cuerpo: { proyecto: { ...EN_CURSO, nombre: 'Tienda Ortega' }, cambioFecha: null } },
    });
    abrirEn('/admin/proyectos');
    await userEvent.click(within(await fila('P-0027')).getByRole('button', { name: 'Editar P-0027' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Editar proyecto' });

    await userEvent.clear(within(dialogo).getByLabelText('Nombre'));
    await userEvent.type(within(dialogo).getByLabelText('Nombre'), 'Tienda Ortega');
    await userEvent.type(within(dialogo).getByLabelText('Motivo del cambio'), 'El cliente prefiere un nombre más corto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Cambios guardados en P-0027. Avisamos al cliente y al responsable.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/proyectos/P-0027')).toEqual({ nombre: 'Tienda Ortega', motivo: 'El cliente prefiere un nombre más corto' });
  });

  it('con el plan aprobado, cambiar la entrega pide un motivo de 20 caracteres y dice que el cliente debe aceptar', async () => {
    api({
      'PATCH /proyectos/P-0027': {
        estado: 200,
        cuerpo: { proyecto: EN_CURSO, cambioFecha: { cambioId: 'cp-1', estado: 'AUTORIZADO', nuevaFecha: '2026-12-20' } },
      },
    });
    abrirEn('/admin/proyectos');
    await userEvent.click(within(await fila('P-0027')).getByRole('button', { name: 'Editar P-0027' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Editar proyecto' });
    expect(within(dialogo).getByText(/la nueva fecha se la proponemos al cliente/)).toBeInTheDocument();

    const entrega = within(dialogo).getByLabelText('Entrega estimada');
    await userEvent.clear(entrega);
    await userEvent.type(entrega, '2026-12-20');
    await userEvent.type(within(dialogo).getByLabelText('Motivo del cambio'), 'Motivo de 10+');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }));
    expect(await within(dialogo).findByText('El motivo del cambio de fecha debe tener al menos 20 caracteres.')).toBeInTheDocument();

    await userEvent.type(within(dialogo).getByLabelText('Motivo del cambio'), ' y algo más de detalle');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }));
    expect(await screen.findByText('Le propusimos a Felipe Ortega entregar el 20 dic 2026. La fecha cambia cuando acepte.')).toBeInTheDocument();
  });

  it('con el plan enviado la fecha de entrega no se puede cambiar', async () => {
    api();
    abrirEn('/admin/proyectos');
    await userEvent.click(within(await fila('P-0035')).getByRole('button', { name: 'Editar P-0035' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Editar proyecto' });
    expect(within(dialogo).getByLabelText('Entrega estimada')).toBeDisabled();
    expect(within(dialogo).getByText(/espera su respuesta para cambiar la entrega/)).toBeInTheDocument();
  });

  it('reasigna a un equipo con cupo y deja sin elegir a los que tienen su cupo lleno', async () => {
    const fetchSimulado = api({
      'PATCH /proyectos/P-0027': { estado: 200, cuerpo: { proyecto: { ...EN_CURSO, equipo: { equipoId: 'e-camila', productOwner: 'Camila Jaimes', proyectosActivos: 1, maxProyectos: 3 } }, cambioFecha: null } },
    });
    abrirEn('/admin/proyectos');
    await userEvent.click(within(await fila('P-0027')).getByRole('menuitem', { name: 'Reasignar equipo' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Reasignar equipo' });

    expect(within(dialogo).getByRole('radio', { name: /María Ruiz/ })).toBeDisabled(); // el responsable actual
    expect(within(dialogo).getByRole('radio', { name: /Andrés Pérez/ })).toBeDisabled(); // 3 de 3
    expect(within(dialogo).getByText('Sin cupo: ya tiene 3 proyectos activos')).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Reasignar' }));
    expect(within(dialogo).getByText('Elige el nuevo equipo.')).toBeInTheDocument();

    await userEvent.click(within(dialogo).getByRole('radio', { name: /Camila Jaimes/ }));
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'María está sin cupo para atender este');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Reasignar' }));

    expect(await screen.findByText('P-0027 pasó de María Ruiz a Camila Jaimes.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/proyectos/P-0027')).toEqual({ equipoId: 'e-camila', motivo: 'María está sin cupo para atender este' });
  });

  it('pausa con un motivo de 10 caracteres o más', async () => {
    const fetchSimulado = api({ 'PATCH /proyectos/P-0027/estado': { estado: 200, cuerpo: { ...EN_CURSO, estado: 'PAUSADO' } } });
    abrirEn('/admin/proyectos');
    await userEvent.click(within(await fila('P-0027')).getByRole('menuitem', { name: 'Pausar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Pausar proyecto' });

    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'corto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Pausar proyecto' }));
    expect(within(dialogo).getByText('Escribe al menos 10 caracteres.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'PATCH')).toBe(false);

    await userEvent.type(within(dialogo).getByLabelText('Motivo'), ' y el resto del motivo');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Pausar proyecto' }));
    expect(await screen.findByText('P-0027 pausado. Ya no cuenta como proyecto activo de María Ruiz.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/proyectos/P-0027/estado')).toMatchObject({ estado: 'PAUSADO' });
  });

  it('reanudar un plan rechazado obliga a elegir el equipo y avisa que el plan vuelve a borrador', async () => {
    const fetchSimulado = api({ 'PATCH /proyectos/P-0029/estado': { estado: 200, cuerpo: { ...PAUSADO_RECHAZADO, estado: 'ACTIVO', estadoPlan: 'BORRADOR' } } });
    abrirEn('/admin/proyectos');
    await userEvent.click(within(await fila('P-0029')).getByRole('menuitem', { name: 'Reanudar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Reanudar proyecto' });
    expect(within(dialogo).getByText(/el plan vuelve a borrador/)).toBeInTheDocument();

    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'El cliente quiere retomarlo');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Reanudar proyecto' }));
    expect(within(dialogo).getByText('Elige el equipo que retomará el proyecto (puede ser el mismo).')).toBeInTheDocument();

    await userEvent.click(within(dialogo).getByRole('radio', { name: /Camila Jaimes/ }));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Reanudar proyecto' }));
    expect(await screen.findByText('P-0029 reanudado.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/proyectos/P-0029/estado')).toEqual({ estado: 'ACTIVO', motivo: 'El cliente quiere retomarlo', equipoId: 'e-camila' });
  });

  it('muestra el mensaje de la API cuando el equipo ya no tiene cupo', async () => {
    api({ 'PATCH /proyectos/P-0027/estado': { estado: 409, cuerpo: errores({ mensaje: 'El proyecto ya está pausado' }) } });
    abrirEn('/admin/proyectos');
    await userEvent.click(within(await fila('P-0027')).getByRole('menuitem', { name: 'Cancelar proyecto' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Cancelar proyecto' });
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'El cliente desistió por presupuesto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar proyecto' }));

    expect(await within(dialogo).findByText('El proyecto ya está pausado')).toBeInTheDocument();
  });

  it('sin resultados ofrece limpiar los filtros', async () => {
    api({ 'GET /proyectos': { estado: 200, cuerpo: { ...LISTA, proyectos: [], totalFiltrados: 0 } } });
    abrirEn('/admin/proyectos');
    expect(await screen.findByText('No hay proyectos con estos filtros')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Limpiar filtros' })).toBeInTheDocument();
  });
});
