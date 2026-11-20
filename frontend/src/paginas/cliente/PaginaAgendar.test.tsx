import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

import { AHORA_PRUEBA, MARZO_COLOMBIA, MARZO_MADRID } from '../../pruebas/agenda';
import { LAURA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { TIENDA, WEB, publico } from '../../pruebas/catalogo';

const CREADA = {
  citaId: 'c-64', codigo: 'C-0064', estado: 'PENDIENTE',
  fechaHoraLocal: '2031-03-06T09:00:00-05:00', fechaHoraColombia: '2031-03-06T09:00:00-05:00',
};
const DESCRIPCION = 'Tengo una tienda de ropa en Cúcuta y quiero vender por internet.';

function api(extra: Parameters<typeof simularApi>[1] = {}) {
  return simularApi(LAURA, {
    'GET /servicios': { estado: 200, cuerpo: [publico(TIENDA), publico(WEB)] },
    'GET /citas/disponibilidad': { estado: 200, cuerpo: MARZO_COLOMBIA },
    'POST /citas': { estado: 201, cuerpo: CREADA },
    ...extra,
  });
}

const consultasDisponibilidad = (fetchSimulado: ReturnType<typeof api>) =>
  fetchSimulado.mock.calls.map(([url]) => String(url)).filter((url) => url.includes('/citas/disponibilidad'));

async function elegirServicioYContinuar(nombre = 'Desarrollo de tienda en línea') {
  await userEvent.click(await screen.findByRole('radio', { name: new RegExp(nombre) }));
  await userEvent.click(screen.getByRole('button', { name: /Continuar/ }));
  await screen.findByRole('heading', { name: '¿Cuándo quieres la cita?' });
}

async function completarPaso2() {
  await userEvent.click(await screen.findByRole('button', { name: /Jueves 6 de marzo, 3 horarios libres/ }));
  await userEvent.click(screen.getByRole('radio', { name: /9:00 a\. m\./ }));
  await userEvent.click(screen.getByRole('radio', { name: /Virtual/ }));
  await userEvent.type(screen.getByRole('textbox', { name: 'Qué necesitas' }), DESCRIPCION);
}

describe('Agendar una cita (HU-01)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(AHORA_PRUEBA);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('paso 1: no continúa sin servicio y llega con el servicio del catálogo ya elegido', async () => {
    api();
    abrirEn('/cliente/agendar');
    await userEvent.click(await screen.findByRole('button', { name: /Continuar/ }));
    expect(screen.getByRole('alert')).toHaveTextContent('Elige el servicio que necesitas para continuar.');
    expect(screen.getByText('Sin elegir')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '¿Cuándo quieres la cita?' })).not.toBeInTheDocument();
  });

  it('llega con el servicio de ?servicio=slug marcado', async () => {
    api();
    abrirEn('/cliente/agendar?servicio=web-corporativa');
    expect(await screen.findByRole('radio', { name: /Página web corporativa/ })).toBeChecked();
    expect(screen.getByText('Página web corporativa', { selector: 'dd' })).toBeInTheDocument();
  });

  it('hace los tres pasos y muestra la cita registrada', async () => {
    const fetchSimulado = api();
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    await completarPaso2();
    // El resumen lateral se va llenando
    expect(screen.getByText('Jueves 6 de marzo · 9:00 a. m.')).toBeInTheDocument();
    expect(screen.getByText('Virtual', { selector: 'dd' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Continuar/ }));
    expect(await screen.findByRole('heading', { name: 'Tu cita' })).toBeInTheDocument();
    expect(screen.getByText('Jueves 6 de marzo de 2031')).toBeInTheDocument();
    expect(screen.getByText('9:00 a. m. a 10:00 a. m.')).toBeInTheDocument();
    expect(screen.getByText(DESCRIPCION)).toBeInTheDocument();
    expect(screen.getAllByText('laura.gomez@correo.com').length).toBeGreaterThan(1); // en el menú y en "A nombre de"
    expect(screen.getByText('Para agendar la cita deberás realizar su pago antes de confirmarla.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cita' }));
    expect(await screen.findByRole('heading', { name: 'Tu cita quedó registrada' })).toBeInTheDocument();
    expect(screen.getByText('C-0064')).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mis citas' })).toHaveAttribute('href', '/cliente/citas');

    // El instante se envía tal cual lo dio la disponibilidad, con el país y la zona
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/citas')).toEqual({
      servicioId: 's-tienda', fechaHora: '2031-03-06T09:00:00-05:00', modalidad: 'VIRTUAL',
      descripcionNecesidad: DESCRIPCION, pais: 'Colombia', zonaHoraria: 'America/Bogota',
    });
    expect(consultasDisponibilidad(fetchSimulado)[0]).toContain('servicioId=s-tienda&mes=2031-03&zona=America%2FBogota');
  });

  it('paso 2: marca lo que falta, con los textos del prototipo, y lleva el foco al primer error', async () => {
    api();
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    await userEvent.type(screen.getByRole('textbox', { name: 'Qué necesitas' }), 'Muy corto');
    await userEvent.click(screen.getByRole('button', { name: /Continuar/ }));

    const alertas = screen.getAllByRole('alert').map((a) => a.textContent);
    expect(alertas).toContain('Elige el día de tu cita.');
    expect(alertas).toContain('Elige la hora de tu cita.');
    expect(alertas).toContain('Elige si la cita es presencial o virtual.');
    expect(alertas).toContain('Escribe un poco más (mínimo 20 caracteres) para entender tu necesidad.');
    expect(screen.getByRole('textbox', { name: 'Qué necesitas' })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByRole('heading', { name: 'Tu cita' })).not.toBeInTheDocument();
  });

  it('muestra "Ya tienes una cita" y las horas ocupadas sin dejar elegirlas', async () => {
    api();
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    await userEvent.click(await screen.findByRole('button', { name: /Jueves 6 de marzo/ }));
    expect(screen.getByText('3 de 5 libres')).toBeInTheDocument();
    expect(screen.getByText('Ya tienes una cita')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /10:00 a\. m\., ocupada/ })).toBeDisabled();
    expect(screen.getByRole('radio', { name: /11:00 a\. m\./ })).toBeEnabled();
  });

  it('volver atrás no pierde lo escrito', async () => {
    api();
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    await completarPaso2();
    await userEvent.click(screen.getByRole('button', { name: /Continuar/ }));
    await screen.findByRole('heading', { name: 'Tu cita' });

    await userEvent.click(screen.getByRole('button', { name: 'Cambiar hora' }));
    expect(await screen.findByRole('textbox', { name: 'Qué necesitas' })).toHaveValue(DESCRIPCION);
    expect(screen.getByRole('radio', { name: /Virtual/ })).toBeChecked();
    expect(await screen.findByRole('radio', { name: /9:00 a\. m\./ })).toBeChecked();
    expect(screen.getByRole('button', { name: /Jueves 6 de marzo/ })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(screen.getByRole('button', { name: /Atrás/ }));
    expect(screen.getByRole('radio', { name: /Desarrollo de tienda en línea/ })).toBeChecked();
  });

  it('si la franja se ocupó (409), vuelve al paso 2 con el servicio y la descripción', async () => {
    const fetchSimulado = api({
      'POST /citas': { estado: 409, cuerpo: errores({ campo: 'fechaHora', mensaje: 'La hora que elegiste ya no está disponible, elige otra' }) },
    });
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    await completarPaso2();
    await userEvent.click(screen.getByRole('button', { name: /Continuar/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar cita' }));

    expect(await screen.findByText('La hora que elegiste ya no está disponible, elige otra')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '¿Cuándo quieres la cita?' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Qué necesitas' })).toHaveValue(DESCRIPCION);
    expect(screen.getByText('Desarrollo de tienda en línea', { selector: 'dd' })).toBeInTheDocument();
    // El día y la hora se borran y la disponibilidad se pide otra vez
    expect(screen.getByText('Elige un día')).toBeInTheDocument();
    await waitFor(() => expect(consultasDisponibilidad(fetchSimulado).length).toBeGreaterThanOrEqual(2));
  });

  it('si ocultaron el servicio (404), vuelve al paso 1', async () => {
    api({ 'POST /citas': { estado: 404, cuerpo: errores({ mensaje: 'Este servicio ya no está disponible' }) } });
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    await completarPaso2();
    await userEvent.click(screen.getByRole('button', { name: /Continuar/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar cita' }));

    expect(await screen.findByRole('heading', { name: '¿Qué servicio necesitas?' })).toBeInTheDocument();
    expect(screen.getByText('Este servicio ya no está disponible')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Desarrollo de tienda en línea/ })).not.toBeChecked();
  });

  it('un cliente en España ve las horas en su zona con la de Colombia, y al cambiar de país se borran el día y la hora', async () => {
    const fetchSimulado = api({ 'GET /citas/disponibilidad': { estado: 200, cuerpo: MARZO_MADRID } });
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();

    await userEvent.selectOptions(screen.getByLabelText('País donde vives'), 'España');
    expect(screen.getByLabelText('Zona horaria')).toHaveValue('Europe/Madrid');
    await waitFor(() => expect(consultasDisponibilidad(fetchSimulado).at(-1)).toContain('zona=Europe%2FMadrid'));

    await userEvent.click(await screen.findByRole('button', { name: /Jueves 6 de marzo, 2 horarios libres/ }));
    const hora = screen.getByRole('radio', { name: /3:00 p\. m\., 9:00 a\. m\. en Colombia/ });
    expect(within(hora.closest('label') as HTMLElement).getByText('9:00 a. m. COL')).toBeInTheDocument();
    await userEvent.click(hora);
    expect(screen.getByText('En Colombia: 9:00 a. m.', { exact: false })).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('País donde vives'), 'Colombia');
    expect(screen.queryByLabelText('Zona horaria')).not.toBeInTheDocument();
    expect(screen.getByText('Elige un día')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Jueves 6 de marzo/ })).toHaveAttribute('aria-pressed', 'false');
  });

  it('avanza al mes siguiente pidiendo sus horarios', async () => {
    const fetchSimulado = api();
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    await screen.findByRole('button', { name: /Jueves 6 de marzo/ });
    await userEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));
    expect(await screen.findByText('Abril 2031')).toBeInTheDocument();
    await waitFor(() => expect(consultasDisponibilidad(fetchSimulado).at(-1)).toContain('mes=2031-04'));
    expect(screen.getByRole('button', { name: 'Mes anterior' })).toBeEnabled();
  });

  it('si no se pueden cargar los horarios, ofrece reintentar', async () => {
    api({ 'GET /citas/disponibilidad': { estado: 500 } });
    abrirEn('/cliente/agendar');
    await elegirServicioYContinuar();
    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
