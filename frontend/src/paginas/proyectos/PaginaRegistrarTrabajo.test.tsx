import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ANDRES, abrirEn, errores, simularApi } from '../../pruebas/aplicacion';
import { DETALLE_DEL_EQUIPO, FASE_2, paso } from '../../pruebas/seguimiento';

const RUTA = '/equipo/proyectos/P-0031/registrar';
const NOTA = 'Ya recibes cada pedido en tu panel y tu cliente recibe un correo.';

/** Después de publicar, el servidor devuelve el proyecto con el paso hecho. */
const DESPUES = {
  ...DETALLE_DEL_EQUIPO,
  fases: [DETALLE_DEL_EQUIPO.fases[0]!, {
    ...FASE_2, pasosHechos: 2,
    pasos: [FASE_2.pasos[0]!, paso('s-3', 2, 'Pedidos y correos de confirmación', { estado: 'HECHO', nota: NOTA, hechoAt: '2026-11-20T15:00:00Z' }), FASE_2.pasos[2]!],
  }],
};

function abrir(extra = {}) {
  let publicado = false;
  const fetchSimulado = simularApi(ANDRES, {
    'GET /proyectos/P-0031': () => ({ estado: 200, cuerpo: publicado ? DESPUES : DETALLE_DEL_EQUIPO }),
    'PATCH /pasos/s-3': () => { publicado = true; return { estado: 200, cuerpo: { pasoId: 's-3', hechoAt: '2026-11-20T15:00:00Z', pasosHechos: '2 de 3' } }; },
    ...extra,
  });
  abrirEn(RUTA);
  return fetchSimulado;
}

const hayPatch = (f: ReturnType<typeof simularApi>) => f.mock.calls.some(([, op]) => op?.method === 'PATCH');

describe('Registrar trabajo del Product Owner (HU-03)', () => {
  it('muestra la fase actual con el paso en curso elegido y cuántos quedan', async () => {
    abrir();
    expect(await screen.findByRole('heading', { level: 1, name: 'Registrar trabajo' })).toBeInTheDocument();
    expect(screen.getByText('Fase 2 de 2 ·', { exact: false })).toBeInTheDocument();
    const paso = screen.getByRole('combobox', { name: 'Paso terminado' });
    expect(paso).toHaveValue('s-3');
    expect(within(paso).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Pedidos y correos de confirmación (en curso)', 'Pago contra entrega',
    ]);
    expect(screen.getByText('por hacer en Desarrollo.', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('Lo que publiques no se puede editar ni borrar.')).toBeInTheDocument();
  });

  it('la nota exige de 20 a 400 caracteres y no envía nada si falla', async () => {
    const f = abrir();
    await userEvent.click(await screen.findByRole('button', { name: 'Marcar como hecho' }));
    expect(await screen.findByText('Escribe la nota para que Laura sepa qué cambió.')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Nota para Laura: ¿qué se hizo?'), 'Muy corta');
    await userEvent.click(screen.getByRole('button', { name: 'Marcar como hecho' }));
    expect(await screen.findByText('Escribe al menos 20 caracteres.')).toBeInTheDocument();
    expect(hayPatch(f)).toBe(false);
  });

  it('rechaza un archivo que no es PNG, JPG, SVG o PDF antes de enviar', async () => {
    const f = abrir();
    await userEvent.type(await screen.findByLabelText('Nota para Laura: ¿qué se hizo?'), NOTA);
    await userEvent.upload(screen.getByLabelText(/Bocetos o documentos/), new File(['MZ'], 'virus.exe', { type: 'application/x-msdownload' }),
      { applyAccept: false });
    await userEvent.click(screen.getByRole('button', { name: 'Marcar como hecho' }));
    expect(await screen.findByText('«virus.exe» no es un PNG, JPG, SVG o PDF.')).toBeInTheDocument();
    expect(hayPatch(f)).toBe(false);
  });

  it('confirma, publica el paso y sube los archivos; luego el paso ya no se ofrece', async () => {
    const f = abrir({
      'POST /pasos/s-3/adjuntos': { estado: 201, cuerpo: [{ id: 'a-9', titulo: 'boceto', tipoMime: 'image/png', tamanoBytes: 3, url: '/api/v1/adjuntos/a-9' }] },
    });
    await userEvent.type(await screen.findByLabelText('Nota para Laura: ¿qué se hizo?'), NOTA);
    await userEvent.upload(screen.getByLabelText(/Bocetos o documentos/), new File(['abc'], 'boceto.png', { type: 'image/png' }));
    expect(screen.getByRole('list', { name: 'Archivos elegidos' })).toHaveTextContent('boceto.png');
    await userEvent.click(screen.getByRole('button', { name: 'Marcar como hecho' }));

    const dialogo = await screen.findByRole('dialog', { name: '¿Marcar el paso como hecho?' });
    expect(within(dialogo).getByText(/«Pedidos y correos de confirmación» quedará con ✓ y Laura verá tu nota/)).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Sí, publicar' }));

    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Paso terminado' })).toHaveValue('s-4'));
    const patch = f.mock.calls.find(([, op]) => op?.method === 'PATCH')!;
    expect(JSON.parse(patch[1]!.body as string)).toEqual({ estado: 'HECHO', nota: NOTA });
    const subida = f.mock.calls.find(([url]) => url === '/api/v1/pasos/s-3/adjuntos')!;
    expect(subida[1]!.body).toBeInstanceOf(FormData);
    expect(await screen.findByText('✓ Paso hecho. Laura verá tu nota.')).toBeInTheDocument();
  });

  it('si otra pestaña ya lo registró (409) muestra el mensaje y recarga lo último', async () => {
    abrir({
      'PATCH /pasos/s-3': { estado: 409, cuerpo: errores({ mensaje: 'Este paso ya está registrado o no es de la fase actual' }) },
    });
    await userEvent.type(await screen.findByLabelText('Nota para Laura: ¿qué se hizo?'), NOTA);
    await userEvent.click(screen.getByRole('button', { name: 'Marcar como hecho' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Sí, publicar' }));
    expect(await screen.findByText('Este paso ya está registrado o no es de la fase actual')).toBeInTheDocument();
  });

  it('si el proyecto no es de su equipo se ve «Proyecto no encontrado»', async () => {
    simularApi(ANDRES, { 'GET /proyectos/P-0031': { estado: 403, cuerpo: errores({ mensaje: 'Este proyecto no es de tu equipo' }) } });
    abrirEn(RUTA);
    expect(await screen.findByRole('heading', { level: 1, name: 'Proyecto no encontrado' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a mis proyectos' })).toHaveAttribute('href', '/equipo/proyectos');
  });

  it('con el plan sin aprobar no hay formulario', async () => {
    simularApi(ANDRES, { 'GET /proyectos/P-0031': { estado: 200, cuerpo: { ...DETALLE_DEL_EQUIPO, estadoPlan: 'ENVIADO', fases: [] } } });
    abrirEn(RUTA);
    expect(await screen.findByText('El trabajo empieza cuando Laura apruebe el plan y la cotización.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Marcar como hecho' })).not.toBeInTheDocument();
  });
});
