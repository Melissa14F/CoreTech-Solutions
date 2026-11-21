import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Hilo } from '../../comunicacion/mensajes';
import type { Solicitud, SolicitudesDelProyecto } from '../../comunicacion/reuniones';
import { LAURA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { DETALLE } from '../../pruebas/seguimiento';

type Extra = NonNullable<Parameters<typeof simularApi>[1]>;

/**
 * Las reuniones de seguimiento en el detalle del proyecto del cliente (HU-15, RN-14, RN-15): pedir una con uno o dos horarios,
 * ver su respuesta y, cuando se atendió, el resumen. Sin el plan aprobado o con una pendiente, el botón no aparece.
 */

const MOTIVOS = ['Revisar el avance', 'Cambio en los requerimientos', 'Duda técnica', 'Reportar un error', 'Otro'];
const HILO_VACIO: Hilo = { proyecto: 'Tienda en línea Laura', puedeEscribir: true, escribeConTema: true, temas: ['Otro'], mensajes: [] };

const SOLICITUD: Solicitud = {
  id: 's-8', codigo: 'S-0008', proyectoCodigo: 'P-0031', proyecto: 'Tienda en línea Laura', cliente: 'Laura Gómez', responsable: 'Andrés Pérez',
  fase: 'Desarrollo', motivo: 'Revisar el avance', razon: 'Quiero revisar el flujo de pago antes de que pase a Pruebas.', modalidad: 'VIRTUAL',
  opciones: ['2026-11-19T15:00:00Z', '2026-11-20T20:00:00Z'], estado: 'PENDIENTE', fechaProgramada: null, enlace: null, motivoRechazo: null,
  enviadaAt: '2026-11-18T21:12:00Z', resumen: null, puedeCerrar: false,
};

function abrir(reuniones: SolicitudesDelProyecto, extra: Extra = {}) {
  const f = simularApi(LAURA, {
    'GET /proyectos/P-0031': { estado: 200, cuerpo: DETALLE },
    'GET /proyectos/P-0031/mensajes': { estado: 200, cuerpo: HILO_VACIO },
    'GET /proyectos/P-0031/solicitudes-reunion': { estado: 200, cuerpo: reuniones },
    ...extra,
  });
  abrirEn('/cliente/proyectos/P-0031');
  return f;
}

const tarjeta = async () => (await screen.findByRole('heading', { name: 'Reuniones de seguimiento' })).closest('section')!;

describe('Las reuniones de seguimiento del cliente (HU-15)', () => {
  it('sin el plan aprobado explica cuándo podrá pedirlas y no muestra el botón', async () => {
    abrir({ puedeSolicitar: false, motivos: MOTIVOS, solicitudes: [] });
    expect(await within(await tarjeta()).findByText('Podrás pedir reuniones de seguimiento cuando apruebes el plan de trabajo.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Solicitar reunión' })).not.toBeInTheDocument();
  });

  it('con una solicitud pendiente muestra su estado y no deja pedir otra (RN-15)', async () => {
    abrir({ puedeSolicitar: false, motivos: MOTIVOS, solicitudes: [SOLICITUD] });
    const t = await tarjeta();
    expect(await within(t).findByText('Por responder')).toBeInTheDocument();
    expect(within(t).getByText(/Andrés aún no responde/)).toBeInTheDocument();
    expect(within(t).queryByRole('button', { name: 'Solicitar reunión' })).not.toBeInTheDocument();
  });

  it('una reunión programada trae el enlace, y una atendida su resumen', async () => {
    abrir({
      puedeSolicitar: true, motivos: MOTIVOS,
      solicitudes: [
        { ...SOLICITUD, id: 's-6', codigo: 'S-0006', estado: 'PROGRAMADA', fechaProgramada: '2026-11-19T15:00:00Z', enlace: 'https://meet.google.com/abc' },
        { ...SOLICITUD, id: 's-5', codigo: 'S-0005', estado: 'ATENDIDA', fechaProgramada: '2026-11-05T15:00:00Z',
          resumen: { resumen: 'Revisamos el catálogo y quedó aprobado.', quePidio: 'Más filtros por categoría.', siguientePaso: 'CAMBIO_DE_PLAN', registradoAt: '2026-11-05T17:00:00Z' } },
        { ...SOLICITUD, id: 's-4', codigo: 'S-0004', estado: 'RECHAZADA', motivoRechazo: 'La duda se resolvió por correo.' },
      ],
    });
    const t = await tarjeta();
    expect(await within(t).findByRole('link', { name: 'Entrar a la videollamada' })).toHaveAttribute('href', 'https://meet.google.com/abc');
    expect(within(t).getByText('Revisamos el catálogo y quedó aprobado.')).toBeInTheDocument();
    expect(within(t).getByText('Proponer un cambio de plan')).toBeInTheDocument();
    expect(within(t).getByText(/La duda se resolvió por correo\./)).toBeInTheDocument();
    expect(within(t).getByRole('button', { name: 'Solicitar reunión' })).toBeInTheDocument();
  });

  it('pedir una reunión valida cada campo y no envía nada mientras falte alguno', async () => {
    const f = abrir({ puedeSolicitar: true, motivos: MOTIVOS, solicitudes: [] });
    await userEvent.click(await within(await tarjeta()).findByRole('button', { name: 'Solicitar reunión' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Solicitar una reunión' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar solicitud' }));

    expect(await within(dialogo).findByText('Elige el motivo de la reunión.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Cuéntanos por qué quieres la reunión.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Elige cómo quieres reunirte.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Elige el día y la hora de la opción 1.')).toBeInTheDocument();
    expect(f.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('manda el motivo, la razón, la modalidad y las horas de Colombia, con la segunda opción opcional', async () => {
    const f = abrir({ puedeSolicitar: true, motivos: MOTIVOS, solicitudes: [] }, { 'POST /proyectos/P-0031/solicitudes-reunion': { estado: 201, cuerpo: SOLICITUD } });
    await userEvent.click(await within(await tarjeta()).findByRole('button', { name: 'Solicitar reunión' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Solicitar una reunión' });

    await userEvent.selectOptions(within(dialogo).getByLabelText('Motivo'), 'Duda técnica');
    await userEvent.type(within(dialogo).getByLabelText('¿Qué quieres hablar?'), 'Quiero entender cómo funcionarán los recordatorios.');
    await userEvent.click(within(dialogo).getByRole('radio', { name: /Virtual/ }));
    const dia1 = within(dialogo).getByLabelText('Día de la opción 1');
    await userEvent.selectOptions(dia1, within(dia1).getAllByRole('option')[2]!);
    await userEvent.selectOptions(within(dialogo).getByLabelText('Hora de la opción 1'), '10');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar solicitud' }));

    expect(await screen.findByText('Solicitud enviada. Andrés te responderá con el horario elegido.')).toBeInTheDocument();
    const cuerpo = cuerpoEnviado(f, 'POST', '/proyectos/P-0031/solicitudes-reunion') as Record<string, string | null>;
    expect(cuerpo).toMatchObject({ motivo: 'Duda técnica', razon: 'Quiero entender cómo funcionarán los recordatorios.', modalidad: 'VIRTUAL', opcion2: null });
    expect(cuerpo.opcion1).toMatch(/^\d{4}-\d{2}-\d{2}T10:00:00-05:00$/);
  });

  it('dos opciones iguales no se envían', async () => {
    const f = abrir({ puedeSolicitar: true, motivos: MOTIVOS, solicitudes: [] });
    await userEvent.click(await within(await tarjeta()).findByRole('button', { name: 'Solicitar reunión' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Solicitar una reunión' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('Motivo'), 'Otro');
    await userEvent.type(within(dialogo).getByLabelText('¿Qué quieres hablar?'), 'Quiero revisar varios puntos del proyecto.');
    await userEvent.click(within(dialogo).getByRole('radio', { name: /Presencial/ }));
    for (const n of ['1', '2']) {
      const dia = within(dialogo).getByLabelText(`Día de la opción ${n}`);
      await userEvent.selectOptions(dia, within(dia).getAllByRole('option')[1]!);
      await userEvent.selectOptions(within(dialogo).getByLabelText(`Hora de la opción ${n}`), '15');
    }
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar solicitud' }));
    expect(await within(dialogo).findByText('Las dos opciones deben ser distintas')).toBeInTheDocument();
    expect(f.mock.calls.some(([, op]) => op?.method === 'POST')).toBe(false);
  });

  it('si ya hay una pendiente (409) muestra el mensaje y vuelve a pedir la lista', async () => {
    abrir({ puedeSolicitar: true, motivos: MOTIVOS, solicitudes: [] }, {
      'POST /proyectos/P-0031/solicitudes-reunion': { estado: 409, cuerpo: errores({ mensaje: 'Ya tienes una solicitud de reunión en espera para este proyecto' }) },
    });
    await userEvent.click(await within(await tarjeta()).findByRole('button', { name: 'Solicitar reunión' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Solicitar una reunión' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('Motivo'), 'Otro');
    await userEvent.type(within(dialogo).getByLabelText('¿Qué quieres hablar?'), 'Quiero revisar varios puntos del proyecto.');
    await userEvent.click(within(dialogo).getByRole('radio', { name: /Virtual/ }));
    const dia = within(dialogo).getByLabelText('Día de la opción 1');
    await userEvent.selectOptions(dia, within(dia).getAllByRole('option')[1]!);
    await userEvent.selectOptions(within(dialogo).getByLabelText('Hora de la opción 1'), '9');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar solicitud' }));
    expect(await screen.findByText('Ya tienes una solicitud de reunión en espera para este proyecto')).toBeInTheDocument();
  });
});
