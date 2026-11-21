import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { validarCuestionario } from './PaginaCitasEquipo';
import { ANDRES, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import {
  AHORA_PRUEBA, ATENDIDA, CANCELADA, CONFIRMADA_FUTURA, CONFIRMADA_PASADA, DESDE_MEXICO, SIN_EQUIPO,
} from '../../pruebas/gestionCitas';

const CITAS = [SIN_EQUIPO, DESDE_MEXICO, CONFIRMADA_FUTURA, CONFIRMADA_PASADA, ATENDIDA, CANCELADA];
const PRESENCIAL_PENDIENTE = { ...SIN_EQUIPO, citaId: 'c-70', codigo: 'C-0070', modalidad: 'PRESENCIAL' as const };

const tarjeta = (codigo: string) => screen.getByText(codigo).closest('li') as HTMLElement;

function api(extra = {}, citas = CITAS) {
  return simularApi(ANDRES, { 'GET /citas/equipo': { estado: 200, cuerpo: citas }, ...extra });
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(AHORA_PRUEBA);
});
afterEach(() => vi.useRealTimers());

describe('Citas y reuniones del equipo (HU-10)', () => {
  it('agrupa las citas: por confirmar, confirmadas, cuestionario pendiente e historial', async () => {
    api();
    abrirEn('/equipo/citas');
    await screen.findByText('C-0061');

    const titulos = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(titulos).toEqual(['Por confirmar2', 'Confirmadas', 'Cuestionario pendiente1', 'Historial', 'Reuniones de seguimiento']);

    // Por confirmar: de la más cercana a la más lejana, con ambas horas si el cliente vive fuera
    const codigos = screen.getAllByText(/^C-00\d\d$/).map((e) => e.textContent);
    expect(codigos).toEqual(['C-0061', 'C-0062', 'C-0059', 'C-0058', 'C-0055', 'C-0052']);
    expect(within(tarjeta('C-0062')).getByText('México · para el cliente son las 8:00 a. m.')).toBeInTheDocument();
    expect(within(tarjeta('C-0062')).getByText(/Nota de la administración: Natalia ya es cliente\./)).toBeInTheDocument();
    expect(within(tarjeta('C-0061')).getByRole('button', { name: 'Confirmar' })).toBeInTheDocument();

    // Confirmada futura: el enlace, sin botón de cuestionario (la reunión aún no ocurre)
    expect(within(tarjeta('C-0059')).getByText('meet.google.com/fyo-pqrs-tuv')).toBeInTheDocument();
    expect(within(tarjeta('C-0059')).queryByRole('button', { name: 'Llenar cuestionario' })).not.toBeInTheDocument();
    // Confirmada que ya pasó: falta el cuestionario
    expect(within(tarjeta('C-0058')).getByRole('button', { name: 'Llenar cuestionario' })).toBeInTheDocument();
    expect(within(tarjeta('C-0052')).getByRole('button', { name: 'Ver cuestionario' })).toBeInTheDocument();
    expect(within(tarjeta('C-0055')).getByText('Motivo: El cliente pidió cambiar la fecha.')).toBeInTheDocument();
  });

  it('una cita virtual no se confirma sin el enlace de la reunión', async () => {
    const fetchSimulado = api();
    abrirEn('/equipo/citas');
    await userEvent.click(within(await screen.findByText('C-0061').then(() => tarjeta('C-0061'))).getByRole('button', { name: 'Confirmar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Confirmar cita' });

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar cita' }));
    expect(within(dialogo).getByText('Pega el enlace: la cita es virtual y el cliente lo necesita para unirse.')).toBeInTheDocument();
    await userEvent.type(within(dialogo).getByLabelText('Enlace de la videollamada'), 'meet.google.com/abc');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar cita' }));
    expect(within(dialogo).getByText('El enlace debe empezar por https://')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'PATCH')).toBe(false);
  });

  it('confirma una cita virtual con su enlace y la nota para el cliente', async () => {
    const fetchSimulado = api({
      'PATCH /citas/c-61/confirmar': { estado: 200, cuerpo: { citaId: 'c-61', estado: 'CONFIRMADA', confirmadaAt: '2031-03-04T15:00:00Z' } },
    });
    abrirEn('/equipo/citas');
    await screen.findByText('C-0061');
    await userEvent.click(within(tarjeta('C-0061')).getByRole('button', { name: 'Confirmar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Confirmar cita' });

    await userEvent.type(within(dialogo).getByLabelText('Enlace de la videollamada'), 'https://meet.google.com/abc-defg-hij');
    await userEvent.type(within(dialogo).getByLabelText(/Nota para el cliente/), 'Trae ejemplos que te gusten.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar cita' }));

    expect(await screen.findByText('Cita C-0061 confirmada. Laura recibirá un aviso con el enlace.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/citas/c-61/confirmar')).toEqual({
      enlaceReunion: 'https://meet.google.com/abc-defg-hij', nota: 'Trae ejemplos que te gusten.',
    });
  });

  it('una cita presencial se confirma sin pedir enlace', async () => {
    const fetchSimulado = api({
      'PATCH /citas/c-70/confirmar': { estado: 200, cuerpo: { citaId: 'c-70', estado: 'CONFIRMADA', confirmadaAt: '2031-03-04T15:00:00Z' } },
    }, [PRESENCIAL_PENDIENTE]);
    abrirEn('/equipo/citas');
    await screen.findByText('C-0070');
    await userEvent.click(within(tarjeta('C-0070')).getByRole('button', { name: 'Confirmar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Confirmar cita' });

    expect(within(dialogo).queryByLabelText('Enlace de la videollamada')).not.toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar cita' }));
    expect(await screen.findByText('Cita C-0070 confirmada. Laura recibirá un aviso.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/citas/c-70/confirmar')).toEqual({ enlaceReunion: null, nota: null });
  });

  it('el cuestionario incompleto no se envía y marca cada campo con el mensaje del prototipo', async () => {
    const fetchSimulado = api();
    abrirEn('/equipo/citas');
    await screen.findByText('C-0058');
    await userEvent.click(within(tarjeta('C-0058')).getByRole('button', { name: 'Llenar cuestionario' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Cuestionario de la cita' });

    await userEvent.type(within(dialogo).getByLabelText('Resumen de la reunión'), 'muy corto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar y cerrar la cita' }));

    expect(within(dialogo).getByText('Completa el cuestionario: faltan datos.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Escribe al menos 30 caracteres.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Describe al cliente.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Elige el presupuesto aproximado.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Elige el idioma.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Elige el nivel de urgencia.')).toBeInTheDocument();
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('envía el cuestionario completo y la cita queda atendida', async () => {
    const fetchSimulado = api({
      'POST /citas/c-58/cuestionario': { estado: 201, cuerpo: { cuestionarioId: 'q-1', citaEstado: 'ATENDIDA' } },
    });
    abrirEn('/equipo/citas');
    await screen.findByText('C-0058');
    await userEvent.click(within(tarjeta('C-0058')).getByRole('button', { name: 'Llenar cuestionario' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Cuestionario de la cita' });

    await userEvent.type(within(dialogo).getByLabelText('Resumen de la reunión'), 'Hablamos del alcance del inventario y los tiempos.');
    await userEvent.type(within(dialogo).getByLabelText('Descripción del cliente'), 'Dueño de una ferretería con dos bodegas en Cúcuta.');
    await userEvent.selectOptions(within(dialogo).getByLabelText('Presupuesto aproximado'), 'DE_5_A_15M');
    await userEvent.selectOptions(within(dialogo).getByLabelText('Idioma preferido'), 'ESPANOL');
    await userEvent.click(within(dialogo).getByRole('radio', { name: /Media/ }));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar y cerrar la cita' }));

    expect(await screen.findByText('Cuestionario enviado. La cita C-0058 quedó atendida.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/citas/c-58/cuestionario')).toEqual({
      resumen: 'Hablamos del alcance del inventario y los tiempos.',
      descripcionCliente: 'Dueño de una ferretería con dos bodegas en Cúcuta.',
      presupuesto: 'DE_5_A_15M', fechaDeseada: null, idioma: 'ESPANOL', urgencia: 'MEDIA',
    });
  });

  it('si la API dice que ya tiene cuestionario, lo muestra en el diálogo', async () => {
    api({ 'POST /citas/c-58/cuestionario': { estado: 409, cuerpo: errores({ mensaje: 'Esta cita ya tiene su cuestionario' }) } });
    abrirEn('/equipo/citas');
    await screen.findByText('C-0058');
    await userEvent.click(within(tarjeta('C-0058')).getByRole('button', { name: 'Llenar cuestionario' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Cuestionario de la cita' });
    await userEvent.type(within(dialogo).getByLabelText('Resumen de la reunión'), 'Hablamos del alcance del inventario y los tiempos.');
    await userEvent.type(within(dialogo).getByLabelText('Descripción del cliente'), 'Dueño de una ferretería con dos bodegas en Cúcuta.');
    await userEvent.selectOptions(within(dialogo).getByLabelText('Presupuesto aproximado'), 'MAS_30M');
    await userEvent.selectOptions(within(dialogo).getByLabelText('Idioma preferido'), 'INGLES');
    await userEvent.click(within(dialogo).getByRole('radio', { name: /Alta/ }));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar y cerrar la cita' }));

    expect(await within(dialogo).findByText('Esta cita ya tiene su cuestionario')).toBeInTheDocument();
  });

  it('sin citas asignadas lo explica', async () => {
    api({}, []);
    abrirEn('/equipo/citas');
    expect(await screen.findByText('Aún no tienes citas asignadas')).toBeInTheDocument();
  });

  it('validarCuestionario usa los mismos límites que la API', () => {
    expect(validarCuestionario({ resumen: 'x'.repeat(29), descripcionCliente: 'y'.repeat(30), presupuesto: 'MAS_30M', fechaDeseada: '', idioma: 'OTRO', urgencia: 'BAJA' }))
      .toEqual({ resumen: 'Escribe al menos 30 caracteres.' });
    expect(validarCuestionario({ resumen: 'x'.repeat(30), descripcionCliente: 'y'.repeat(30), presupuesto: 'MAS_30M', fechaDeseada: '', idioma: 'OTRO', urgencia: 'BAJA' }))
      .toEqual({});
  });
});
