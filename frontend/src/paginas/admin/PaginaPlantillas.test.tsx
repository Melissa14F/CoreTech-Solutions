import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { APP_OCULTA, TIENDA, WEB } from '../../pruebas/catalogo';

const paso = (orden: number, titulo: string) => ({ orden, titulo, explicacion: `Explicación de ${titulo}` });
const PLANTILLA_TIENDA = {
  fases: [
    { orden: 1, nombre: 'Requerimientos', queEs: 'Entendemos tu negocio.', apruebaCliente: true, pasos: [paso(1, 'Reunión inicial'), paso(2, 'Lista de funciones')] },
    { orden: 2, nombre: 'Desarrollo', queEs: null, apruebaCliente: false, pasos: [paso(1, 'Catálogo de productos')] },
    { orden: 3, nombre: 'Entrega', queEs: null, apruebaCliente: true, pasos: [paso(1, 'Publicación')] },
  ],
};
const PLANTILLA_WEB = {
  fases: [
    { orden: 1, nombre: 'Contenido', queEs: null, apruebaCliente: true, pasos: [paso(1, 'Mapa del sitio')] },
    { orden: 2, nombre: 'Publicación', queEs: null, apruebaCliente: false, pasos: [paso(1, 'Dominio')] },
  ],
};

function api(extra = {}) {
  return simularApi(PATRICIA, {
    'GET /servicios/gestion': { estado: 200, cuerpo: [TIENDA, WEB, APP_OCULTA] },
    'GET /servicios/s-tienda/plantilla': { estado: 200, cuerpo: PLANTILLA_TIENDA },
    'GET /servicios/s-web/plantilla': { estado: 200, cuerpo: PLANTILLA_WEB },
    ...extra,
  });
}

const resumen = () => document.querySelector('.editor-plan__resumen') as HTMLElement;
const guardar = () => screen.getByRole('button', { name: 'Guardar plantilla' });

describe('Plantillas de fases (HU-27)', () => {
  afterEach(() => vi.restoreAllMocks());

  it('abre la plantilla del primer servicio con su resumen en vivo', async () => {
    api();
    abrirEn('/admin/plantillas');

    expect(await screen.findByRole('heading', { level: 2, name: TIENDA.nombre })).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Requerimientos')).toBeInTheDocument();
    expect(resumen()).toHaveTextContent('3 fases');
    expect(resumen()).toHaveTextContent('33,3 % vale cada una');
    expect(resumen()).toHaveTextContent('4 pasos');
    expect(resumen()).toHaveTextContent('2 las aprueba el cliente');
    expect(screen.getByRole('button', { name: /Desarrollo de tienda en línea/ })).toHaveAttribute('aria-current', 'true');
  });

  it('?servicio=slug abre la plantilla de ese servicio', async () => {
    api();
    abrirEn('/admin/plantillas?servicio=web-corporativa');
    expect(await screen.findByRole('heading', { level: 2, name: WEB.nombre })).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Contenido')).toBeInTheDocument();
  });

  it('agregar una fase actualiza el valor de cada una y la deja con un paso', async () => {
    api();
    abrirEn('/admin/plantillas');
    await screen.findByDisplayValue('Requerimientos');
    await userEvent.click(screen.getByRole('button', { name: 'Agregar fase' }));

    expect(resumen()).toHaveTextContent('4 fases');
    expect(resumen()).toHaveTextContent('25 % vale cada una');
    expect(resumen()).toHaveTextContent('5 pasos');
    expect(document.querySelectorAll('.fase-editor')).toHaveLength(4);
  });

  it('con 8 fases no deja agregar más, y con 2 no deja quitar', async () => {
    api({ 'GET /servicios/s-web/plantilla': { estado: 200, cuerpo: PLANTILLA_WEB } });
    abrirEn('/admin/plantillas?servicio=web-corporativa');
    await screen.findByDisplayValue('Contenido');
    screen.getAllByRole('button', { name: 'Quitar fase' }).forEach((b) => expect(b).toBeDisabled());

    for (let i = 0; i < 6; i++) await userEvent.click(screen.getByRole('button', { name: 'Agregar fase' }));
    expect(screen.getByRole('button', { name: 'Agregar fase' })).toBeDisabled();
    expect(screen.getByText('Llegaste al máximo de 8 fases.')).toBeInTheDocument();
  });

  it('reordena una fase con los botones, sin arrastrar', async () => {
    api();
    abrirEn('/admin/plantillas');
    await screen.findByDisplayValue('Requerimientos');
    await userEvent.click(screen.getAllByRole('button', { name: 'Bajar fase' })[0]!);

    const nombres = Array.from(document.querySelectorAll<HTMLInputElement>('.fase-editor input[id$="-nombre"]')).map((i) => i.value);
    expect(nombres).toEqual(['Desarrollo', 'Requerimientos', 'Entrega']);
  });

  it('guarda la plantilla completa en el orden de la lista y avisa que se usará en los proyectos nuevos', async () => {
    const fetchSimulado = api({ 'PUT /servicios/s-tienda/plantilla': { estado: 200, cuerpo: PLANTILLA_TIENDA } });
    abrirEn('/admin/plantillas');
    const nombre = await screen.findByDisplayValue('Desarrollo');
    await userEvent.clear(nombre);
    await userEvent.type(nombre, '  Construcción  ');
    await userEvent.click(guardar());

    expect(await screen.findByText(`Plantilla de «${TIENDA.nombre}» guardada. Se usará en los proyectos nuevos.`)).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/servicios/s-tienda/plantilla')).toEqual({
      fases: [
        { nombre: 'Requerimientos', queEs: 'Entendemos tu negocio.', apruebaCliente: true,
          pasos: [{ titulo: 'Reunión inicial', explicacion: 'Explicación de Reunión inicial' }, { titulo: 'Lista de funciones', explicacion: 'Explicación de Lista de funciones' }] },
        { nombre: 'Construcción', queEs: null, apruebaCliente: false, pasos: [{ titulo: 'Catálogo de productos', explicacion: 'Explicación de Catálogo de productos' }] },
        { nombre: 'Entrega', queEs: null, apruebaCliente: true, pasos: [{ titulo: 'Publicación', explicacion: 'Explicación de Publicación' }] },
      ],
    });
  });

  it('dos fases con el mismo nombre no se guardan y se marcan en la segunda', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/plantillas');
    const nombre = await screen.findByDisplayValue('Desarrollo');
    await userEvent.clear(nombre);
    await userEvent.type(nombre, 'requerimientos');
    await userEvent.click(guardar());

    expect(await screen.findByText('Ya hay una fase con ese nombre en este servicio')).toBeInTheDocument();
    expect(nombre).toHaveAttribute('aria-invalid', 'true');
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/servicios/s-tienda/plantilla')).toBeUndefined();
  });

  it('una fase sin nombre ni título de paso lista el error arriba y lleva el foco al primer campo', async () => {
    api();
    abrirEn('/admin/plantillas');
    await screen.findByDisplayValue('Requerimientos');
    await userEvent.click(screen.getByRole('button', { name: 'Agregar fase' }));
    await userEvent.click(guardar());

    const alerta = await screen.findByText('Revisa el plan antes de guardar:');
    expect(within(alerta.closest('.alerta') as HTMLElement).getByText('Fase 4: completa el nombre y los pasos')).toBeInTheDocument();
    expect(screen.getByText('Escribe el nombre de la fase (al menos 3 caracteres).')).toBeInTheDocument();
    expect(screen.getByText('Escribe qué se hace en este paso.')).toBeInTheDocument();
    await waitFor(() => expect(document.activeElement).toHaveAttribute('aria-invalid', 'true'));
  });

  it('sin ninguna fase que apruebe el cliente no guarda', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/plantillas');
    await screen.findByDisplayValue('Requerimientos');
    for (const casilla of screen.getAllByRole('checkbox', { checked: true })) await userEvent.click(casilla);
    await userEvent.click(guardar());

    const lista = (await screen.findByText('Revisa el plan antes de guardar:')).closest('.alerta') as HTMLElement;
    expect(within(lista).getByText('Marca al menos una fase que apruebe el cliente')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/servicios/s-tienda/plantilla')).toBeUndefined();
  });

  it('muestra el 422 del servidor (nombre repetido) bajo la fase', async () => {
    api({
      'PUT /servicios/s-tienda/plantilla': {
        estado: 422,
        cuerpo: errores({ campo: 'fases[1].nombre', mensaje: 'Ya hay una fase con ese nombre en este servicio' }),
      },
    });
    abrirEn('/admin/plantillas');
    const nombre = await screen.findByDisplayValue('Desarrollo');
    await userEvent.type(nombre, ' web');
    await userEvent.click(guardar());

    expect(await screen.findByText('Ya hay una fase con ese nombre en este servicio')).toBeInTheDocument();
    expect(nombre).toHaveAttribute('aria-invalid', 'true');
  });

  it('"Deshacer cambios" vuelve a la última versión guardada', async () => {
    api();
    abrirEn('/admin/plantillas');
    const nombre = await screen.findByDisplayValue('Desarrollo');
    expect(screen.getByRole('button', { name: 'Deshacer cambios' })).toBeDisabled();
    await userEvent.type(nombre, ' extra');
    await userEvent.click(screen.getByRole('button', { name: 'Deshacer cambios' }));

    expect(await screen.findByText('Se restableció la última versión guardada.')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Desarrollo')).toBeInTheDocument();
  });

  it('al cambiar de servicio con cambios sin guardar pregunta si quiere descartarlos', async () => {
    api();
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
    abrirEn('/admin/plantillas');
    await userEvent.type(await screen.findByDisplayValue('Desarrollo'), ' extra');

    await userEvent.click(screen.getByRole('button', { name: /Página web corporativa/ }));
    expect(confirmar).toHaveBeenCalledWith(`Tienes cambios sin guardar en «${TIENDA.nombre}». ¿Quieres descartarlos?`);
    expect(screen.getByRole('heading', { level: 2, name: TIENDA.nombre })).toBeInTheDocument();

    confirmar.mockReturnValue(true);
    await userEvent.click(screen.getByRole('button', { name: /Página web corporativa/ }));
    expect(await screen.findByRole('heading', { level: 2, name: WEB.nombre })).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Contenido')).toBeInTheDocument();
  });

  it('sin cambios, cambiar de servicio no pregunta', async () => {
    api();
    const confirmar = vi.spyOn(window, 'confirm');
    abrirEn('/admin/plantillas');
    await screen.findByDisplayValue('Desarrollo');
    await userEvent.click(screen.getByRole('button', { name: /Página web corporativa/ }));
    expect(await screen.findByDisplayValue('Contenido')).toBeInTheDocument();
    expect(confirmar).not.toHaveBeenCalled();
  });

  it('antes de quitar una fase pide confirmación: cancelar la deja y confirmar la quita', async () => {
    api();
    abrirEn('/admin/plantillas');
    await screen.findByDisplayValue('Requerimientos');
    const quitar = screen.getAllByRole('button', { name: 'Quitar fase' })[0]!;

    await userEvent.click(quitar);
    const dialogo = await screen.findByRole('dialog', { name: '¿Quitar esta fase?' });
    expect(dialogo).toHaveTextContent('Se quitará la fase «Requerimientos» con sus 2 pasos.');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar' }));
    expect(document.querySelectorAll('.fase-editor')).toHaveLength(3);

    await userEvent.click(quitar);
    await userEvent.click(within(await screen.findByRole('dialog', { name: '¿Quitar esta fase?' })).getByRole('button', { name: 'Quitar fase' }));
    expect(document.querySelectorAll('.fase-editor')).toHaveLength(2);
    expect(screen.queryByDisplayValue('Requerimientos')).not.toBeInTheDocument();
  });

  it('antes de quitar un paso pide confirmación', async () => {
    api();
    abrirEn('/admin/plantillas');
    await screen.findByDisplayValue('Requerimientos');
    await userEvent.click(screen.getAllByRole('button', { name: 'Quitar paso 1' })[0]!);
    const dialogo = await screen.findByRole('dialog', { name: '¿Quitar este paso?' });
    expect(dialogo).toHaveTextContent('Se quitará el paso «Reunión inicial».');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Quitar paso' }));
    expect(screen.queryByDisplayValue('Reunión inicial')).not.toBeInTheDocument();
    expect(screen.getByDisplayValue('Lista de funciones')).toBeInTheDocument();
  });
});
