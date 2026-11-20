import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { ContenidoEdicion } from '../../catalogo/api';
import { CONTENIDO_DE_RESPALDO } from '../../catalogo/ContenidoSitio';
import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';

/** Los 27 textos con un largo permitido sencillo (el real viene de contenido_sitio). */
const LIMITES: Record<string, [number, number]> = { 'portada-etiqueta': [3, 30], 'llamado-titulo': [5, 50], 'contacto-telefono': [7, 14] };
const EDICION: ContenidoEdicion = {
  textos: Object.entries(CONTENIDO_DE_RESPALDO).map(([clave, valor]) => ({
    clave, valor, seccion: 'Portada', minLongitud: LIMITES[clave]?.[0] ?? 3, maxLongitud: LIMITES[clave]?.[1] ?? 300,
  })),
  ultimaPublicacion: { fecha: '2026-11-08T15:00:00Z', nombre: 'Patricia León' },
};

function api(extra = {}) {
  return simularApi(PATRICIA, { 'GET /contenido/edicion': { estado: 200, cuerpo: EDICION }, ...extra });
}

const seccion = async (titulo: string) => (await screen.findByRole('region', { name: new RegExp(titulo) })) as HTMLElement;
const barra = () => screen.getByRole('region', { name: 'Publicar cambios' });

describe('Editar los textos de la página principal (HU-19)', () => {
  it('muestra cada texto con su vista previa y la última publicación', async () => {
    api();
    abrirEn('/admin/contenido');
    const llamado = await seccion('Llamado final');
    expect(within(llamado).getByLabelText('Título')).toHaveValue('¿Listo para empezar tu proyecto?');
    expect(screen.getByText(/Última publicación: 8 nov 2026 por Patricia León/)).toBeInTheDocument();
    expect(within(barra()).getByText('Sin cambios por publicar')).toBeInTheDocument();
  });

  it('al editar, la vista previa cambia y la sección queda "Sin guardar"', async () => {
    api();
    abrirEn('/admin/contenido');
    const llamado = await seccion('Llamado final');
    const titulo = within(llamado).getByLabelText('Título');
    await userEvent.clear(titulo);
    await userEvent.type(titulo, 'Empieza hoy tu proyecto');

    expect(within(llamado).getByText('Sin guardar')).toBeInTheDocument();
    expect(within(llamado).getAllByText('Empieza hoy tu proyecto')).not.toHaveLength(0);
    expect(within(barra()).getByText('1 texto')).toBeInTheDocument();
  });

  it('publica solo los textos que cambiaron', async () => {
    const publicado = { ...EDICION, textos: EDICION.textos.map((t) => (t.clave === 'llamado-titulo' ? { ...t, valor: 'Empieza hoy tu proyecto' } : t)) };
    const fetchSimulado = api({ 'PUT /contenido': { estado: 200, cuerpo: publicado } });
    abrirEn('/admin/contenido');
    const titulo = within(await seccion('Llamado final')).getByLabelText('Título');
    await userEvent.clear(titulo);
    await userEvent.type(titulo, '  Empieza hoy tu proyecto ');
    await userEvent.click(within(barra()).getByRole('button', { name: 'Publicar cambios' }));

    expect(await screen.findByText('1 texto publicado en la página principal.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/contenido')).toEqual({ textos: { 'llamado-titulo': 'Empieza hoy tu proyecto' } });
    await waitFor(() => expect(within(barra()).getByText('Sin cambios por publicar')).toBeInTheDocument());
  });

  it('no publica un texto fuera de su largo ni un teléfono con letras', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/contenido');
    const etiqueta = within(await seccion('Portada')).getByLabelText('Etiqueta superior');
    await userEvent.clear(etiqueta);
    await userEvent.type(etiqueta, 'ab');
    const telefono = within(await seccion('Contacto')).getByLabelText('Teléfono');
    await userEvent.clear(telefono);
    await userEvent.type(telefono, '300-123-4567');
    await userEvent.click(within(barra()).getByRole('button', { name: 'Publicar cambios' }));

    expect(screen.getByText('Escribe al menos 3 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Escribe solo números y espacios, por ejemplo 300 123 4567.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/contenido')).toBeUndefined();
  });

  it('muestra bajo su campo el error que devuelve el servidor (422)', async () => {
    api({ 'PUT /contenido': { estado: 422, cuerpo: errores({ campo: 'contacto-correo', mensaje: 'Escribe un correo válido, por ejemplo nombre@empresa.com.' }) } });
    abrirEn('/admin/contenido');
    const correo = within(await seccion('Contacto')).getByLabelText('Correo');
    await userEvent.clear(correo);
    await userEvent.type(correo, 'contacto@coretech.co');
    await userEvent.click(within(barra()).getByRole('button', { name: 'Publicar cambios' }));

    expect(await screen.findByText('Escribe un correo válido, por ejemplo nombre@empresa.com.')).toBeInTheDocument();
    expect(correo).toHaveAttribute('aria-invalid', 'true');
  });

  it('descartar vuelve a los textos publicados', async () => {
    api();
    abrirEn('/admin/contenido');
    const titulo = within(await seccion('Llamado final')).getByLabelText('Título');
    await userEvent.type(titulo, ' ya');
    await userEvent.click(within(barra()).getByRole('button', { name: 'Descartar' }));

    expect(titulo).toHaveValue('¿Listo para empezar tu proyecto?');
    expect(screen.getByText('Se descartaron los cambios. Los textos publicados siguen igual.')).toBeInTheDocument();
  });
});
