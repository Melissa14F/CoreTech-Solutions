import { act, cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Bandeja } from '../comunicacion/notificaciones';
import { ANDRES, LAURA, PATRICIA, abrirEn, simularApi } from '../pruebas/aplicacion';
import { REFRESCO_MS } from './Campana';

const hace = (minutos: number) => new Date(Date.now() - minutos * 60_000).toISOString();

const BANDEJA: Bandeja = {
  noLeidas: 2,
  notificaciones: [
    { notificacionId: 'n1', tipo: 'CITA_CONFIRMADA', titulo: 'Tu cita fue confirmada', mensaje: 'La cita C-0064 quedó para el lunes.', enlace: '/cliente/citas', leida: false, fecha: hace(5) },
    { notificacionId: 'n2', tipo: 'NUEVO_MENSAJE', titulo: 'Nuevo mensaje', mensaje: 'Andrés te escribió.', enlace: null, leida: false, fecha: hace(30) },
    { notificacionId: 'n3', tipo: 'PASO_HECHO', titulo: 'Nuevo avance', mensaje: 'Tu equipo terminó un paso.', enlace: null, leida: true, fecha: hace(90) },
  ],
};

const llamadas = (fetchSimulado: ReturnType<typeof simularApi>, metodo: string, ruta: string) =>
  fetchSimulado.mock.calls.filter(([url, op]) => url === `/api/v1${ruta}` && (op?.method ?? 'GET') === metodo).length;

describe('campana de notificaciones (HU-24)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('muestra el punto y cuántas hay sin leer en los tres paneles', async () => {
    for (const [usuario, ruta] of [[LAURA, '/cliente/proyectos'], [ANDRES, '/equipo/proyectos'], [PATRICIA, '/admin']] as const) {
      simularApi(usuario, { 'GET /notificaciones': { estado: 200, cuerpo: BANDEJA } });
      abrirEn(ruta);
      const boton = await screen.findByRole('button', { name: 'Notificaciones: 2 nuevas' });
      expect(boton.querySelector('.campana__punto')).not.toBeNull();
      cleanup();
    }
  });

  it('sin avisos nuevos no muestra el punto', async () => {
    simularApi(LAURA, { 'GET /notificaciones': { estado: 200, cuerpo: { noLeidas: 0, notificaciones: [] } } });
    abrirEn('/cliente/proyectos');
    const boton = await screen.findByRole('button', { name: 'Notificaciones: no tienes avisos nuevos' });
    expect(boton.querySelector('.campana__punto')).toBeNull();
    await userEvent.click(boton);
    expect(await screen.findByText('No tienes notificaciones.')).toBeInTheDocument();
  });

  it('abre la lista con las no leídas primero y la cierra con Escape', async () => {
    simularApi(LAURA, { 'GET /notificaciones': { estado: 200, cuerpo: BANDEJA } });
    abrirEn('/cliente/proyectos');
    const boton = await screen.findByRole('button', { name: 'Notificaciones: 2 nuevas' });

    await userEvent.click(boton);
    const lista = screen.getByRole('region', { name: 'Notificaciones' });
    const avisos = within(lista).getAllByRole('listitem');
    expect(avisos).toHaveLength(3);
    expect(avisos[0]).toHaveTextContent('Nueva: Tu cita fue confirmada');
    expect(avisos[2]).not.toHaveTextContent('Nueva:');
    expect(within(lista).getByText('2 nuevas')).toBeInTheDocument();
    expect(boton).toHaveAttribute('aria-expanded', 'true');

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('region', { name: 'Notificaciones' })).not.toBeInTheDocument();
    expect(boton).toHaveFocus();
  });

  it('abrir un aviso con enlace lo marca como leído y lleva a su pantalla', async () => {
    const fetchSimulado = simularApi(LAURA, {
      'GET /notificaciones': { estado: 200, cuerpo: BANDEJA },
      'PATCH /notificaciones/n1/leida': { estado: 204 },
    });
    const enrutador = abrirEn('/cliente/proyectos');
    await userEvent.click(await screen.findByRole('button', { name: 'Notificaciones: 2 nuevas' }));

    await userEvent.click(screen.getByRole('link', { name: /Tu cita fue confirmada/ }));

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/citas'));
    expect(llamadas(fetchSimulado, 'PATCH', '/notificaciones/n1/leida')).toBe(1);
    expect(screen.getByRole('button', { name: 'Notificaciones: 1 nueva' })).toBeInTheDocument();
  });

  it('un aviso sin enlace se marca como leído al tocarlo', async () => {
    const fetchSimulado = simularApi(LAURA, {
      'GET /notificaciones': { estado: 200, cuerpo: BANDEJA },
      'PATCH /notificaciones/n2/leida': { estado: 204 },
    });
    abrirEn('/cliente/proyectos');
    await userEvent.click(await screen.findByRole('button', { name: 'Notificaciones: 2 nuevas' }));

    await userEvent.click(screen.getByRole('button', { name: /Nuevo mensaje/ }));

    expect(llamadas(fetchSimulado, 'PATCH', '/notificaciones/n2/leida')).toBe(1);
    expect(screen.getByRole('button', { name: 'Notificaciones: 1 nueva' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Nuevo mensaje/ })).not.toBeInTheDocument();
  });

  it('marcar todas como leídas apaga el punto', async () => {
    const fetchSimulado = simularApi(LAURA, {
      'GET /notificaciones': { estado: 200, cuerpo: BANDEJA },
      'PATCH /notificaciones/leidas': { estado: 204 },
    });
    abrirEn('/cliente/proyectos');
    await userEvent.click(await screen.findByRole('button', { name: 'Notificaciones: 2 nuevas' }));

    await userEvent.click(screen.getByRole('button', { name: 'Marcar todas como leídas' }));

    expect(llamadas(fetchSimulado, 'PATCH', '/notificaciones/leidas')).toBe(1);
    const boton = screen.getByRole('button', { name: 'Notificaciones: no tienes avisos nuevos' });
    expect(boton.querySelector('.campana__punto')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Marcar todas como leídas' })).not.toBeInTheDocument();
  });

  it('se refresca sola cada minuto', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let bandeja: Bandeja = { noLeidas: 0, notificaciones: [] };
    const fetchSimulado = simularApi(LAURA, { 'GET /notificaciones': () => ({ estado: 200, cuerpo: bandeja }) });
    abrirEn('/cliente/proyectos');
    await screen.findByRole('button', { name: 'Notificaciones: no tienes avisos nuevos' });
    const antes = llamadas(fetchSimulado, 'GET', '/notificaciones');

    bandeja = BANDEJA;
    await act(() => vi.advanceTimersByTimeAsync(REFRESCO_MS));

    expect(await screen.findByRole('button', { name: 'Notificaciones: 2 nuevas' })).toBeInTheDocument();
    expect(llamadas(fetchSimulado, 'GET', '/notificaciones')).toBe(antes + 1);
  });

  it('si la API falla, la campana lo dice sin romper el panel', async () => {
    simularApi(LAURA, { 'GET /notificaciones': { estado: 500 } });
    abrirEn('/cliente/proyectos');
    await userEvent.click(await screen.findByRole('button', { name: 'Notificaciones: no tienes avisos nuevos' }));
    expect(await screen.findByText(/No pudimos cargar tus notificaciones/)).toBeInTheDocument();
  });
});

