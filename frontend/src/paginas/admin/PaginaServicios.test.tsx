import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { APP_OCULTA, TIENDA, WEB } from '../../pruebas/catalogo';

const NUEVO = { ...WEB, servicioId: 's-nuevo', nombre: 'Sistema de facturación', slug: 'sistema-de-facturacion', orden: 4, citasUltimos6Meses: 0, fasesPlantilla: 0 };

function api(extra = {}) {
  return simularApi(PATRICIA, {
    'GET /servicios/gestion': { estado: 200, cuerpo: [TIENDA, WEB, APP_OCULTA] },
    ...extra,
  });
}

const fila = async (nombre: string) => (await screen.findByRole('heading', { level: 2, name: nombre })).closest('li') as HTMLElement;

describe('Administrar los servicios (HU-18)', () => {
  it('lista los servicios en el orden del carrusel con su estado, citas y fases', async () => {
    api();
    abrirEn('/admin/servicios');
    const tienda = await fila(TIENDA.nombre);
    expect(within(tienda).getByText('Visible')).toBeInTheDocument();
    expect(within(tienda).getByText('26 citas en 6 meses')).toBeInTheDocument();
    expect(within(tienda).getByRole('link', { name: /5 fases/ })).toHaveAttribute('href', '/admin/plantillas?servicio=tienda-en-linea');
    expect(within(await fila(APP_OCULTA.nombre)).getByText('Oculto')).toBeInTheDocument();
    expect(screen.getByText('visibles en el catálogo', { exact: false })).toHaveTextContent('2 visibles en el catálogo · 1 oculto');
  });

  it('crea un servicio con vista previa y avisa que quedó publicado', async () => {
    const fetchSimulado = api({ 'POST /servicios': { estado: 201, cuerpo: NUEVO } });
    abrirEn('/admin/servicios');
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo servicio' }));

    await userEvent.type(screen.getByLabelText('Nombre'), 'Sistema de facturación');
    await userEvent.type(screen.getByLabelText('Descripción corta'), 'Facturas electrónicas al día y sin errores.');
    await userEvent.click(screen.getByRole('radio', { name: 'Página web corporativa' }));
    await userEvent.type(screen.getByLabelText('Texto alternativo de la imagen'), 'Ilustración de una factura electrónica');
    // La vista previa repite lo que se escribe
    const previa = screen.getByLabelText('Vista previa de la tarjeta');
    expect(within(previa).getByRole('heading', { name: 'Sistema de facturación' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Crear servicio' }));
    expect(await screen.findByText('Servicio "Sistema de facturación" creado y publicado en el catálogo.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/servicios')).toEqual({
      nombre: 'Sistema de facturación', descripcionCorta: 'Facturas electrónicas al día y sin errores.',
      imagenUrl: 'img/servicios/web-corporativa.webp', imagenAlt: 'Ilustración de una factura electrónica', visible: true,
    });
  });

  it('sin datos marca cada campo con el mensaje del prototipo', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/servicios');
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo servicio' }));
    await userEvent.click(screen.getByRole('button', { name: 'Crear servicio' }));

    expect(screen.getByText('Escribe el nombre del servicio.')).toBeInTheDocument();
    expect(screen.getByText('Escribe la descripción.')).toBeInTheDocument();
    expect(screen.getByText('Elige o sube una imagen.')).toBeInTheDocument();
    expect(screen.getByText('Describe la imagen: lo leen las personas con lector de pantalla.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/servicios')).toBeUndefined();
  });

  it('un nombre repetido se marca bajo el nombre (409)', async () => {
    api({ 'PUT /servicios/s-web': { estado: 409, cuerpo: errores({ campo: 'nombre', mensaje: 'Ya existe un servicio con este nombre.' }) } });
    abrirEn('/admin/servicios');
    await userEvent.click(within(await fila(WEB.nombre)).getByRole('button', { name: 'Editar' }));
    const nombre = screen.getByLabelText('Nombre');
    await userEvent.clear(nombre);
    await userEvent.type(nombre, TIENDA.nombre);
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Ya existe un servicio con este nombre.')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre')).toHaveAttribute('aria-invalid', 'true');
  });

  it('sube una imagen propia y la usa en el servicio', async () => {
    const url = '/api/v1/servicios/imagenes/9b1d0c55-1234-4321-aaaa-bbbbccccdddd.webp';
    api({ 'POST /servicios/imagenes': { estado: 201, cuerpo: { imagenUrl: url } } });
    abrirEn('/admin/servicios');
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo servicio' }));

    const archivo = new File([new Uint8Array([82, 73, 70, 70])], 'mi-imagen.webp', { type: 'image/webp' });
    await userEvent.upload(screen.getByTestId('archivo-imagen'), archivo);

    // Sin texto alternativo todavía, la imagen de la vista previa es decorativa: se busca por etiqueta
    await waitFor(() => expect(screen.getByLabelText('Vista previa de la tarjeta').querySelector('img')).toHaveAttribute('src', url));
    expect(screen.getByRole('radio', { name: /Cambiar/ })).toBeChecked();
  });

  it('no deja subir una imagen de más de 2 MB', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/servicios');
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo servicio' }));
    const grande = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'grande.png', { type: 'image/png' });
    await userEvent.upload(screen.getByTestId('archivo-imagen'), grande);

    expect(screen.getByText('La imagen pesa más de 2 MB. Comprímela o usa formato WebP.')).toBeInTheDocument();
    expect(fetchSimulado).not.toHaveBeenCalledWith('/api/v1/servicios/imagenes', expect.anything());
  });

  it('oculta un servicio del catálogo', async () => {
    const fetchSimulado = api({ 'PATCH /servicios/s-web/visible': { estado: 200, cuerpo: { ...WEB, visible: false } } });
    abrirEn('/admin/servicios');
    await userEvent.click(within(await fila(WEB.nombre)).getByRole('button', { name: `Ocultar ${WEB.nombre} del catálogo` }));

    expect(await within(await fila(WEB.nombre)).findByText('Oculto')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/servicios/s-web/visible')).toEqual({ visible: false });
  });

  it('cambia el orden del carrusel', async () => {
    const fetchSimulado = api({ 'PATCH /servicios/s-web/orden': { estado: 200, cuerpo: [{ ...WEB, orden: 1 }, { ...TIENDA, orden: 2 }, APP_OCULTA] } });
    abrirEn('/admin/servicios');
    await userEvent.click(within(await fila(WEB.nombre)).getByRole('button', { name: `Subir ${WEB.nombre}` }));

    await waitFor(() => expect(screen.getAllByRole('heading', { level: 2 })[0]).toHaveTextContent(WEB.nombre));
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/servicios/s-web/orden')).toEqual({ direccion: -1 });
    expect(within(await fila(WEB.nombre)).getByRole('button', { name: `Subir ${WEB.nombre}` })).toBeDisabled();
  });

  it('un servicio con citas no se elimina: ofrece ocultarlo (RN-21)', async () => {
    const fetchSimulado = api({
      'DELETE /servicios/s-tienda': { estado: 409, cuerpo: errores({ mensaje: 'Este servicio tiene citas o proyectos; puedes ocultarlo' }) },
      'PATCH /servicios/s-tienda/visible': { estado: 200, cuerpo: { ...TIENDA, visible: false } },
    });
    abrirEn('/admin/servicios');
    await userEvent.click(within(await fila(TIENDA.nombre)).getByRole('button', { name: `Eliminar ${TIENDA.nombre}` }));
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(await screen.findByRole('heading', { name: 'No se puede eliminar' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ocultarlo' }));
    expect(await screen.findByText(`"${TIENDA.nombre}" quedó oculto del catálogo.`)).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/servicios/s-tienda/visible')).toEqual({ visible: false });
  });

  it('un servicio sin citas se elimina', async () => {
    let eliminado = false;
    simularApi(PATRICIA, {
      'GET /servicios/gestion': () => ({ estado: 200, cuerpo: eliminado ? [TIENDA, WEB] : [TIENDA, WEB, APP_OCULTA] }),
      'DELETE /servicios/s-app': () => {
        eliminado = true;
        return { estado: 204 };
      },
    });
    abrirEn('/admin/servicios');
    await userEvent.click(within(await fila(APP_OCULTA.nombre)).getByRole('button', { name: `Eliminar ${APP_OCULTA.nombre}` }));
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    await waitFor(() => expect(screen.queryByRole('heading', { level: 2, name: APP_OCULTA.nombre })).not.toBeInTheDocument());
  });
});
