import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

import { MARZO_COLOMBIA } from '../pruebas/agenda';
import { Calendario, infoDia } from './Calendario';

const HOY = '2031-03-04';

function montar(propios: Partial<Parameters<typeof Calendario>[0]> = {}) {
  const alElegir = vi.fn();
  const alCambiarMes = vi.fn();
  render(
    <Calendario mes="2031-03" hoy={HOY} franjas={MARZO_COLOMBIA} seleccionado={null} alElegir={alElegir}
      alCambiarMes={alCambiarMes} mesMinimo="2031-03" mesMaximo="2031-04" {...propios} />,
  );
  return { alElegir, alCambiarMes };
}

describe('Calendario del agendamiento (HU-01)', () => {
  it('dice qué días tienen horarios y por qué los demás no están disponibles', () => {
    montar();
    expect(screen.getByRole('button', { name: 'Jueves 6 de marzo, 3 horarios libres' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Viernes 7 de marzo, 1 horario libre' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Miércoles 5 de marzo, no disponible\. Sin cupos/ })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('button', { name: /Lunes 10 de marzo, no disponible\. No disponible: festivo en Colombia/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sábado 8 de marzo, no disponible\. Fin de semana/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Lunes 3 de marzo, no disponible\. Ya pasó/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Martes 4 de marzo, no disponible\. Hoy/ })).toHaveAttribute('aria-current', 'date');
  });

  it('se recorre con el teclado: un solo día entra con Tab y las flechas cambian el foco', async () => {
    montar();
    const botones = screen.getAllByRole('button').filter((b) => b.hasAttribute('data-fecha'));
    expect(botones.filter((b) => b.getAttribute('tabindex') === '0')).toHaveLength(1);

    const jueves = screen.getByRole('button', { name: /Jueves 6 de marzo/ });
    jueves.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', { name: /Viernes 7 de marzo/ })).toHaveFocus();
    // Los días que no se pueden elegir también reciben el foco, para que se anuncien
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', { name: /Sábado 8 de marzo/ })).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('button', { name: /Sábado 15 de marzo/ })).toHaveFocus();
    await userEvent.keyboard('{Home}');
    expect(screen.getByRole('button', { name: /Lunes 10 de marzo/ })).toHaveFocus();
    await userEvent.keyboard('{End}');
    expect(screen.getByRole('button', { name: /Domingo 16 de marzo/ })).toHaveFocus();
  });

  it('Enter elige un día con horarios y no hace nada en uno no disponible', async () => {
    const { alElegir } = montar();
    screen.getByRole('button', { name: /Miércoles 5 de marzo/ }).focus();
    await userEvent.keyboard('{Enter}');
    expect(alElegir).not.toHaveBeenCalled();

    screen.getByRole('button', { name: /Jueves 6 de marzo/ }).focus();
    await userEvent.keyboard('{Enter}');
    expect(alElegir).toHaveBeenCalledWith('2031-03-06');
  });

  it('Av Pág pasa al mes siguiente y no se sale del plazo', async () => {
    const { alCambiarMes } = montar();
    screen.getByRole('button', { name: /Jueves 6 de marzo/ }).focus();
    await userEvent.keyboard('{PageDown}');
    expect(alCambiarMes).toHaveBeenCalledWith('2031-04');

    alCambiarMes.mockClear();
    await userEvent.keyboard('{PageUp}'); // marzo ya es el primer mes: no hay anterior
    expect(alCambiarMes).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Mes anterior' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Mes siguiente' })).toBeEnabled();
  });

  it('marca el día elegido y los días con pocos horarios', () => {
    montar({ seleccionado: '2031-03-06' });
    expect(screen.getByRole('button', { name: /Jueves 6 de marzo/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /Viernes 7 de marzo/ })).toHaveClass('calendario__dia--pocos');
  });

  it('mientras carga no anuncia los días como disponibles', () => {
    montar({ franjas: null });
    expect(screen.getByRole('button', { name: /Jueves 6 de marzo, no disponible\. Cargando horarios/ })).toBeInTheDocument();
    expect(infoDia('2031-03-06', null, HOY).disponible).toBe(false);
  });
});
