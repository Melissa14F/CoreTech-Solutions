import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ANDRES, PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { PLAN_AJUSTES, PLAN_APROBADO, PLAN_BORRADOR, PLAN_ENVIADO, PLAN_RECHAZADO } from '../../pruebas/plan';

const RUTA = '/equipo/proyectos/P-0035/plan';

function api(plan = PLAN_BORRADOR, extra = {}) {
  return simularApi(ANDRES, { 'GET /proyectos/P-0035/plan': { estado: 200, cuerpo: plan }, ...extra });
}

const enviar = () => screen.getAllByRole('button', { name: 'Enviar a Laura' })[0]!;

async function completarCotizacion() {
  await userEvent.type(screen.getByLabelText('Valor del proyecto'), '7800000');
  fireEvent.change(screen.getByLabelText('Fecha estimada de entrega'), { target: { value: '2027-02-12' } });
  await userEvent.type(screen.getByLabelText('Qué incluye y qué no'), 'Incluye 2 rondas de ajustes y 30 días de garantía.');
}

describe('Plan de trabajo del equipo (HU-28)', () => {
  it('en borrador muestra las fases para editar y la cotización vacía, con el plazo de respuesta', async () => {
    api();
    abrirEn(RUTA);

    expect(await screen.findByRole('heading', { level: 1, name: 'Plan de trabajo Borrador' })).toBeInTheDocument();
    expect(screen.getByText(/Inventario del salón/)).toBeInTheDocument();
    expect(screen.getByText(/Se copió de la plantilla «Sistema de inventario» el 11 nov 2026\. Ajústalo a lo que se habló con Laura en la cita C-0049\./)).toBeInTheDocument();
    expect(screen.getByLabelText('Valor del proyecto')).toHaveValue('');
    expect(screen.getByText('El proyecto empieza el 16 nov 2026.')).toBeInTheDocument();
    expect(screen.getByText('A los 10 días hábiles')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Enviar a Laura' })).toHaveLength(2);
  });

  it('el valor se escribe con puntos de miles', async () => {
    api();
    abrirEn(RUTA);
    const valor = await screen.findByLabelText('Valor del proyecto');
    await userEvent.type(valor, '7800000');
    expect(valor).toHaveValue('7.800.000');
  });

  it('sin la cotización no envía y marca cada campo con su mensaje', async () => {
    const fetchSimulado = api();
    abrirEn(RUTA);
    await screen.findByLabelText('Valor del proyecto');
    await userEvent.click(enviar());

    expect(await screen.findByText('Escribe el valor del proyecto.')).toBeInTheDocument();
    expect(screen.getByText('Escribe qué incluye el valor.')).toBeInTheDocument();
    expect(screen.getByLabelText('Valor del proyecto')).toHaveFocus();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('un valor de $999.999 o de $200.000.001 se rechaza', async () => {
    api();
    abrirEn(RUTA);
    const valor = await screen.findByLabelText('Valor del proyecto');
    await completarCotizacion();
    await userEvent.clear(valor);
    await userEvent.type(valor, '999999');
    await userEvent.click(enviar());
    expect(await screen.findByText('El valor debe estar entre $1.000.000 y $200.000.000.')).toBeInTheDocument();

    await userEvent.clear(valor);
    await userEvent.type(valor, '200000001');
    expect(await screen.findByText('El valor debe estar entre $1.000.000 y $200.000.000.')).toBeInTheDocument();
  });

  it('la entrega debe ser dos semanas después del inicio', async () => {
    api();
    abrirEn(RUTA);
    await screen.findByLabelText('Valor del proyecto');
    await completarCotizacion();
    fireEvent.change(screen.getByLabelText('Fecha estimada de entrega'), { target: { value: '2026-11-20' } });
    await userEvent.click(enviar());
    expect(await screen.findByText('La entrega debe ser al menos 2 semanas después del inicio (16 nov 2026).')).toBeInTheDocument();
  });

  it('con todo completo muestra el resumen, envía el plan y vuelve a los proyectos', async () => {
    const fetchSimulado = api(PLAN_BORRADOR, {
      'POST /proyectos/P-0035/plan/enviar': {
        estado: 200, cuerpo: { estadoPlan: 'ENVIADO', porcentajePorFase: 33.3, plazoRespuesta: '2026-11-17', venceEl: '2026-11-26' },
      },
    });
    const enrutador = abrirEn(RUTA);
    await screen.findByLabelText('Valor del proyecto');
    await completarCotizacion();
    await userEvent.click(enviar());

    const dialogo = await screen.findByRole('dialog', { name: 'Enviar el plan a Laura' });
    expect(within(dialogo).getByText('$7.800.000 COP')).toBeInTheDocument();
    expect(within(dialogo).getByText('12 feb 2027')).toBeInTheDocument();
    expect(within(dialogo).getByText('3 · 33,3 % cada una')).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar plan' }));

    await waitFor(() => expect(enrutador.state.location.pathname).toBe(RUTA.replace('/equipo/proyectos/P-0035/plan', '/equipo/proyectos')));
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/proyectos/P-0035/plan/enviar')).toMatchObject({
      valorCotizado: 7800000, fechaEstimadaEntrega: '2027-02-12', condicionesCotizacion: 'Incluye 2 rondas de ajustes y 30 días de garantía.',
    });
    expect(await screen.findByText('Plan enviado a Laura. Tiene hasta el martes 17 nov para aprobarlo o pedir ajustes.')).toBeInTheDocument();
  });

  it('el administrador vuelve a la lista de proyectos con el código buscado', async () => {
    simularApi(PATRICIA, {
      'GET /proyectos/P-0035/plan': { estado: 200, cuerpo: PLAN_BORRADOR },
      'POST /proyectos/P-0035/plan/enviar': { estado: 200, cuerpo: { estadoPlan: 'ENVIADO', porcentajePorFase: 33.3, plazoRespuesta: '2026-11-17', venceEl: '2026-11-26' } },
    });
    const enrutador = abrirEn('/admin/proyectos/P-0035/plan');
    await screen.findByLabelText('Valor del proyecto');
    await completarCotizacion();
    await userEvent.click(enviar());
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Enviar el plan a Laura' })).getByRole('button', { name: 'Enviar plan' }));

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/admin/proyectos'));
    expect(enrutador.state.location.search).toBe('?buscar=P-0035');
  });

  it('el servidor puede rechazar el valor con un 422 y se muestra bajo el campo', async () => {
    api(PLAN_BORRADOR, {
      'POST /proyectos/P-0035/plan/enviar': {
        estado: 422, cuerpo: errores({ campo: 'valorCotizado', mensaje: 'El valor debe estar entre $1.000.000 y $200.000.000.' }),
      },
    });
    abrirEn(RUTA);
    await screen.findByLabelText('Valor del proyecto');
    await completarCotizacion();
    await userEvent.click(enviar());
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Enviar el plan a Laura' })).getByRole('button', { name: 'Enviar plan' }));

    expect(await screen.findByText('El valor debe estar entre $1.000.000 y $200.000.000.')).toBeInTheDocument();
    expect(screen.getByLabelText('Valor del proyecto')).toBeInvalid();
  });

  it('guardar el borrador manda el plan completo sin enviarlo', async () => {
    const fetchSimulado = api(PLAN_BORRADOR, { 'PUT /proyectos/P-0035/plan': { estado: 200, cuerpo: { ...PLAN_BORRADOR, valorCotizado: 5_000_000 } } });
    abrirEn(RUTA);
    const valor = await screen.findByLabelText('Valor del proyecto');
    const guardar = screen.getByRole('button', { name: 'Guardar borrador' });
    expect(guardar).toBeDisabled();
    await userEvent.type(valor, '5000000');
    await userEvent.click(guardar);

    expect(await screen.findByText('Borrador guardado.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/proyectos/P-0035/plan')).toMatchObject({ valorCotizado: 5000000 });
    expect(fetchSimulado.mock.calls.some(([url]) => String(url).endsWith('/enviar'))).toBe(false);
  });

  it('con ajustes pedidos muestra el comentario del cliente y deja editar', async () => {
    api(PLAN_AJUSTES);
    abrirEn(RUTA);
    expect(await screen.findByRole('heading', { level: 1, name: 'Plan de trabajo Ajustes pedidos' })).toBeInTheDocument();
    expect(screen.getByText('Laura pidió ajustes al plan')).toBeInTheDocument();
    expect(screen.getByText('Falta una fase de capacitación del personal.')).toBeInTheDocument();
    expect(screen.getByLabelText('Valor del proyecto')).toHaveValue('7.800.000');
  });

  it('con el plan enviado queda en lectura y permite recordar al cliente', async () => {
    api(PLAN_ENVIADO, { 'POST /proyectos/P-0035/plan/recordar': { estado: 204 } });
    abrirEn(RUTA);

    expect(await screen.findByRole('heading', { level: 1, name: 'Plan de trabajo Enviado' })).toBeInTheDocument();
    expect(screen.getByText(/Enviaste el plan y la cotización a Laura el 11 nov\./)).toBeInTheDocument();
    expect(screen.getAllByText('martes 17 nov').length).toBeGreaterThan(0);
    expect(screen.getAllByText('jueves 26 nov').length).toBeGreaterThan(0);
    expect(screen.getByText('$7.800.000')).toBeInTheDocument();
    expect(screen.getAllByText('La aprueba Laura', { selector: '.insignia' })).toHaveLength(2);
    expect(screen.queryByLabelText('Valor del proyecto')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar a Laura' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Recordar a Laura' }));
    expect(await screen.findByText('Le recordamos a Laura por correo que tiene el plan por responder.')).toBeInTheDocument();
  });

  it('un plan aprobado o rechazado se ve en lectura con su mensaje', async () => {
    api(PLAN_APROBADO);
    abrirEn(RUTA);
    expect(await screen.findByText('Laura aprobó el plan y la cotización el 13 nov')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Enviar a|Recordar a/ })).not.toBeInTheDocument();
  });

  it('un plan rechazado muestra el motivo del cliente', async () => {
    api(PLAN_RECHAZADO);
    abrirEn(RUTA);
    expect(await screen.findByText('Laura rechazó la propuesta')).toBeInTheDocument();
    expect(screen.getByText(/El valor supera nuestro presupuesto de este año\./)).toBeInTheDocument();
  });

  it('un proyecto que no es de su equipo (403) muestra el mensaje del servidor', async () => {
    simularApi(ANDRES, { 'GET /proyectos/P-0035/plan': { estado: 403, cuerpo: errores({ mensaje: 'Este proyecto no es de tu equipo' }) } });
    abrirEn(RUTA);
    expect(await screen.findByText('Este proyecto no es de tu equipo')).toBeInTheDocument();
  });
});
