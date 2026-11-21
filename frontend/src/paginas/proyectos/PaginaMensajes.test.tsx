import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Hilo } from '../../comunicacion/mensajes';
import { ANDRES, LAURA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { DETALLE, DETALLE_DEL_EQUIPO } from '../../pruebas/seguimiento';

type Extra = NonNullable<Parameters<typeof simularApi>[1]>;

/**
 * El hilo de mensajes del proyecto (HU-14, RN-14): el cliente elige un tema, el desarrollador responde sin tema, el autor edita
 * durante 5 minutos y, con el proyecto pausado, finalizado o cancelado, el hilo es solo de lectura.
 */

const TEMAS = ['Pregunta sobre el avance', 'Idea o cambio para el proyecto', 'Envío de información', 'Reportar un problema', 'Otro'];
const MENSAJE = '¿Se puede agregar el pago contra entrega en Cúcuta?';

const HILO: Hilo = {
  proyecto: 'Tienda en línea Laura', puedeEscribir: true, escribeConTema: true, temas: TEMAS,
  mensajes: [
    { id: 'm-1', autor: 'Laura Gómez', rol: 'CLIENTE', esMio: true, tema: 'Pregunta sobre el avance', contenido: MENSAJE, creadoAt: '2026-11-06T15:12:00Z', editadoAt: null, puedeEditar: true },
    { id: 'm-2', autor: 'Andrés Pérez', rol: 'PRODUCT_OWNER', esMio: false, tema: null, contenido: 'Sí, lo incluyo en el módulo de pago.', creadoAt: '2026-11-06T19:40:00Z', editadoAt: '2026-11-06T19:44:00Z', puedeEditar: false },
  ],
};

const RECARGA = 'El proyecto cambió, recarga para ver lo último';

function abrirCliente(hilo: Hilo, extra: Extra = {}) {
  const f = simularApi(LAURA, {
    'GET /proyectos/P-0031': { estado: 200, cuerpo: DETALLE },
    'GET /proyectos/P-0031/mensajes': { estado: 200, cuerpo: hilo },
    'GET /proyectos/P-0031/solicitudes-reunion': { estado: 200, cuerpo: { puedeSolicitar: false, motivos: [], solicitudes: [] } },
    ...extra,
  });
  abrirEn('/cliente/proyectos/P-0031');
  return f;
}

describe('El hilo de mensajes del cliente (HU-14)', () => {
  it('muestra los mensajes con el tema, la marca «Editado» y quién escribió', async () => {
    abrirCliente(HILO);
    const lista = await screen.findByRole('list', { name: 'Mensajes' });
    expect(within(lista).getByText('Tú')).toBeInTheDocument();
    expect(within(lista).getByText('Andrés Pérez')).toBeInTheDocument();
    expect(within(lista).getByText('Pregunta sobre el avance')).toBeInTheDocument();
    expect(within(lista).getByText(MENSAJE)).toBeInTheDocument();
    expect(within(lista).getByText('· Editado')).toBeInTheDocument();
    // Solo el propio mensaje reciente se edita
    expect(within(lista).getAllByRole('button', { name: 'Editar' })).toHaveLength(1);
  });

  it('enviar exige el tema y un mensaje de al menos 10 caracteres, y no llama al servidor si falla', async () => {
    const f = abrirCliente(HILO);
    await userEvent.click(await screen.findByRole('button', { name: 'Escribir' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Escribirle a Andrés' });

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar mensaje' }));
    expect(await within(dialogo).findByText('Elige de qué trata tu mensaje.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Escribe tu mensaje.')).toBeInTheDocument();

    await userEvent.selectOptions(within(dialogo).getByLabelText('¿De qué trata?'), 'Otro');
    await userEvent.type(within(dialogo).getByLabelText('Tu mensaje'), 'Hola');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar mensaje' }));
    expect(await within(dialogo).findByText('Escribe al menos 10 caracteres.')).toBeInTheDocument();
    expect(f.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('envía el mensaje con su tema, avisa y vuelve a pedir el hilo', async () => {
    let enviado = false;
    const f = abrirCliente(HILO, {
      'GET /proyectos/P-0031/mensajes': () => ({ estado: 200, cuerpo: enviado ? { ...HILO, mensajes: [...HILO.mensajes, { ...HILO.mensajes[0], id: 'm-3', contenido: 'Quisiera ver el avance del catálogo.', puedeEditar: true }] } : HILO }),
      'POST /proyectos/P-0031/mensajes': () => { enviado = true; return { estado: 201, cuerpo: HILO.mensajes[0] }; },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Escribir' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Escribirle a Andrés' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('¿De qué trata?'), 'Pregunta sobre el avance');
    await userEvent.type(within(dialogo).getByLabelText('Tu mensaje'), 'Quisiera ver el avance del catálogo.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar mensaje' }));

    expect(await screen.findByText('Mensaje enviado. Andrés te responderá aquí mismo.')).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'POST', '/proyectos/P-0031/mensajes')).toEqual({ tema: 'Pregunta sobre el avance', contenido: 'Quisiera ver el avance del catálogo.' });
    expect(await within(screen.getByRole('list', { name: 'Mensajes' })).findByText('Quisiera ver el avance del catálogo.')).toBeInTheDocument();
  });

  it('con el proyecto pausado el hilo queda de solo lectura: sin «Escribir» ni «Editar»', async () => {
    abrirCliente({ ...HILO, puedeEscribir: false, mensajes: HILO.mensajes.map((m) => ({ ...m, puedeEditar: false })) });
    expect(await screen.findByText(/no recibe mensajes en su estado actual/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Escribir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
  });

  it('edita su mensaje: el diálogo trae el texto actual y manda los cambios', async () => {
    const f = abrirCliente(HILO, { 'PATCH /mensajes/m-1': { estado: 200, cuerpo: HILO.mensajes[0] } });
    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Editar mensaje' });
    const texto = within(dialogo).getByLabelText('Tu mensaje');
    expect(texto).toHaveValue(MENSAJE);
    await userEvent.clear(texto);
    await userEvent.type(texto, 'Mejor te pregunto por el flujo de pago.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Mensaje editado.')).toBeInTheDocument();
    expect(cuerpoEnviado(f, 'PATCH', '/mensajes/m-1')).toEqual({ tema: 'Pregunta sobre el avance', contenido: 'Mejor te pregunto por el flujo de pago.' });
  });

  it('si pasaron los 5 minutos (409) muestra el mensaje y vuelve a pedir el hilo', async () => {
    let cargas = 0;
    abrirCliente(HILO, {
      'GET /proyectos/P-0031/mensajes': () => { cargas += 1; return { estado: 200, cuerpo: HILO }; },
      'PATCH /mensajes/m-1': { estado: 409, cuerpo: errores({ mensaje: 'Ya no puedes editar este mensaje' }) },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Ya no puedes editar este mensaje')).toBeInTheDocument();
    await waitFor(() => expect(cargas).toBe(2));
  });

  it('un 409 por datos desactualizados al enviar también recarga', async () => {
    let cargas = 0;
    abrirCliente(HILO, {
      'GET /proyectos/P-0031/mensajes': () => { cargas += 1; return { estado: 200, cuerpo: HILO }; },
      'POST /proyectos/P-0031/mensajes': { estado: 409, cuerpo: errores({ mensaje: RECARGA }) },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Escribir' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Escribirle a Andrés' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('¿De qué trata?'), 'Otro');
    await userEvent.type(within(dialogo).getByLabelText('Tu mensaje'), 'Un mensaje de prueba largo.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar mensaje' }));
    expect(await screen.findByText(RECARGA)).toBeInTheDocument();
    await waitFor(() => expect(cargas).toBe(2));
  });
});

describe('El hilo de mensajes del desarrollador (Product Owner)', () => {
  it('responde sin tema: el formulario no pide «¿De qué trata?»', async () => {
    const f = simularApi(ANDRES, {
      'GET /proyectos/P-0031': { estado: 200, cuerpo: DETALLE_DEL_EQUIPO },
      'GET /proyectos/P-0031/mensajes': { estado: 200, cuerpo: { ...HILO, escribeConTema: false } },
      'POST /proyectos/P-0031/mensajes': { estado: 201, cuerpo: HILO.mensajes[1] },
    });
    abrirEn('/equipo/proyectos/P-0031/registrar');
    await userEvent.click(await screen.findByRole('button', { name: 'Escribir' }));
    const dialogo = await screen.findByRole('dialog');
    expect(within(dialogo).queryByLabelText('¿De qué trata?')).not.toBeInTheDocument();
    await userEvent.type(within(dialogo).getByLabelText('Tu mensaje'), 'Ya está listo, mañana te lo muestro.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar mensaje' }));

    await waitFor(() => expect(cuerpoEnviado(f, 'POST', '/proyectos/P-0031/mensajes')).toEqual({ tema: null, contenido: 'Ya está listo, mañana te lo muestro.' }));
  });
});
