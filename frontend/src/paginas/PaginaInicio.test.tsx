import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { LAURA, abrirEn, simularApi } from '../pruebas/aplicacion';
import { TIENDA, WEB, publico } from '../pruebas/catalogo';

const CONTENIDO = {
  'portada-titulo-1': 'Software hecho a tu medida.',
  'portada-titulo-2': 'Con cada paso a la vista.',
  'pie-mision': 'Nuestra misión publicada desde el panel de administración del sitio.',
  'contacto-telefono': '301 555 1234',
};

describe('Página principal (HU-07)', () => {
  it('muestra los textos publicados y los servicios visibles en orden', async () => {
    simularApi(null, {
      'GET /servicios': { estado: 200, cuerpo: [publico(TIENDA), publico(WEB)] },
      'GET /contenido': { estado: 200, cuerpo: { textos: CONTENIDO } },
    });
    abrirEn('/');

    expect(await screen.findByRole('heading', { level: 1, name: 'Software hecho a tu medida. Con cada paso a la vista.' })).toBeInTheDocument();
    const carrusel = await screen.findByRole('region', { name: 'Servicios de CoreTech' });
    // Las tarjetas fuera de la vista están ocultas al lector de pantalla (aria-hidden), pero existen
    const titulos = within(carrusel).getAllByRole('heading', { level: 3, hidden: true }).map((h) => h.textContent);
    expect(titulos).toEqual([TIENDA.nombre, WEB.nombre]);
    expect(screen.getByText('Nuestra misión publicada desde el panel de administración del sitio.')).toBeInTheDocument();
    // El teléfono sale en Contacto y en el pie de página
    screen.getAllByRole('link', { name: '301 555 1234' }).forEach((enlace) => expect(enlace).toHaveAttribute('href', 'tel:+573015551234'));
  });

  it('"Agendar cita" lleva a crear la cuenta con el servicio elegido', async () => {
    simularApi(null, { 'GET /servicios': { estado: 200, cuerpo: [publico(TIENDA)] } });
    abrirEn('/');
    expect(await screen.findByRole('link', { name: `Agendar cita para ${TIENDA.nombre}` }))
      .toHaveAttribute('href', '/registro?servicio=tienda-en-linea');
  });

  it('un cliente con sesión va directo a agendar', async () => {
    simularApi(LAURA, { 'GET /servicios': { estado: 200, cuerpo: [publico(TIENDA)] } });
    abrirEn('/');
    expect(await screen.findByRole('link', { name: `Agendar cita para ${TIENDA.nombre}` }))
      .toHaveAttribute('href', '/cliente/agendar?servicio=tienda-en-linea');
  });

  it('sin servicios visibles invita a escribir', async () => {
    simularApi(null, { 'GET /servicios': { estado: 200, cuerpo: [] } });
    abrirEn('/');
    expect(await screen.findByRole('heading', { name: 'Pronto publicaremos nuestros servicios' })).toBeInTheDocument();
  });

  it('si la imagen de un servicio no carga muestra una genérica con el mismo texto alternativo', async () => {
    simularApi(null, { 'GET /servicios': { estado: 200, cuerpo: [publico(TIENDA)] } });
    abrirEn('/');
    fireEvent.error(await screen.findByRole('img', { name: TIENDA.imagenAlt }));
    const generica = screen.getByRole('img', { name: TIENDA.imagenAlt });
    expect(generica.tagName).toBe('SPAN');
  });

  it('si la API no responde muestra los textos de respaldo', async () => {
    simularApi(null);
    abrirEn('/');
    expect(await screen.findByRole('heading', { level: 1, name: 'Tu idea, hecha software. Y cada avance, a la vista.' })).toBeInTheDocument();
  });

  it('el carrusel avanza con las flechas y se puede pausar', async () => {
    simularApi(null, { 'GET /servicios': { estado: 200, cuerpo: [publico(TIENDA), publico(WEB)] } });
    abrirEn('/');
    const siguiente = await screen.findByRole('button', { name: 'Servicios siguientes' });
    const grupo2 = screen.getByRole('button', { name: 'Ver grupo 2 de 2' });
    expect(grupo2).toHaveAttribute('aria-current', 'false');

    await userEvent.click(siguiente);
    expect(grupo2).toHaveAttribute('aria-current', 'true');
    // La primera tarjeta salió de la vista: oculta al lector de pantalla y sin foco de teclado (inert)
    const primera = document.querySelector('[aria-label="1 de 2"]');
    expect(primera).toHaveAttribute('aria-hidden', 'true');
    expect(primera).toHaveAttribute('inert');
    expect(screen.getByRole('group', { name: '2 de 2' })).toHaveAttribute('aria-hidden', 'false');

    const pausa = screen.getByRole('button', { name: 'Pausar carrusel' });
    await userEvent.click(pausa);
    expect(screen.getByRole('button', { name: 'Reanudar carrusel' })).toHaveAttribute('aria-pressed', 'true');
  });
});
