import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { CITA_ATENDIDA, CITA_CONFIRMADA, CITA_EN_MADRID, CITA_PENDIENTE } from '../../pruebas/agenda';
import { LAURA, abrirEn, errores, simularApi } from '../../pruebas/aplicacion';

const CITAS = [CITA_ATENDIDA, CITA_CONFIRMADA, CITA_PENDIENTE];

const tarjeta = (codigo: string) => screen.getByText(codigo).closest('li') as HTMLElement;

describe('Mis citas (HU-08)', () => {
  it('lista las citas por grupo, con su estado y los botones que corresponden', async () => {
    simularApi(LAURA, { 'GET /citas': { estado: 200, cuerpo: CITAS } });
    abrirEn('/cliente/citas');
    await screen.findByText('C-0061');

    expect(screen.getByRole('heading', { name: 'Próximas' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Atendidas' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Canceladas' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Todas/ })).toHaveTextContent('3');

    // Las próximas van de la más cercana a la más lejana
    const codigos = screen.getAllByText(/^C-00\d\d$/).map((e) => e.textContent);
    expect(codigos).toEqual(['C-0061', 'C-0062', 'C-0052']);

    const pendiente = tarjeta('C-0061');
    expect(within(pendiente).getByText('Pendiente')).toBeInTheDocument();
    expect(within(pendiente).getByText('Martes 11 de marzo · 9:00 a. m. a 10:00 a. m.')).toBeInTheDocument();
    expect(within(pendiente).getByText('Equipo por asignar')).toBeInTheDocument();
    expect(within(pendiente).getByRole('button', { name: 'Cancelar cita C-0061' })).toBeInTheDocument();

    // Confirmada: sin botón de cancelar, con el enlace de la reunión
    const confirmada = tarjeta('C-0062');
    expect(within(confirmada).queryByRole('button', { name: /Cancelar/ })).not.toBeInTheDocument();
    expect(within(confirmada).getByRole('link', { name: 'Unirse a la reunión' })).toHaveAttribute('href', 'https://meet.google.com/abc-defg-hij');
    expect(within(tarjeta('C-0052')).queryByRole('button', { name: /Cancelar/ })).not.toBeInTheDocument();
  });

  it('filtra por próximas, atendidas y canceladas, y explica cuando no hay ninguna', async () => {
    simularApi(LAURA, { 'GET /citas': { estado: 200, cuerpo: CITAS } });
    abrirEn('/cliente/citas');
    await screen.findByText('C-0061');

    await userEvent.click(screen.getByRole('button', { name: /Atendidas/ }));
    expect(screen.getByText('C-0052')).toBeInTheDocument();
    expect(screen.queryByText('C-0061')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Canceladas/ }));
    expect(screen.getByText('No tienes citas canceladas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Canceladas/ })).toHaveAttribute('aria-pressed', 'true');
  });

  it('cancela una cita pendiente después de confirmar en el diálogo', async () => {
    const fetchSimulado = simularApi(LAURA, {
      'GET /citas': { estado: 200, cuerpo: CITAS },
      'PATCH /citas/c-61/cancelar': { estado: 200, cuerpo: { citaId: 'c-61', estado: 'CANCELADA', canceladaAt: '2031-03-04T15:00:00Z' } },
    });
    abrirEn('/cliente/citas');
    await screen.findByText('C-0061');

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar cita C-0061' }));
    const dialogo = screen.getByRole('dialog', { hidden: true });
    expect(within(dialogo).getByText('¿Cancelar la cita C-0061?')).toBeInTheDocument();
    expect(within(dialogo).getByText(/Martes 11 de marzo, 9:00 a\. m\. La franja quedará libre/)).toBeInTheDocument();
    // "Mantener cita" no cancela nada
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Mantener cita', hidden: true }));
    expect(fetchSimulado.mock.calls.some(([, op]) => op?.method === 'PATCH')).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar cita C-0061' }));
    await userEvent.click(within(screen.getByRole('dialog', { hidden: true })).getByRole('button', { name: 'Sí, cancelar', hidden: true }));

    await waitFor(() => expect(within(tarjeta('C-0061')).getByText('Cancelada')).toBeInTheDocument());
    expect(within(tarjeta('C-0061')).queryByRole('button', { name: /Cancelar/ })).not.toBeInTheDocument();
    expect(screen.getByText('Cancelaste la cita C-0061. La franja quedó libre.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Canceladas' })).toBeInTheDocument();
  });

  it('si la cita ya se confirmó, muestra el mensaje de la API y recarga la lista', async () => {
    let llamadasLista = 0;
    simularApi(LAURA, {
      'GET /citas': () => {
        llamadasLista += 1;
        return { estado: 200, cuerpo: llamadasLista === 1 ? CITAS : [CITA_ATENDIDA, CITA_CONFIRMADA, { ...CITA_PENDIENTE, estado: 'CONFIRMADA', puedeCancelar: false }] };
      },
      'PATCH /citas/c-61/cancelar': { estado: 409, cuerpo: errores({ mensaje: 'Esta cita ya fue confirmada, escríbenos para reprogramarla' }) },
    });
    abrirEn('/cliente/citas');
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar cita C-0061' }));
    await userEvent.click(within(screen.getByRole('dialog', { hidden: true })).getByRole('button', { name: 'Sí, cancelar', hidden: true }));

    expect(await screen.findByText('Esta cita ya fue confirmada, escríbenos para reprogramarla')).toBeInTheDocument();
    await waitFor(() => expect(within(tarjeta('C-0061')).getByText('Confirmada')).toBeInTheDocument());
    expect(within(tarjeta('C-0061')).queryByRole('button', { name: /Cancelar/ })).not.toBeInTheDocument();
  });

  it('muestra la hora local y la de Colombia de una cita agendada desde otro país', async () => {
    simularApi(LAURA, { 'GET /citas': { estado: 200, cuerpo: [CITA_EN_MADRID] } });
    abrirEn('/cliente/citas');
    await screen.findByText('C-0070');
    expect(screen.getByText('Jueves 13 de marzo · 3:00 p. m. a 4:00 p. m.')).toBeInTheDocument();
    expect(screen.getByText('En Colombia: 9:00 a. m.')).toBeInTheDocument();
  });

  it('sin citas invita a agendar la primera', async () => {
    simularApi(LAURA, { 'GET /citas': { estado: 200, cuerpo: [] } });
    abrirEn('/cliente/citas');
    expect(await screen.findByText('Aún no tienes citas')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Agendar cita' })[0]).toHaveAttribute('href', '/cliente/agendar');
  });
});
