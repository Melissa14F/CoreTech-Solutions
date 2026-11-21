import { screen, within } from '@testing-library/react';

import { LAURA, abrirEn, errores, simularApi } from '../../pruebas/aplicacion';
import { DETALLE } from '../../pruebas/seguimiento';

const RUTA = '/cliente/proyectos/P-0031';

describe('Detalle del proyecto del cliente (HU-04)', () => {
  it('muestra el avance, las fases con sus pasos, la nota y el boceto, y el equipo', async () => {
    simularApi(LAURA, { 'GET /proyectos/P-0031': { estado: 200, cuerpo: DETALLE } });
    abrirEn(RUTA);

    expect(await screen.findByRole('heading', { level: 1, name: 'Tienda en línea Laura En desarrollo' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Avance general 50 %' })).toBeInTheDocument();
    expect(screen.getByText('Fase 2 de 2')).toBeInTheDocument();
    expect(screen.getByText('Desarrollo · 1 de 3 pasos hechos')).toBeInTheDocument();
    expect(screen.getByText('Tu proyecto tiene 2 fases y cada una vale 50 %. Abre cada fase para ver qué se ha hecho.')).toBeInTheDocument();

    // La fase aprobada y la actual, con lo que dijo el Product Owner
    expect(screen.getByRole('heading', { level: 3, name: 'Toma de requerimientos' })).toBeInTheDocument();
    expect(screen.getByText('Esta fase la apruebas tú.')).toBeInTheDocument();
    expect(screen.getByText('Esta fase la cierra el equipo.')).toBeInTheDocument();
    expect(screen.getByText('El catálogo ya muestra los productos con sus filtros.')).toBeInTheDocument();
    expect(screen.getAllByText('Andrés:').length).toBeGreaterThan(0);
    expect(screen.getByRole('img', { name: 'Boceto: Página de producto' })).toHaveAttribute('src', '/api/v1/adjuntos/a-1');
    expect(screen.getByText('Explicación de Pedidos y correos de confirmación.')).toBeInTheDocument();
    expect(screen.getByText('Hecho el 3 nov')).toBeInTheDocument();

    // El equipo y su contacto
    const equipo = screen.getByRole('heading', { level: 2, name: 'Tu equipo' }).closest('section')!;
    expect(within(equipo).getByText('Valeria Rincón')).toBeInTheDocument();
    expect(within(equipo).getByText('Pruebas')).toBeInTheDocument();
    expect(within(equipo).getByRole('link', { name: 'andres.perez@coretechsolutions.co' })).toHaveAttribute('href', 'mailto:andres.perez@coretechsolutions.co');
  });

  it('con el plan en borrador no hay fases: dice que el equipo lo prepara', async () => {
    simularApi(LAURA, { 'GET /proyectos/P-0031': { estado: 200, cuerpo: { ...DETALLE, estadoPlan: 'BORRADOR', fases: [], avancePorcentaje: 0 } } });
    abrirEn(RUTA);
    expect(await screen.findByText('Andrés está preparando el plan de trabajo. Cuando te lo envíe, verás aquí sus fases.')).toBeInTheDocument();
    expect(screen.getByText('Empieza cuando apruebes el plan')).toBeInTheDocument();
  });

  it('un proyecto ajeno responde 404 y se ve «Proyecto no encontrado» sin revelar nada', async () => {
    simularApi(LAURA, { 'GET /proyectos/P-0099': { estado: 404, cuerpo: errores({ mensaje: 'Proyecto no encontrado' }) } });
    abrirEn('/cliente/proyectos/P-0099');

    expect(await screen.findByRole('heading', { level: 1, name: 'Proyecto no encontrado' })).toBeInTheDocument();
    expect(screen.getByText(/No encontramos este proyecto en tu cuenta/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a mis proyectos' })).toHaveAttribute('href', '/cliente/proyectos');
    expect(screen.getByRole('link', { name: 'Ver mis citas' })).toHaveAttribute('href', '/cliente/citas');
    expect(screen.queryByText('P-0099')).not.toBeInTheDocument();
  });

  it('un error del servidor muestra el mensaje y un camino de vuelta', async () => {
    simularApi(LAURA, { 'GET /proyectos/P-0031': { estado: 500 } });
    abrirEn(RUTA);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a mis proyectos' })).toBeInTheDocument();
  });
});
