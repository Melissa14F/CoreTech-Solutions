import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ANDRES, LAURA, abrirEn, errores, simularApi } from '../../pruebas/aplicacion';
import { DETALLE, DETALLE_DEL_EQUIPO, FASE_1, FASE_2, paso } from '../../pruebas/seguimiento';
import type { DetalleProyecto } from '../../seguimiento/api';

/**
 * El ciclo de una fase en las pantallas (HU-11, HU-13): aprobar, pedir cambios, entregar o cerrar. Cada acción que el servidor
 * rechaza con 409 porque la fase cambió (otra pestaña, HU-03 «Datos desactualizados») muestra el mensaje y vuelve a pedir el
 * proyecto, sin dejar el diálogo abierto con datos viejos.
 */

const RECARGA = 'El proyecto cambió, recarga para ver lo último';
const PEDIDO = 'Quiero que el botón de pagar sea más grande y de otro color para verlo mejor.';

/** La fase 1 entregada y esperando la respuesta de Laura; la 2 todavía no empieza. */
const EN_REVISION: DetalleProyecto = {
  ...DETALLE,
  avancePorcentaje: 0,
  faseActual: { orden: 1, nombre: FASE_1.nombre },
  fases: [
    { ...FASE_1, estado: 'EN_REVISION', aprobadaAt: null, rondasAjusteUsadas: 0, entregadaAt: '2026-11-20T15:00:00Z', plazoAprobacion: '2026-11-25' },
    { ...FASE_2, estado: 'PENDIENTE', iniciadaAt: null, pasosHechos: 0, pasos: [paso('s-5', 1, 'Catálogo de productos'), paso('s-6', 2, 'Pedidos')] },
  ],
};

/** La fase 2 (la cierra el equipo) con todos sus pasos hechos. */
const LISTA_PARA_CERRAR: DetalleProyecto = {
  ...DETALLE_DEL_EQUIPO,
  fases: [FASE_1, {
    ...FASE_2, pasosHechos: 3,
    pasos: FASE_2.pasos.map((p) => ({ ...p, estado: 'HECHO', nota: 'Quedó listo y funcionando.', hechoAt: '2026-11-20T15:00:00Z' })),
  }],
};

const cargasDelProyecto = (f: ReturnType<typeof simularApi>) =>
  f.mock.calls.filter(([url, op]) => url === '/api/v1/proyectos/P-0031' && (op?.method ?? 'GET') === 'GET').length;

describe('El cliente aprueba una fase o pide cambios (HU-11)', () => {
  async function abrirRevision(extra = {}) {
    let respondido = false;
    const f = simularApi(LAURA, {
      'GET /proyectos/P-0031': () => ({ estado: 200, cuerpo: respondido ? DETALLE : EN_REVISION }),
      ...Object.fromEntries(Object.entries(extra).map(([ruta, r]) => [ruta, () => { respondido = true; return r as never; }])),
    });
    abrirEn('/cliente/proyectos/P-0031');
    return f;
  }

  it('aprueba la fase: avisa cuánto suma el proyecto y vuelve a pedir lo último', async () => {
    const f = await abrirRevision({
      'POST /fases/f-1/aprobar': { estado: 200, cuerpo: { estado: 'APROBADA', avancePorcentaje: 50, estadoProyecto: 'ACTIVO', garantiaHasta: null } },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar fase' }));
    const dialogo = await screen.findByRole('dialog', { name: '¿Aprobar Toma de requerimientos?' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, aprobar' }));

    expect(await screen.findByText('Toma de requerimientos aprobada. Tu proyecto va en 50 %.')).toBeInTheDocument();
    await waitFor(() => expect(cargasDelProyecto(f)).toBe(2));
  });

  it('si la fase cambió mientras tanto (409) muestra el mensaje, cierra el diálogo y recarga', async () => {
    const f = await abrirRevision({
      'POST /fases/f-1/aprobar': { estado: 409, cuerpo: errores({ mensaje: RECARGA }) },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar fase' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, aprobar' }));

    expect(await screen.findByText(RECARGA)).toBeInTheDocument();
    await waitFor(() => expect(cargasDelProyecto(f)).toBe(2));
    expect(screen.queryByRole('button', { name: 'Aprobar fase' })).not.toBeInTheDocument();
  });

  it('pedir cambios exige el tipo y una descripción de al menos 20 caracteres, y no envía nada si falla', async () => {
    const f = await abrirRevision();
    await userEvent.click(await screen.findByRole('button', { name: 'Pedir cambios' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(await screen.findByText('Elige el tipo de cambio.')).toBeInTheDocument();
    expect(screen.getByText('Describe qué quieres cambiar.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: /Ajuste a lo entregado/ }));
    await userEvent.type(screen.getByLabelText('Describe el cambio'), 'Muy corto');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(await screen.findByText(/Escribe al menos 20 caracteres/)).toBeInTheDocument();
    expect(f.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('envía un ajuste y avisa que el desarrollador (Product Owner) lo recibió', async () => {
    const f = await abrirRevision({
      'POST /fases/f-1/solicitudes-cambio': {
        estado: 201, cuerpo: { solicitudId: 'c-1', tipo: 'AJUSTE', estado: 'ABIERTO', rondasUsadas: 1, estadoFase: 'EN_CURSO', pasoId: 's-9' },
      },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Pedir cambios' }));
    await userEvent.click(screen.getByRole('radio', { name: /Ajuste a lo entregado/ }));
    await userEvent.type(screen.getByLabelText('Describe el cambio'), PEDIDO);
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText(/Ajuste enviado a Andrés Pérez/)).toBeInTheDocument();
    const envio = f.mock.calls.find(([url]) => url === '/api/v1/fases/f-1/solicitudes-cambio')!;
    expect(JSON.parse(envio[1]!.body as string)).toEqual({ tipo: 'AJUSTE', descripcion: PEDIDO });
  });

  it('si la fase cambió mientras tanto (409) al pedir cambios muestra el mensaje y recarga', async () => {
    const f = await abrirRevision({
      'POST /fases/f-1/solicitudes-cambio': { estado: 409, cuerpo: errores({ mensaje: RECARGA }) },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Pedir cambios' }));
    await userEvent.click(screen.getByRole('radio', { name: /Algo nuevo/ }));
    await userEvent.type(screen.getByLabelText('Describe el cambio'), PEDIDO);
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText(RECARGA)).toBeInTheDocument();
    await waitFor(() => expect(cargasDelProyecto(f)).toBe(2));
  });
});

describe('El desarrollador (Product Owner) entrega o cierra una fase (HU-13)', () => {
  function abrirEquipo(extra = {}) {
    let entregado = false;
    const f = simularApi(ANDRES, {
      'GET /proyectos/P-0031': () => ({ estado: 200, cuerpo: entregado ? DETALLE_DEL_EQUIPO : LISTA_PARA_CERRAR }),
      ...Object.fromEntries(Object.entries(extra).map(([ruta, r]) => [ruta, () => { entregado = true; return r as never; }])),
    });
    abrirEn('/equipo/proyectos/P-0031/registrar');
    return f;
  }

  it('cierra la última fase: el proyecto queda terminado y en garantía', async () => {
    const f = abrirEquipo({
      'POST /fases/f-2/entregar': {
        estado: 200, cuerpo: { estado: 'APROBADA', plazoAprobacion: null, avancePorcentaje: 100, estadoProyecto: 'EN_GARANTIA', garantiaHasta: '2026-12-21' },
      },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar fase' }));
    const dialogo = await screen.findByRole('dialog', { name: '¿Cerrar Desarrollo?' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, cerrar' }));

    expect(await screen.findByText('Proyecto terminado. Laura recibirá un aviso. Queda en garantía 30 días.')).toBeInTheDocument();
    await waitFor(() => expect(cargasDelProyecto(f)).toBe(2));
  });

  it('si la fase cambió mientras tanto (409) muestra el mensaje y recarga lo último', async () => {
    const f = abrirEquipo({
      'POST /fases/f-2/entregar': { estado: 409, cuerpo: errores({ mensaje: RECARGA }) },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar fase' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, cerrar' }));

    expect(await screen.findByText(RECARGA)).toBeInTheDocument();
    await waitFor(() => expect(cargasDelProyecto(f)).toBe(2));
  });

  it('con un paso por hacer no se puede entregar y dice cuántos faltan', async () => {
    simularApi(ANDRES, { 'GET /proyectos/P-0031': { estado: 200, cuerpo: DETALLE_DEL_EQUIPO } });
    abrirEn('/equipo/proyectos/P-0031/registrar');
    expect(await screen.findByText('Faltan 2 pasos por terminar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cerrar fase' })).toBeDisabled();
  });
});
