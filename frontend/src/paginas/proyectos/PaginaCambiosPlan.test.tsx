import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Cambio, CambiosDelProyecto, Foto } from '../../proyectos/cambiosPlan';
import { ANDRES, LAURA, PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { DETALLE, DETALLE_DEL_EQUIPO, FASE_1, FASE_2, paso } from '../../pruebas/seguimiento';
import type { DetalleProyecto } from '../../seguimiento/api';

/**
 * Los cambios de un plan aprobado en las pantallas (HU-30, RN-30, D-32): el Product Owner propone, el administrador autoriza y el
 * cliente acepta. Cada respuesta que el servidor rechaza con 409 porque el plan o el cambio ya cambiaron muestra el mensaje y
 * vuelve a pedir lo último.
 */

type Extra = NonNullable<Parameters<typeof simularApi>[1]>;

const MOTIVO = 'El cliente necesita una fase de capacitación antes de lanzar su tienda.';
const RECARGA = 'El plan cambió desde que se propuso este cambio: pídele al equipo que lo proponga de nuevo';

const FASE_3 = {
  ...FASE_2, id: 'f-3', orden: 3, nombre: 'Pruebas', estado: 'PENDIENTE' as const, iniciadaAt: null, pasosHechos: 0, pasosTotal: 2,
  pasos: [paso('s-7', 1, 'Pruebas de pagos'), paso('s-8', 2, 'Pruebas de pedidos')],
};
const DETALLE_PLAN: DetalleProyecto = { ...DETALLE_DEL_EQUIPO, fases: [FASE_1, FASE_2, FASE_3] };

const fase = (nombre: string, porcentaje: number, estado = 'PENDIENTE') => ({ nombre, porcentaje, estado });
const ANTES: Foto = {
  fases: [fase('Toma de requerimientos', 33.3, 'APROBADA'), fase('Desarrollo', 33.3, 'EN_CURSO'), fase('Pruebas', 33.3)],
  avance: 33.33, entrega: '2026-12-18', valorTotal: 7_800_000,
};
const DESPUES: Foto = {
  fases: [fase('Toma de requerimientos', 25, 'APROBADA'), fase('Desarrollo', 25, 'EN_CURSO'), fase('Pruebas', 25), fase('Capacitación', 25)],
  avance: 25, entrega: '2027-01-15', valorTotal: 9_300_000,
};

const CAMBIO: Cambio = {
  id: 'c-1', proyectoCodigo: 'P-0031', proyecto: 'Tienda en línea Laura', cliente: 'Laura Gómez', tipo: 'AGREGAR_FASE', estado: 'PROPUESTO',
  motivo: MOTIVO, propuestoPor: 'Andrés Pérez', creadoAt: '2026-11-20T15:00:00Z', autorizadoAt: null, respondidoAt: null, motivoRechazo: null,
  faseNombre: 'Capacitación', posicion: 4, apruebaCliente: true, pasosNuevos: [{ titulo: 'Taller de uso', explicacion: null }],
  pasoTitulo: null, pasoFase: null, tituloPaso: null, explicacionPaso: null, solicitud: null, nuevaEntrega: '2027-01-15', valorAdicional: 1_500_000,
  antes: ANTES, despues: DESPUES, seAplicaAlAutorizar: false, puedeAutorizar: true, puedeAceptar: false,
};

const SIN_CAMBIOS: CambiosDelProyecto = {
  puedeProponer: true, hayCambioAbierto: false, cambios: [], valorCotizado: 7_800_000, valorAdicionalAceptado: 0, valorTotal: 7_800_000,
  alcancesPorCotizar: [{ id: 'a-1', fase: 'Desarrollo', descripcion: 'Quiero que los clientes puedan pagar contra entrega en Cúcuta.', creadaAt: '2026-11-18T15:00:00Z' }],
};

const cargas = (f: ReturnType<typeof simularApi>, ruta: string) => f.mock.calls.filter(([url, op]) => url === `/api/v1${ruta}` && (op?.method ?? 'GET') === 'GET').length;

// ------------------------------------------------------------------------------------------- Product Owner

function abrirEquipo(cambios: CambiosDelProyecto, extra: Extra = {}) {
  const f = simularApi(ANDRES, {
    'GET /proyectos/P-0031': { estado: 200, cuerpo: DETALLE_PLAN },
    'GET /proyectos/P-0031/cambios-plan': { estado: 200, cuerpo: cambios },
    ...extra,
  });
  abrirEn('/equipo/proyectos/P-0031/registrar');
  return f;
}

async function abrirDialogo() {
  await userEvent.click(await screen.findByRole('button', { name: 'Proponer cambio de plan' }));
  return screen.findByRole('dialog', { name: 'Proponer cambio de plan' });
}

describe('El Product Owner propone un cambio de plan (HU-30)', () => {
  it('sin elegir el tipo no envía nada, y al elegirlo pide los datos de ese tipo', async () => {
    const f = abrirEquipo(SIN_CAMBIOS);
    const dialogo = await abrirDialogo();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));
    expect(await within(dialogo).findByText('Elige el tipo de cambio.')).toBeInTheDocument();
    expect(within(dialogo).queryByLabelText('Motivo')).not.toBeInTheDocument();

    await userEvent.click(within(dialogo).getByRole('radio', { name: /^Agregar una fase/ }));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));
    expect(await within(dialogo).findByText('Escribe el nombre de la fase (al menos 3 caracteres).')).toBeInTheDocument();
    expect(within(dialogo).getByText('Elige dónde va la fase.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Agrega al menos un paso.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Cuéntale al administrador por qué hace falta este cambio.')).toBeInTheDocument();
    expect(f.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('agregar una fase: manda el nombre, la posición, los pasos, el motivo, la entrega y el valor', async () => {
    const f = abrirEquipo(SIN_CAMBIOS, {
      'POST /proyectos/P-0031/cambios-plan': { estado: 201, cuerpo: { cambioId: 'c-1', estado: 'PROPUESTO', fasesAntes: 3, fasesDespues: 4, avanceDespues: 25 } },
    });
    const dialogo = await abrirDialogo();
    await userEvent.click(within(dialogo).getByRole('radio', { name: /^Agregar una fase/ }));
    await userEvent.type(within(dialogo).getByLabelText('Nombre de la fase nueva'), 'Capacitación');
    // La fase en curso es la 2: la nueva va desde la posición 3
    const posicion = within(dialogo).getByLabelText('¿Dónde va?');
    expect(within(posicion).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Elige la posición', 'Posición 3, antes de «Pruebas»', 'Posición 4, al final',
    ]);
    await userEvent.selectOptions(posicion, '4');
    await userEvent.click(within(dialogo).getByRole('checkbox', { name: /La aprueba el cliente/ }));
    await userEvent.type(within(dialogo).getByLabelText('Pasos (uno por línea)'), 'Taller de uso{enter}Entrega de manuales');
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), MOTIVO);
    fireEvent.change(within(dialogo).getByLabelText('Nueva fecha de entrega'), { target: { value: '2027-01-15' } });
    await userEvent.clear(within(dialogo).getByLabelText('Valor adicional (COP)'));
    await userEvent.type(within(dialogo).getByLabelText('Valor adicional (COP)'), '1500000');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));

    expect(await screen.findByText(/Cambio enviado al administrador/)).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'POST', '/proyectos/P-0031/cambios-plan')).toEqual({
      tipo: 'AGREGAR_FASE', motivo: MOTIVO, nuevaEntrega: '2027-01-15', valorAdicional: 1_500_000, nombreFase: 'Capacitación', posicion: 4,
      apruebaCliente: true, pasos: [{ titulo: 'Taller de uso', explicacion: null }, { titulo: 'Entrega de manuales', explicacion: null }],
    });
  });

  it('quitar un paso solo ofrece los que no han empezado y no pide fecha ni valor', async () => {
    const f = abrirEquipo(SIN_CAMBIOS, { 'POST /proyectos/P-0031/cambios-plan': { estado: 201, cuerpo: { cambioId: 'c-2', estado: 'PROPUESTO', fasesAntes: 3, fasesDespues: 3, avanceDespues: 33.33 } } });
    const dialogo = await abrirDialogo();
    await userEvent.click(within(dialogo).getByRole('radio', { name: /^Quitar un paso pendiente/ }));
    const select = within(dialogo).getByLabelText('Paso que se quita');
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Elige un paso pendiente', 'Desarrollo · Pedidos y correos de confirmación', 'Desarrollo · Pago contra entrega',
      'Pruebas · Pruebas de pagos', 'Pruebas · Pruebas de pedidos',
    ]);
    expect(within(dialogo).queryByLabelText('Nueva fecha de entrega')).not.toBeInTheDocument();
    expect(within(dialogo).queryByLabelText('Valor adicional (COP)')).not.toBeInTheDocument();

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));
    expect(await within(dialogo).findByText('Elige el paso que quieres quitar.')).toBeInTheDocument();
    await userEvent.selectOptions(select, 's-3');
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'El cliente ya no necesita los correos de confirmación.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));

    await waitFor(() => expect(cuerpoEnviado(f, 'POST', '/proyectos/P-0031/cambios-plan'))
      .toEqual({ tipo: 'QUITAR_PASO', motivo: 'El cliente ya no necesita los correos de confirmación.', pasoId: 's-3' }));
  });

  it('cambiar la fecha exige una fecha distinta a la actual', async () => {
    abrirEquipo(SIN_CAMBIOS);
    const dialogo = await abrirDialogo();
    await userEvent.click(within(dialogo).getByRole('radio', { name: /^Cambiar solo la fecha/ }));
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), MOTIVO);
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));
    expect(await within(dialogo).findByText('Elige una fecha distinta a la entrega actual')).toBeInTheDocument();
  });

  it('cotizar un trabajo nuevo ofrece las solicitudes del cliente por cotizar', async () => {
    const f = abrirEquipo(SIN_CAMBIOS, { 'POST /proyectos/P-0031/cambios-plan': { estado: 201, cuerpo: { cambioId: 'c-3', estado: 'PROPUESTO', fasesAntes: 3, fasesDespues: 3, avanceDespues: 33.33 } } });
    const dialogo = await abrirDialogo();
    await userEvent.click(within(dialogo).getByRole('radio', { name: /^Cotizar un trabajo nuevo/ }));
    await userEvent.selectOptions(within(dialogo).getByLabelText('Lo que pidió el cliente'), 'a-1');
    await userEvent.type(within(dialogo).getByLabelText('Título del paso nuevo'), 'Pago contra entrega');
    await userEvent.type(within(dialogo).getByLabelText('Qué se hará'), 'Se agrega el pago contra entrega para clientes de Cúcuta.');
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'El cliente pidió un trabajo que no estaba en el plan inicial.');
    fireEvent.change(within(dialogo).getByLabelText('Nueva fecha de entrega'), { target: { value: '2027-01-02' } });
    await userEvent.clear(within(dialogo).getByLabelText('Valor adicional (COP)'));
    await userEvent.type(within(dialogo).getByLabelText('Valor adicional (COP)'), '900000');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));

    await waitFor(() => expect(cuerpoEnviado(f, 'POST', '/proyectos/P-0031/cambios-plan')).toEqual({
      tipo: 'AGREGAR_ALCANCE', motivo: 'El cliente pidió un trabajo que no estaba en el plan inicial.', nuevaEntrega: '2027-01-02', valorAdicional: 900_000,
      solicitudCambioId: 'a-1', tituloPaso: 'Pago contra entrega', explicacionPaso: 'Se agrega el pago contra entrega para clientes de Cúcuta.',
    }));
  });

  it('si ya hay un cambio abierto (409) muestra el mensaje y vuelve a pedir la lista', async () => {
    const f = abrirEquipo({ ...SIN_CAMBIOS, alcancesPorCotizar: [] }, {
      'POST /proyectos/P-0031/cambios-plan': { estado: 409, cuerpo: errores({ mensaje: 'Este proyecto ya tiene un cambio de plan esperando respuesta' }) },
    });
    const dialogo = await abrirDialogo();
    await userEvent.click(within(dialogo).getByRole('radio', { name: /^Quitar una fase pendiente/ }));
    await userEvent.selectOptions(within(dialogo).getByLabelText('Fase que se quita'), 'f-3');
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'El cliente decidió no hacer pruebas con usuarios por ahora.');
    fireEvent.change(within(dialogo).getByLabelText('Nueva fecha de entrega'), { target: { value: '2026-12-10' } });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar al administrador' }));

    expect(await screen.findByText('Este proyecto ya tiene un cambio de plan esperando respuesta')).toBeInTheDocument();
    await waitFor(() => expect(cargas(f, '/proyectos/P-0031/cambios-plan')).toBe(2));
  });

  it('con un cambio abierto no deja proponer otro y lo muestra con su estado', async () => {
    abrirEquipo({ ...SIN_CAMBIOS, puedeProponer: false, hayCambioAbierto: true, cambios: [{ ...CAMBIO, antes: null, despues: null }] });
    expect(await screen.findByText('Por autorizar')).toBeInTheDocument();
    expect(screen.getByText(/Ya hay un cambio esperando respuesta/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Proponer cambio de plan' })).not.toBeInTheDocument();
  });
});

// ------------------------------------------------------------------------------------------- Administrador

function abrirAdmin(lista: Cambio[], extra: Extra = {}) {
  const f = simularApi(PATRICIA, { 'GET /cambios-plan': { estado: 200, cuerpo: lista }, ...extra });
  abrirEn('/admin/proyectos');
  return f;
}

describe('El administrador autoriza o rechaza los cambios de plan (HU-30)', () => {
  it('muestra cada cambio con el plan antes y después', async () => {
    abrirAdmin([CAMBIO]);
    const seccion = (await screen.findByRole('heading', { name: /Cambios de plan por autorizar/ })).closest('section')!;
    expect(within(seccion).getByText('Tienda en línea Laura')).toBeInTheDocument();
    expect(within(seccion).getByText('Agregar una fase')).toBeInTheDocument();
    expect(within(seccion).getByText(/Se agrega la fase «Capacitación» en la posición 4: la aprueba el cliente/)).toBeInTheDocument();
    const comparacion = within(seccion).getByRole('group', { name: 'El plan antes y después del cambio' });
    expect(within(comparacion).getByText('Hoy')).toBeInTheDocument();
    expect(within(comparacion).getByText('Con el cambio')).toBeInTheDocument();
    expect(within(comparacion).getByText('3 fases')).toBeInTheDocument();
    expect(within(comparacion).getByText('4 fases')).toBeInTheDocument();
    expect(within(comparacion).getByText(/nueva/)).toBeInTheDocument();
  });

  it('sin cambios por autorizar no muestra la sección', async () => {
    abrirAdmin([]);
    await waitFor(() => expect(screen.queryByRole('heading', { name: /Cambios de plan por autorizar/ })).not.toBeInTheDocument());
  });

  it('autoriza y envía al cliente, y vuelve a pedir la lista', async () => {
    let autorizado = false;
    const f = abrirAdmin([], {
      'GET /cambios-plan': () => ({ estado: 200, cuerpo: autorizado ? [] : [CAMBIO] }),
      'PATCH /cambios-plan/c-1/autorizar': () => { autorizado = true; return { estado: 200, cuerpo: { cambioId: 'c-1', estado: 'AUTORIZADO' } }; },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Autorizar y enviar' }));
    const dialogo = await screen.findByRole('dialog', { name: '¿Autorizar y enviar al cliente?' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, autorizar' }));

    expect(await screen.findByText('Cambio autorizado. Laura Gómez lo recibe para aceptarlo o no.')).toBeInTheDocument();
    await waitFor(() => expect(cargas(f, '/cambios-plan')).toBe(2));
    expect(screen.queryByRole('heading', { name: /Cambios de plan por autorizar/ })).not.toBeInTheDocument();
  });

  it('quitar un paso de una fase que cierra el equipo se aplica al autorizar', async () => {
    abrirAdmin([{ ...CAMBIO, tipo: 'QUITAR_PASO', pasoTitulo: 'Pedidos', pasoFase: 'Desarrollo', seAplicaAlAutorizar: true, antes: null, despues: null, nuevaEntrega: null, valorAdicional: 0 }], {
      'PATCH /cambios-plan/c-1/autorizar': { estado: 200, cuerpo: { cambioId: 'c-1', estado: 'ACEPTADO' } },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Autorizar y aplicar' }));
    const dialogo = await screen.findByRole('dialog', { name: '¿Autorizar y aplicar el cambio?' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, autorizar' }));
    expect(await screen.findByText('Paso quitado. El cliente recibe un aviso; el resto del plan sigue igual.')).toBeInTheDocument();
  });

  it('no autorizar pide un motivo de al menos 10 caracteres y lo manda', async () => {
    const f = abrirAdmin([CAMBIO], { 'PATCH /cambios-plan/c-1/rechazar': { estado: 200, cuerpo: { cambioId: 'c-1', estado: 'RECHAZADO_ADMIN' } } });
    await userEvent.click(await screen.findByRole('button', { name: 'No autorizar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'No autorizar el cambio' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'No autorizar' }));
    expect(await within(dialogo).findByText('Escribe el motivo: el equipo lo verá.')).toBeInTheDocument();
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'Corto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'No autorizar' }));
    expect(await within(dialogo).findByText('Escribe al menos 10 caracteres.')).toBeInTheDocument();

    await userEvent.type(within(dialogo).getByLabelText('Motivo'), ' y ahora sí alcanza.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'No autorizar' }));
    expect(await screen.findByText(/Cambio no autorizado/)).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'PATCH', '/cambios-plan/c-1/rechazar')).toEqual({ motivo: 'Corto y ahora sí alcanza.' });
  });

  it('si otra persona ya lo respondió (409) muestra el mensaje y recarga', async () => {
    const f = abrirAdmin([CAMBIO], { 'PATCH /cambios-plan/c-1/autorizar': { estado: 409, cuerpo: errores({ mensaje: 'Este cambio de plan ya fue respondido' }) } });
    await userEvent.click(await screen.findByRole('button', { name: 'Autorizar y enviar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, autorizar' }));
    expect(await screen.findByText('Este cambio de plan ya fue respondido')).toBeInTheDocument();
    await waitFor(() => expect(cargas(f, '/cambios-plan')).toBe(2));
  });
});

// ------------------------------------------------------------------------------------------- Cliente

const AUTORIZADO: Cambio = { ...CAMBIO, estado: 'AUTORIZADO', autorizadoAt: '2026-11-21T15:00:00Z', puedeAutorizar: false, puedeAceptar: true };

function abrirCliente(cambios: Cambio[], extra: Extra = {}, adicional = 0) {
  const f = simularApi(LAURA, {
    'GET /proyectos/P-0031': { estado: 200, cuerpo: DETALLE },
    'GET /proyectos/P-0031/cambios-plan': {
      estado: 200, cuerpo: { ...SIN_CAMBIOS, puedeProponer: false, alcancesPorCotizar: [], cambios, valorAdicionalAceptado: adicional, valorTotal: 7_800_000 + adicional },
    },
    ...extra,
  });
  abrirEn('/cliente/proyectos/P-0031');
  return f;
}

describe('El cliente acepta o no un cambio del plan (HU-30)', () => {
  it('sin cambios no muestra la tarjeta', async () => {
    abrirCliente([]);
    await screen.findByText('Fases del proyecto');
    expect(screen.queryByRole('heading', { name: 'Cambios en tu plan' })).not.toBeInTheDocument();
  });

  it('muestra el cambio autorizado con el antes y el después, y acepta: se aplica y se vuelve a pedir el proyecto', async () => {
    const f = abrirCliente([AUTORIZADO], {
      'PATCH /cambios-plan/c-1/aceptar': { estado: 200, cuerpo: { estado: 'ACEPTADO', fases: 4, avancePorcentaje: 25, fechaEstimadaEntrega: '2027-01-15', valorTotal: 9_300_000 } },
    });
    const tarjeta = (await screen.findByRole('heading', { name: 'Cambios en tu plan' })).closest('section')!;
    expect(within(tarjeta).getByText(/la administración ya lo autorizó/)).toBeInTheDocument();
    expect(within(tarjeta).getByText('4 fases')).toBeInTheDocument();
    await userEvent.click(within(tarjeta).getByRole('button', { name: 'Aceptar cambio' }));
    const dialogo = await screen.findByRole('dialog', { name: '¿Aceptar este cambio?' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, aceptar' }));

    expect(await screen.findByText(/Cambio aceptado\. Tu plan ya está actualizado/)).toBeInTheDocument();
    await waitFor(() => expect(cargas(f, '/proyectos/P-0031')).toBe(2));
  });

  it('no aceptar pide un motivo y lo manda; el plan no se recarga', async () => {
    const f = abrirCliente([AUTORIZADO], { 'PATCH /cambios-plan/c-1/rechazar': { estado: 200, cuerpo: { cambioId: 'c-1', estado: 'RECHAZADO_CLIENTE' } } });
    await userEvent.click(await screen.findByRole('button', { name: 'No aceptar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'No aceptar el cambio' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'No aceptar' }));
    expect(await within(dialogo).findByText('Escribe el motivo: el equipo lo verá.')).toBeInTheDocument();
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'Prefiero esperar al próximo trimestre.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'No aceptar' }));

    expect(await screen.findByText('Cambio no aceptado. Tu plan sigue igual.')).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'PATCH', '/cambios-plan/c-1/rechazar')).toEqual({ motivo: 'Prefiero esperar al próximo trimestre.' });
    expect(cargas(f, '/proyectos/P-0031')).toBe(1);
  });

  it('si el plan cambió mientras tanto (409) muestra el mensaje y vuelve a pedir el proyecto', async () => {
    const f = abrirCliente([AUTORIZADO], { 'PATCH /cambios-plan/c-1/aceptar': { estado: 409, cuerpo: errores({ mensaje: RECARGA }) } });
    await userEvent.click(await screen.findByRole('button', { name: 'Aceptar cambio' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, aceptar' }));
    expect(await screen.findByText(RECARGA)).toBeInTheDocument();
    await waitFor(() => expect(cargas(f, '/proyectos/P-0031')).toBe(2));
  });

  it('los cambios ya respondidos quedan en el historial y se ve el valor total con los adicionales', async () => {
    abrirCliente([{ ...CAMBIO, estado: 'ACEPTADO', antes: null, despues: null, puedeAceptar: false, puedeAutorizar: false }], {}, 1_500_000);
    const tarjeta = (await screen.findByRole('heading', { name: 'Cambios en tu plan' })).closest('section')!;
    expect(within(tarjeta).getByText('Cambios anteriores (1)')).toBeInTheDocument();
    expect(within(tarjeta).getByText('Aplicado')).toBeInTheDocument();
    expect(within(tarjeta).getByText(/Valor total del proyecto/)).toBeInTheDocument();
    expect(within(tarjeta).queryByRole('button', { name: 'Aceptar cambio' })).not.toBeInTheDocument();
  });
});
