import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CLAVE_ALMACENAMIENTO, CONFIGURACION_INICIAL, aplicarConfiguracion, leerConfiguracion } from '../accesibilidad/configuracion';
import { ProveedorNotificaciones } from './Notificaciones';
import { PanelAccesibilidad } from './PanelAccesibilidad';

const html = document.documentElement;

/** Dibuja el panel y lo abre: cerrado está oculto (aria-hidden e inert), como en el navegador. */
function dibujarPanel() {
  render(<ProveedorNotificaciones><PanelAccesibilidad /></ProveedorNotificaciones>);
}

async function abrirPanel() {
  dibujarPanel();
  await userEvent.click(screen.getByRole('button', { name: 'Abrir opciones de accesibilidad' }));
}

const guardado = () => JSON.parse(localStorage.getItem(CLAVE_ALMACENAMIENTO) ?? '{}');

beforeEach(() => localStorage.clear());
afterEach(() => {
  localStorage.clear();
  aplicarConfiguracion(CONFIGURACION_INICIAL); // deja <html> limpio para las demás pruebas
  html.classList.remove('panel-accesibilidad-abierto');
});

describe('Panel de accesibilidad (RNF-07)', () => {
  it('tiene la pestaña del borde derecho y abre el panel como un diálogo, con el foco dentro', async () => {
    dibujarPanel();
    const pestana = screen.getByRole('button', { name: 'Abrir opciones de accesibilidad' });
    expect(pestana).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(pestana);

    expect(pestana).toHaveAttribute('aria-expanded', 'true');
    expect(html).toHaveClass('panel-accesibilidad-abierto');
    expect(screen.getByRole('dialog', { name: 'Accesibilidad' })).toHaveAttribute('aria-modal', 'true');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Cerrar opciones de accesibilidad' })).toHaveFocus());
  });

  it('Escape lo cierra y devuelve el foco a la pestaña', async () => {
    dibujarPanel();
    const pestana = screen.getByRole('button', { name: 'Abrir opciones de accesibilidad' });
    await userEvent.click(pestana);
    await userEvent.keyboard('{Escape}');

    expect(html).not.toHaveClass('panel-accesibilidad-abierto');
    expect(pestana).toHaveFocus();
  });

  it('alto contraste: pone la clase en <html>, marca el botón y se recuerda', async () => {
    await abrirPanel();
    const boton = screen.getByRole('button', { name: /Alto contraste/ });
    expect(boton).toHaveAttribute('aria-pressed', 'false');

    await userEvent.click(boton);

    expect(boton).toHaveAttribute('aria-pressed', 'true');
    expect(html).toHaveClass('modo-contraste');
    expect(html.style.getPropertyValue('--filtro-pagina')).toContain('contrast(1.08)');
    expect(guardado().altoContraste).toBe(true);

    await userEvent.click(boton);
    expect(html).not.toHaveClass('modo-contraste');
    expect(html.style.getPropertyValue('--filtro-pagina')).toBe('none');
  });

  it('el tamaño de texto pasa por sus cuatro niveles y vuelve a Normal; el lector de pantalla oye el nivel', async () => {
    await abrirPanel();
    const boton = screen.getByRole('button', { name: /Tamaño de texto/ });
    expect(boton).toHaveTextContent(': Normal');

    await userEvent.click(boton);
    expect(html).toHaveClass('modo-texto-1');
    expect(boton).toHaveTextContent(': Grande');
    await userEvent.click(boton);
    await userEvent.click(boton);
    expect(html).toHaveClass('modo-texto-3');
    expect(boton).toHaveTextContent(': Máximo');
    await userEvent.click(boton);

    expect(html.className).not.toMatch(/modo-texto-/);
    expect(boton).toHaveAttribute('aria-pressed', 'false');
  });

  it('daltonismo: elige un tipo (botones de opción), aplica su filtro de color y se puede volver a «Ninguno»', async () => {
    await abrirPanel();
    const grupo = screen.getByRole('radiogroup', { name: 'Daltonismo' });
    expect(grupo.querySelectorAll('[role="radio"]')).toHaveLength(4);
    expect(screen.getByRole('radio', { name: /Ninguno/ })).toBeChecked();

    await userEvent.click(screen.getByRole('radio', { name: /Deuteranopia/ }));

    expect(screen.getByRole('radio', { name: /Deuteranopia/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /Ninguno/ })).not.toBeChecked();
    expect(html.style.getPropertyValue('--filtro-pagina')).toBe('url(#filtro-deuteranopia)');
    expect(document.getElementById('filtro-deuteranopia')).not.toBeNull(); // el filtro SVG existe

    await userEvent.click(screen.getByRole('radio', { name: /Ninguno/ }));
    expect(html.style.getPropertyValue('--filtro-pagina')).toBe('none');
  });

  it('las opciones avanzadas son interruptores', async () => {
    await abrirPanel();
    const titulos = screen.getByRole('switch', { name: /Resaltar títulos/ });
    expect(titulos).toHaveAttribute('aria-checked', 'false');
    await userEvent.click(titulos);
    expect(titulos).toHaveAttribute('aria-checked', 'true');
    expect(html).toHaveClass('modo-titulos');
    await userEvent.click(screen.getByRole('switch', { name: /Guía de lectura/ }));
    expect(html).toHaveClass('modo-guia');
  });

  it('Restablecer deja todo como al inicio y avisa', async () => {
    await abrirPanel();
    await userEvent.click(screen.getByRole('button', { name: /Alto contraste/ }));
    await userEvent.click(screen.getByRole('switch', { name: /Texto en negrita/ }));
    await userEvent.click(screen.getByRole('radio', { name: /Tritanopia/ }));

    await userEvent.click(screen.getByRole('button', { name: 'Restablecer configuración' }));

    expect(html.className).not.toMatch(/modo-/);
    expect(html.style.getPropertyValue('--filtro-pagina')).toBe('none');
    expect(guardado()).toEqual(CONFIGURACION_INICIAL);
    expect(await screen.findByText('Configuración restablecida.')).toBeInTheDocument();
  });

  it('al abrir la página recupera lo que la persona dejó guardado', async () => {
    localStorage.setItem(CLAVE_ALMACENAMIENTO, JSON.stringify({ tamanoTexto: 2, fuenteDislexia: true, daltonismo: 'protanopia' }));
    await abrirPanel();

    expect(html).toHaveClass('modo-texto-2', 'modo-dislexia');
    expect(screen.getByRole('radio', { name: /Protanopia/ })).toBeChecked();
    expect(screen.getByRole('button', { name: /Tamaño de texto/ })).toHaveTextContent(': Más grande');
  });

  it('«Leer en voz alta» se desactiva con una explicación si el navegador no tiene voz', async () => {
    await abrirPanel();
    const voz = screen.getByRole('button', { name: /Leer en voz alta/ });
    expect(voz).toBeDisabled();
    expect(voz).toHaveAttribute('title', 'Tu navegador no permite leer en voz alta');
  });

  it('un valor guardado dañado no rompe nada: se usan los valores iniciales', () => {
    localStorage.setItem(CLAVE_ALMACENAMIENTO, '{ no es json');
    expect(leerConfiguracion()).toEqual(CONFIGURACION_INICIAL);
    localStorage.setItem(CLAVE_ALMACENAMIENTO, JSON.stringify({ tamanoTexto: 99, daltonismo: 'otro', altoContraste: 'sí' }));
    expect(leerConfiguracion()).toEqual(CONFIGURACION_INICIAL);
  });
});
