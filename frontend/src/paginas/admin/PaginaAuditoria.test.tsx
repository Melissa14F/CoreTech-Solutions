import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { PATRICIA, abrirEn, simularApi } from '../../pruebas/aplicacion';
import { PAGINA_AUDITORIA, RESUMEN_AUDITORIA } from '../../pruebas/administracion';
import { nombreCampo, valorCambio } from './PaginaAuditoria';

const VACIA = { ...PAGINA_AUDITORIA, registros: [], total: 0, porCategoria: {} };

function api(pagina = PAGINA_AUDITORIA) {
  return simularApi(PATRICIA, {
    'GET /auditoria': { estado: 200, cuerpo: pagina },
    'GET /auditoria/resumen': { estado: 200, cuerpo: RESUMEN_AUDITORIA },
  });
}

/** URLs pedidas a GET /auditoria (con sus filtros). */
const consultas = (fetchSimulado: ReturnType<typeof api>) =>
  fetchSimulado.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/v1/auditoria?'));

describe('Consultar la auditoría (HU-22)', () => {
  it('muestra cada evento con su usuario, módulo y el valor anterior y nuevo', async () => {
    api();
    abrirEn('/admin/auditoria');
    const fila = (await screen.findByText('Editó un servicio')).closest('tr') as HTMLElement;
    expect(within(fila).getByText('Patricia León')).toBeInTheDocument();
    expect(within(fila).getByText('Sitio web', { selector: '.modulo' })).toBeInTheDocument();
    expect(within(fila).getByText('Descripción')).toBeInTheDocument();
    expect(within(fila).getByText('Conecta tu sitio con pasarelas de pago seguras.')).toHaveClass('cambio__antes');
    expect(within(fila).getByText('Pagos seguros y conciliación automática.')).toHaveClass('cambio__despues');
    expect(within(fila).getByText(/181\.52\.200\.4 · Chrome en macOS · Registro #10472/)).toBeInTheDocument();

    // Un intento sin sesión se resalta como alerta de seguridad
    const alerta = screen.getByText('Bloqueó la cuenta por intentos fallidos').closest('tr') as HTMLElement;
    expect(alerta).toHaveClass('fila--atencion');
    expect(within(alerta).getByText('Sin identificar')).toBeInTheDocument();
    expect(within(alerta).getByText('Sin cambios en los datos.')).toBeInTheDocument();

    expect(screen.getByText('Eventos hoy').previousSibling).toHaveTextContent('5');
    expect(screen.getByRole('button', { name: /Sitio web/ })).toHaveTextContent('1');
    expect(screen.getByText('2 resultados · Del más reciente al más antiguo')).toBeInTheDocument();
    // Es de solo lectura: no hay botones para editar ni borrar
    expect(screen.queryByRole('button', { name: /Editar|Eliminar|Borrar/ })).not.toBeInTheDocument();
  });

  it('pide a la API los filtros de módulo, usuario y periodo', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/auditoria');
    await screen.findByText('Editó un servicio');

    await userEvent.click(screen.getByRole('button', { name: /Seguridad/ }));
    await waitFor(() => expect(consultas(fetchSimulado).at(-1)).toContain('categoria=seguridad'));
    await userEvent.selectOptions(screen.getByLabelText('Filtrar por usuario'), 'u-andres');
    await waitFor(() => expect(consultas(fetchSimulado).at(-1)).toContain('usuarioId=u-andres'));
    await userEvent.selectOptions(screen.getByLabelText('Filtrar por periodo'), 'hoy');
    await waitFor(() => expect(consultas(fetchSimulado).at(-1)).toMatch(/desde=\d{4}-\d{2}-\d{2}&hasta=\d{4}-\d{2}-\d{2}/));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar en la auditoría' }), 'C-0059');
    await waitFor(() => expect(consultas(fetchSimulado).at(-1)).toContain('q=C-0059'));
  });

  it('revisa el rango de fechas antes de pedirlo', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/auditoria');
    await screen.findByText('Editó un servicio');
    await userEvent.selectOptions(screen.getByLabelText('Filtrar por periodo'), 'rango');
    const antes = consultas(fetchSimulado).length;
    await userEvent.type(screen.getByLabelText('Desde'), '2026-11-10');
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-11-01');

    expect(screen.getByRole('alert')).toHaveTextContent('La fecha final debe ser igual o posterior a la inicial.');
    expect(consultas(fetchSimulado).filter((u) => u.includes('hasta=2026-11-01'))).toHaveLength(0);
    expect(consultas(fetchSimulado).length).toBeGreaterThanOrEqual(antes);
  });

  it('sin resultados lo dice y no exporta', async () => {
    api(VACIA);
    abrirEn('/admin/auditoria');
    expect(await screen.findByText('No hay registros con esos filtros')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Exportar CSV' }));
    expect(screen.getByText('No hay eventos para exportar con estos filtros.')).toBeInTheDocument();
  });

  it('nombra los campos y los valores como los lee el administrador', () => {
    expect(nombreCampo('email')).toBe('Correo');
    expect(nombreCampo('fechaEstimadaEntrega')).toBe('Entrega estimada');
    expect(valorCambio(true)).toBe('Sí');
    expect(valorCambio(null)).toBe('—');
    expect(valorCambio(3)).toBe('3');
  });
});
