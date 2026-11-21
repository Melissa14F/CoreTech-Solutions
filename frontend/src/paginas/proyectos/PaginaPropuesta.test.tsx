import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { LAURA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { PLAN_APROBADO, PLAN_ENVIADO } from '../../pruebas/plan';

const RUTA = '/cliente/proyectos/P-0035/propuesta';

function api(plan = PLAN_ENVIADO, extra = {}) {
  return simularApi(LAURA, { 'GET /proyectos/P-0035/plan': { estado: 200, cuerpo: plan }, ...extra });
}

describe('Propuesta de plan y cotización del cliente (HU-29)', () => {
  it('muestra las fases, la cotización y los tres botones para responder', async () => {
    api();
    abrirEn(RUTA);

    expect(await screen.findByRole('heading', { level: 1, name: 'Inventario del salón Propuesta por aprobar' })).toBeInTheDocument();
    expect(screen.getByText(/Andrés Pérez te envió el plan de trabajo y la cotización el 11 nov\./)).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Plan de trabajo: 3 fases' })).toBeInTheDocument();
    expect(screen.getByText(/Tú apruebas Toma de requerimientos y Pruebas; las demás las cierra el equipo\./)).toBeInTheDocument();
    expect(screen.getByText('Conocemos tus bodegas y tus productos. Vale 33,3 % del proyecto.')).toBeInTheDocument();
    expect(screen.getByText('$7.800.000')).toBeInTheDocument();
    expect(screen.getAllByText('12 feb 2027').length).toBeGreaterThan(0);
    expect(screen.getByText('2 fases')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprobar plan y cotización' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Pedir ajustes' })).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Rechazar propuesta' })).toBeInTheDocument();
    // En el celular los tres botones quedan fijos abajo
    expect(within(screen.getByRole('group', { name: 'Responder la propuesta' })).getAllByRole('button')).toHaveLength(3);
  });

  it('aprobar exige la casilla y luego el plan queda aprobado', async () => {
    const fetchSimulado = api(PLAN_ENVIADO, {
      'POST /proyectos/P-0035/plan/aprobar': { estado: 200, cuerpo: { estadoPlan: 'APROBADO', estado: 'ACTIVO', planAprobadoAt: '2026-11-13T15:00:00Z' } },
    });
    const enrutador = abrirEn(RUTA);
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar plan y cotización' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Aprobar plan y cotización' });
    expect(within(dialogo).getByText('Al aprobar, el plan queda fijo y Andrés Pérez empieza la fase 1: Toma de requerimientos.')).toBeInTheDocument();

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar' }));
    expect(await within(dialogo).findByText('Confirma que leíste el plan y la cotización.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);

    await userEvent.click(within(dialogo).getByRole('checkbox', { name: /Leí el plan de trabajo y la cotización/ }));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar' }));

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/proyectos'));
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/proyectos/P-0035/plan/aprobar')).toEqual({ aceptaPlanYCotizacion: true });
    expect(await screen.findByText('¡Plan aprobado! Andrés empieza la fase 1 y verás el avance en tu panel.')).toBeInTheDocument();
  });

  it('pedir ajustes pide al menos 20 caracteres y manda el comentario', async () => {
    const fetchSimulado = api(PLAN_ENVIADO, {
      'POST /proyectos/P-0035/plan/ajustes': { estado: 201, cuerpo: { estadoPlan: 'AJUSTES_PEDIDOS', estado: 'ACTIVO', respuestaId: 'r-1' } },
    });
    const enrutador = abrirEn(RUTA);
    await userEvent.click((await screen.findAllByRole('button', { name: 'Pedir ajustes' }))[0]!);
    const dialogo = await screen.findByRole('dialog', { name: 'Pedir ajustes' });
    const comentario = within(dialogo).getByLabelText('Qué quieres cambiar');

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar comentarios' }));
    expect(await within(dialogo).findByText('Cuéntanos qué quieres cambiar.')).toBeInTheDocument();

    await userEvent.type(comentario, 'Muy corto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar comentarios' }));
    expect(await within(dialogo).findByText('Escribe al menos 20 caracteres para que Andrés te entienda.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);

    await userEvent.clear(comentario);
    await userEvent.type(comentario, 'Falta una fase de capacitación del personal.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar comentarios' }));

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/proyectos'));
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/proyectos/P-0035/plan/ajustes')).toEqual({ comentario: 'Falta una fase de capacitación del personal.' });
    expect(await screen.findByText('Enviamos tus comentarios a Andrés. Te avisaremos cuando el plan esté ajustado.')).toBeInTheDocument();
  });

  it('rechazar pide el motivo (20 caracteres) y avisa que el proyecto queda en pausa', async () => {
    const fetchSimulado = api(PLAN_ENVIADO, {
      'POST /proyectos/P-0035/plan/rechazar': { estado: 201, cuerpo: { estadoPlan: 'RECHAZADO', estado: 'PAUSADO', respuestaId: 'r-2' } },
    });
    const enrutador = abrirEn(RUTA);
    await userEvent.click(await screen.findByRole('button', { name: 'Rechazar propuesta' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Rechazar la propuesta' });
    const motivo = within(dialogo).getByLabelText('Por qué la rechazas');

    await userEvent.type(motivo, 'No me sirve');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, rechazar' }));
    expect(await within(dialogo).findByText('Cuéntanos por qué la rechazas (mínimo 20 caracteres)')).toBeInTheDocument();

    await userEvent.clear(motivo);
    await userEvent.type(motivo, 'El valor supera nuestro presupuesto de este año.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, rechazar' }));

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/proyectos'));
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/proyectos/P-0035/plan/rechazar')).toEqual({ motivo: 'El valor supera nuestro presupuesto de este año.' });
    expect(await screen.findByText('Rechazaste la propuesta. El proyecto quedó en pausa; si no respondes en 30 días, se cancelará.')).toBeInTheDocument();
  });

  it('si el plan ya no espera respuesta (409) muestra el mensaje del servidor en el diálogo', async () => {
    api(PLAN_ENVIADO, {
      'POST /proyectos/P-0035/plan/aprobar': { estado: 409, cuerpo: errores({ mensaje: 'Este plan no está esperando tu respuesta' }) },
    });
    abrirEn(RUTA);
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar plan y cotización' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Aprobar plan y cotización' });
    await userEvent.click(within(dialogo).getByRole('checkbox'));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar' }));

    expect(await within(dialogo).findByText('Este plan no está esperando tu respuesta')).toBeInTheDocument();
  });

  it('un plan ya aprobado se lee sin botones para responder', async () => {
    api(PLAN_APROBADO);
    abrirEn(RUTA);

    expect(await screen.findByText('Este plan no está esperando tu respuesta')).toBeInTheDocument();
    expect(screen.getByText('Ya aprobaste el plan y la cotización: el trabajo está en marcha.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar plan y cotización' })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Responder la propuesta' })).not.toBeInTheDocument();
  });

  it('un proyecto de otro cliente se ve como «Proyecto no encontrado»', async () => {
    simularApi(LAURA, { 'GET /proyectos/P-0035/plan': { estado: 404, cuerpo: errores({ mensaje: 'Proyecto no encontrado' }) } });
    abrirEn(RUTA);
    expect(await screen.findByRole('heading', { name: 'Proyecto no encontrado' })).toBeInTheDocument();
  });
});
