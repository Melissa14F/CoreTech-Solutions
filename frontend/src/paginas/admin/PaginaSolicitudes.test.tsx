import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { GestionSolicitudes } from '../../componentes/GestionSolicitudes';
import { ProveedorNotificaciones } from '../../componentes/Notificaciones';
import type { Solicitud } from '../../comunicacion/reuniones';
import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';

type Extra = NonNullable<Parameters<typeof simularApi>[1]>;

/**
 * Solicitudes de reunión de seguimiento (HU-16, D-42): el administrador programa, rechaza y cancela; el Product Owner, después
 * de la reunión, llena el resumen. Un 409 (el horario ya no está o la solicitud ya cambió) muestra el mensaje y recarga la lista.
 */

const PENDIENTE: Solicitud = {
  id: 's-8', codigo: 'S-0008', proyectoCodigo: 'P-0031', proyecto: 'Tienda en línea Laura', cliente: 'Laura Gómez', responsable: 'Andrés Pérez',
  fase: 'Desarrollo', motivo: 'Revisar el avance', razon: 'Quiero revisar el flujo de pago antes de que pase a Pruebas.', modalidad: 'VIRTUAL',
  opciones: ['2026-11-19T15:00:00Z', '2026-11-20T20:00:00Z'], estado: 'PENDIENTE', fechaProgramada: null, enlace: null, motivoRechazo: null,
  enviadaAt: '2026-11-18T21:12:00Z', resumen: null, puedeCerrar: false,
};
const PROGRAMADA: Solicitud = {
  ...PENDIENTE, id: 's-6', codigo: 'S-0006', motivo: 'Duda técnica', estado: 'PROGRAMADA', fechaProgramada: '2026-11-12T15:00:00Z', enlace: 'https://meet.google.com/abc',
};
const ATENDIDA: Solicitud = {
  ...PENDIENTE, id: 's-5', codigo: 'S-0005', cliente: 'Carlos Díaz', responsable: 'María Ruiz', estado: 'ATENDIDA', fechaProgramada: '2026-11-05T15:00:00Z',
  resumen: { resumen: 'Revisamos el lector de código de barras y quedó aprobado.', quePidio: 'Que el lector funcione también con códigos dañados.', siguientePaso: 'DENTRO_DEL_PLAN', registradoAt: '2026-11-05T17:00:00Z' },
};
const RECHAZADA: Solicitud = { ...PENDIENTE, id: 's-4', codigo: 'S-0004', estado: 'RECHAZADA', motivoRechazo: 'La duda se resolvió por correo.' };
const TODAS = [PENDIENTE, PROGRAMADA, ATENDIDA, RECHAZADA];

const RECARGA = 'Ese horario ya no está disponible, propón otro o rechaza';

function abrirAdmin(extra: Extra = {}, lista: Solicitud[] = TODAS) {
  const f = simularApi(PATRICIA, { 'GET /solicitudes-reunion': { estado: 200, cuerpo: lista }, ...extra });
  abrirEn('/admin/solicitudes');
  return f;
}

const fila = async (codigo: string) => (await screen.findAllByText(codigo)).map((e) => e.closest('tr')!).find(Boolean)!;

describe('Solicitudes de reunión del administrador (HU-16)', () => {
  it('lista las solicitudes con su estado y filtra por estado y por búsqueda', async () => {
    abrirAdmin();
    expect(await screen.findByRole('heading', { name: 'Solicitudes de reunión' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /^Todas 4$/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Por responder 1$/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Rechazadas 1$/ })).toBeInTheDocument();
    expect(screen.getByText('4 resultados')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /^Atendidas/ }));
    expect(screen.getByText('S-0005')).toBeInTheDocument();
    expect(screen.queryByText('S-0008')).not.toBeInTheDocument();
    expect(screen.getByText('1 resultado')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /^Todas/ }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar solicitud' }), 'Carlos');
    expect(screen.getByText('S-0005')).toBeInTheDocument();
    expect(screen.queryByText('S-0006')).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar solicitud' }), ' inexistente');
    expect(await screen.findByText('No hay solicitudes con estos filtros')).toBeInTheDocument();
  });

  it('programar exige uno de los horarios y el enlace, y manda la opción elegida', async () => {
    const f = abrirAdmin({ 'PATCH /solicitudes-reunion/s-8': { estado: 200, cuerpo: { ...PENDIENTE, estado: 'PROGRAMADA', fechaProgramada: '2026-11-20T20:00:00Z' } } });
    await userEvent.click(await within(await fila('S-0008')).findByRole('button', { name: 'Programar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Programar reunión' });

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Programar reunión' }));
    expect(await within(dialogo).findByText('Elige uno de los horarios propuestos.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Pega el enlace: la reunión es virtual.')).toBeInTheDocument();

    await userEvent.click(within(dialogo).getAllByRole('radio')[1]!);
    await userEvent.type(within(dialogo).getByLabelText('Enlace de la videollamada'), 'meet.google.com/sin-https');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Programar reunión' }));
    expect(await within(dialogo).findByText('El enlace debe empezar por https://')).toBeInTheDocument();
    expect(f.mock.calls.some(([, op]) => op?.method === 'PATCH')).toBe(false);

    const enlace = within(dialogo).getByLabelText('Enlace de la videollamada');
    await userEvent.clear(enlace);
    await userEvent.type(enlace, 'https://meet.google.com/abc-defg-hij');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Programar reunión' }));

    expect(await screen.findByText(/Reunión programada para el/)).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'PATCH', '/solicitudes-reunion/s-8')).toEqual({ accion: 'PROGRAMAR', opcion: 2, enlace: 'https://meet.google.com/abc-defg-hij' });
  });

  it('una reunión presencial no pide enlace', async () => {
    const f = abrirAdmin({ 'PATCH /solicitudes-reunion/s-8': { estado: 200, cuerpo: PROGRAMADA } }, [{ ...PENDIENTE, modalidad: 'PRESENCIAL' }]);
    await userEvent.click(await screen.findByRole('button', { name: 'Programar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Programar reunión' });
    expect(within(dialogo).queryByLabelText('Enlace de la videollamada')).not.toBeInTheDocument();
    expect(within(dialogo).getByText(/oficina de CoreTech/)).toBeInTheDocument();
    await userEvent.click(within(dialogo).getAllByRole('radio')[0]!);
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Programar reunión' }));
    await waitFor(() => expect(cuerpoEnviado(f, 'PATCH', '/solicitudes-reunion/s-8')).toMatchObject({ accion: 'PROGRAMAR', opcion: 1 }));
  });

  it('si el horario ya no está disponible (409) muestra el mensaje y vuelve a pedir la lista', async () => {
    let cargas = 0;
    abrirAdmin({
      'GET /solicitudes-reunion': () => { cargas += 1; return { estado: 200, cuerpo: TODAS }; },
      'PATCH /solicitudes-reunion/s-8': { estado: 409, cuerpo: errores({ mensaje: RECARGA }) },
    });
    await userEvent.click(await within(await fila('S-0008')).findByRole('button', { name: 'Programar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Programar reunión' });
    await userEvent.click(within(dialogo).getAllByRole('radio')[0]!);
    await userEvent.type(within(dialogo).getByLabelText('Enlace de la videollamada'), 'https://meet.google.com/abc');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Programar reunión' }));

    expect(await screen.findByText(RECARGA)).toBeInTheDocument();
    await waitFor(() => expect(cargas).toBe(2));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('rechazar pide un motivo de al menos 15 caracteres y lo manda', async () => {
    const f = abrirAdmin({ 'PATCH /solicitudes-reunion/s-8': { estado: 200, cuerpo: RECHAZADA } });
    await userEvent.click(await screen.findByLabelText('Más acciones para S-0008'));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Rechazar solicitud' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Rechazar solicitud' });

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Rechazar solicitud' }));
    expect(await within(dialogo).findByText('Escribe el motivo: el cliente lo verá.')).toBeInTheDocument();
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'Muy corto');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Rechazar solicitud' }));
    expect(await within(dialogo).findByText('Escribe al menos 15 caracteres.')).toBeInTheDocument();

    await userEvent.type(within(dialogo).getByLabelText('Motivo'), ' y ahora sí es largo.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Rechazar solicitud' }));
    expect(await screen.findByText('Solicitud rechazada. El cliente ya puede enviar otra.')).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'PATCH', '/solicitudes-reunion/s-8')).toEqual({ accion: 'RECHAZAR', motivo: 'Muy corto y ahora sí es largo.' });
  });

  it('el administrador cancela una reunión programada con su motivo', async () => {
    const f = abrirAdmin({ 'PATCH /solicitudes-reunion/s-6': { estado: 200, cuerpo: { ...PROGRAMADA, estado: 'CANCELADA' } } });
    await userEvent.click(await screen.findByLabelText('Más acciones para S-0006'));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Cancelar reunión' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Cancelar reunión' });
    await userEvent.type(within(dialogo).getByLabelText('Motivo'), 'El equipo no puede asistir ese día.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar reunión' }));

    expect(await screen.findByText('Reunión cancelada. El cliente ya puede pedir otra.')).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'PATCH', '/solicitudes-reunion/s-6')).toEqual({ accion: 'CANCELAR', motivo: 'El equipo no puede asistir ese día.' });
  });

  it('una reunión atendida muestra su resumen, y las cerradas no tienen acciones', async () => {
    abrirAdmin();
    await userEvent.click(await within(await fila('S-0005')).findByRole('button', { name: 'Ver resumen' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Resumen de S-0005' });
    expect(within(dialogo).getByText('Revisamos el lector de código de barras y quedó aprobado.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Lo hacemos dentro del plan')).toBeInTheDocument();
    expect(within(await fila('S-0004')).getByText('Cerrada')).toBeInTheDocument();
  });
});

describe('El Product Owner registra el resumen de la reunión (D-42)', () => {
  const LISTA_PARA_CERRAR: Solicitud = { ...PROGRAMADA, puedeCerrar: true };

  it('solo ve «Registrar resumen» cuando la reunión ya ocurrió, y no cancela', async () => {
    simularApi(null, { 'GET /solicitudes-reunion': { estado: 200, cuerpo: [LISTA_PARA_CERRAR, { ...PROGRAMADA, id: 's-9', codigo: 'S-0009' }] } });
    render(<ProveedorNotificaciones><GestionSolicitudes modo="equipo" /></ProveedorNotificaciones>);
    expect(await screen.findAllByRole('button', { name: 'Registrar resumen' })).toHaveLength(1);
    expect(screen.getByText('Esperando la reunión')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Más acciones/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Filtrar por responsable')).not.toBeInTheDocument();
  });

  it('valida el resumen, lo que pidió el cliente y el siguiente paso, y manda el resumen', async () => {
    const f = simularApi(null, {
      'GET /solicitudes-reunion': { estado: 200, cuerpo: [LISTA_PARA_CERRAR] },
      'POST /solicitudes-reunion/s-6/resumen': { estado: 201, cuerpo: { ...PROGRAMADA, estado: 'ATENDIDA' } },
    });
    render(<ProveedorNotificaciones><GestionSolicitudes modo="equipo" /></ProveedorNotificaciones>);
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar resumen' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Resumen de la reunión' });

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar resumen' }));
    expect(await within(dialogo).findByText('Cuenta qué se habló en la reunión.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Escribe qué pidió el cliente.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Elige cuál es el siguiente paso.')).toBeInTheDocument();

    await userEvent.type(within(dialogo).getByLabelText('¿Qué se habló?'), 'Corto');
    await userEvent.type(within(dialogo).getByLabelText('¿Qué pidió el cliente?'), 'Poco');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar resumen' }));
    expect(await within(dialogo).findAllByText(/Escribe al menos \d+ caracteres\./)).toHaveLength(2);
    expect(f.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);

    await userEvent.clear(within(dialogo).getByLabelText('¿Qué se habló?'));
    await userEvent.type(within(dialogo).getByLabelText('¿Qué se habló?'), 'Revisamos el flujo de pago y se aprobaron los cambios del botón.');
    await userEvent.clear(within(dialogo).getByLabelText('¿Qué pidió el cliente?'));
    await userEvent.type(within(dialogo).getByLabelText('¿Qué pidió el cliente?'), 'Un botón de pagar más grande y de otro color.');
    await userEvent.selectOptions(within(dialogo).getByLabelText('¿Qué sigue?'), 'DENTRO_DEL_PLAN');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar resumen' }));

    expect(await screen.findByText('Resumen guardado. La reunión quedó atendida y el cliente puede leerlo.')).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'POST', '/solicitudes-reunion/s-6/resumen')).toEqual({
      resumen: 'Revisamos el flujo de pago y se aprobaron los cambios del botón.',
      quePidio: 'Un botón de pagar más grande y de otro color.',
      siguientePaso: 'DENTRO_DEL_PLAN',
    });
  });

  it('si la reunión aún no ocurrió o ya se cerró (409) muestra el mensaje y recarga', async () => {
    let cargas = 0;
    simularApi(null, {
      'GET /solicitudes-reunion': () => { cargas += 1; return { estado: 200, cuerpo: [LISTA_PARA_CERRAR] }; },
      'POST /solicitudes-reunion/s-6/resumen': { estado: 409, cuerpo: errores({ mensaje: 'Esta reunión ya no se puede cerrar' }) },
    });
    render(<ProveedorNotificaciones><GestionSolicitudes modo="equipo" /></ProveedorNotificaciones>);
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar resumen' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Resumen de la reunión' });
    await userEvent.type(within(dialogo).getByLabelText('¿Qué se habló?'), 'Revisamos el flujo de pago y se aprobaron los cambios del botón.');
    await userEvent.type(within(dialogo).getByLabelText('¿Qué pidió el cliente?'), 'Un botón de pagar más grande y de otro color.');
    await userEvent.selectOptions(within(dialogo).getByLabelText('¿Qué sigue?'), 'NINGUNO');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar resumen' }));

    expect(await screen.findByText('Esta reunión ya no se puede cerrar')).toBeInTheDocument();
    await waitFor(() => expect(cargas).toBe(2));
  });
});
