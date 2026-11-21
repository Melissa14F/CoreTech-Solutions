import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Tablero } from '../../dashboard/api';
import { PATRICIA, abrirEn, errores, simularApi } from '../../pruebas/aplicacion';

/**
 * El dashboard del administrador (HU-23): indicadores, «Requiere atención» del más grave al menos grave, citas por mes, carga
 * de los equipos, proyectos por fase, servicios, citas por estado y actividad; con el periodo semana, mes o trimestre.
 */

const TABLERO: Tablero = {
  generadoAt: '2026-11-11T16:00:00Z', corte: '2026-11-11', periodo: 'mes', etiquetaPeriodo: 'Citas en noviembre', sinDatos: false,
  indicadores: {
    citas: { etiqueta: 'Citas en noviembre', total: 14, anterior: 12, variacion: 17, etiquetaAnterior: 'octubre' },
    conversion: { proyectos: 33, citas: 91, porcentaje: 36 },
    proyectos: { activos: 7, capacidad: 12, porcentaje: 58, planesPorAprobar: 1 },
    entregas: { aTiempo: 17, total: 20, porcentaje: 85, anio: 2026 },
  },
  conteos: {
    citasSemana: 4, citasSinAsignar: 1, planesPorAprobar: 1, cambiosPorAutorizar: 1, fasesPorAprobar: 2, fasesVencidas: 1, proyectosEnGarantia: 2,
    garantiasCumplidas: 1, solicitudesPendientes: 3, proyectosActivos: 7, capacidad: 12,
  },
  alertas: [
    { id: 'riesgo-P-0024', nivel: 'CRITICA', titulo: 'P-0024 está fuera de plazo', texto: 'Portal de clientes · en Pruebas · María Ruiz · entrega prevista el 30 de octubre.', enlace: '/admin/proyectos', accion: 'Revisar' },
    { id: 'cambio-P-0027', nivel: 'ADVERTENCIA', titulo: 'P-0027 tiene un cambio de plan por autorizar', texto: 'Tienda de repuestos Ortega · María Ruiz propone agregar la fase «Carga del catálogo» · +$1.200.000.', enlace: '/admin/proyectos', accion: 'Autorizar' },
    { id: 'sinequipo-C-0061', nivel: 'INFO', titulo: 'C-0061 no tiene equipo asignado', texto: 'Laura Gómez · Desarrollo de tienda en línea · mar 17, 9:00 a. m. · virtual.', enlace: '/admin/citas', accion: 'Asignar' },
  ],
  citasPorMes: [
    { mes: '2026-06', etiqueta: 'Jun', citas: 11, parcial: false }, { mes: '2026-07', etiqueta: 'Jul', citas: 13, parcial: false },
    { mes: '2026-08', etiqueta: 'Ago', citas: 15, parcial: false }, { mes: '2026-09', etiqueta: 'Sep', citas: 17, parcial: false },
    { mes: '2026-10', etiqueta: 'Oct', citas: 21, parcial: false }, { mes: '2026-11', etiqueta: 'Nov', citas: 14, parcial: true },
  ],
  carga: [
    { id: 'e-1', productOwner: 'Andrés Pérez', activos: 3, maximo: 3, libres: 0 },
    { id: 'e-2', productOwner: 'Sebastián Mora', activos: 1, maximo: 3, libres: 2 },
    { id: 'e-3', productOwner: 'Camila Jaimes', activos: 0, maximo: 3, libres: 3 },
  ],
  proyectosPorFase: [{ fase: 'Plan por aprobar', proyectos: 1 }, { fase: 'Requerimientos', proyectos: 1 }, { fase: 'Desarrollo', proyectos: 2 }],
  servicios: [{ servicio: 'Desarrollo de tienda en línea', citas: 26 }, { servicio: 'Página web corporativa', citas: 13 }],
  citasPorEstado: { pendientes: 3, confirmadas: 5, atendidas: 5, canceladas: 1, total: 14, promedioDiasParaConfirmar: 0.8 },
  actividad: [
    { fecha: '2026-11-11T16:20:00Z', usuario: 'Andrés Pérez', categoria: 'citas', accion: 'Confirmó una cita', entidad: 'cita', entidadId: 'C-0059' },
    { fecha: '2026-11-10T20:05:00Z', usuario: 'Patricia León', categoria: 'proyectos', accion: 'Pausó un proyecto', entidad: 'proyecto', entidadId: 'P-0037' },
  ],
};

const VACIO: Tablero = {
  ...TABLERO, sinDatos: true, alertas: [], carga: [], proyectosPorFase: [], servicios: [], actividad: [],
  indicadores: {
    citas: { etiqueta: 'Citas en noviembre', total: 0, anterior: 0, variacion: null, etiquetaAnterior: 'octubre' },
    conversion: { proyectos: 0, citas: 0, porcentaje: null },
    proyectos: { activos: 0, capacidad: 0, porcentaje: 0, planesPorAprobar: 0 },
    entregas: { aTiempo: 0, total: 0, porcentaje: null, anio: 2026 },
  },
  citasPorEstado: { pendientes: 0, confirmadas: 0, atendidas: 0, canceladas: 0, total: 0, promedioDiasParaConfirmar: null },
  citasPorMes: TABLERO.citasPorMes.map((m) => ({ ...m, citas: 0 })),
};

function abrir(tablero: Tablero = TABLERO) {
  const f = simularApi(PATRICIA, { 'GET /dashboard': { estado: 200, cuerpo: tablero } });
  abrirEn('/admin');
  return f;
}

describe('El dashboard del administrador (HU-23)', () => {
  it('saluda, muestra el corte y los cuatro indicadores clave', async () => {
    abrir();
    await screen.findByRole('list', { name: 'Indicadores clave' });
    expect(screen.getByRole('heading', { name: 'Hola, Patricia' })).toBeInTheDocument();
    expect(screen.getByText(/Así va CoreTech al corte del miércoles 11 de noviembre de 2026/)).toBeInTheDocument();

    const kpis = screen.getByRole('list', { name: 'Indicadores clave' });
    expect(within(kpis).getByText('Citas en noviembre')).toBeInTheDocument();
    expect(within(kpis).getByText('14')).toBeInTheDocument();
    expect(within(kpis).getByText(/frente a octubre/)).toBeInTheDocument();
    expect(within(kpis).getByText('36 %')).toBeInTheDocument();
    expect(within(kpis).getByText('33 de 91 citas en 6 meses')).toBeInTheDocument();
    expect(within(kpis).getByText(/58 % de la capacidad · 1 con plan por aprobar/)).toBeInTheDocument();
    expect(within(kpis).getByText('85 %')).toBeInTheDocument();
    expect(within(kpis).getByText('17 de 20 proyectos en 2026')).toBeInTheDocument();
    expect(within(kpis).getByRole('link', { name: /Citas en noviembre/ })).toHaveAttribute('href', '/admin/citas');
  });

  it('«Requiere atención» lista las alertas del más grave al menos grave, cada una con su enlace', async () => {
    abrir();
    const bloque = (await screen.findByRole('heading', { name: 'Requiere atención' })).closest('section')!;
    expect(within(bloque).getByText('3 asuntos abiertos, del más grave al menos grave')).toBeInTheDocument();
    const items = within(bloque).getAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(items[0]!).toHaveClass('alerta-tablero--critica');
    expect(items[1]!).toHaveClass('alerta-tablero--advertencia');
    expect(items[2]!).toHaveClass('alerta-tablero--info');
    expect(within(items[0]!).getByText('Crítica:')).toBeInTheDocument();
    expect(within(items[0]!).getByText('P-0024 está fuera de plazo')).toBeInTheDocument();
    expect(within(items[0]!).getByRole('link', { name: 'Revisar' })).toHaveAttribute('href', '/admin/proyectos');
    expect(within(items[1]!).getByRole('link', { name: 'Autorizar' })).toHaveAttribute('href', '/admin/proyectos');
    expect(within(items[2]!).getByRole('link', { name: 'Asignar' })).toHaveAttribute('href', '/admin/citas');
  });

  it('muestra los conteos de la historia con su enlace', async () => {
    abrir();
    const bloque = (await screen.findByRole('heading', { name: 'De un vistazo' })).closest('section')!;
    expect(within(bloque).getByRole('link', { name: /4\s*Citas de esta semana/ })).toHaveAttribute('href', '/admin/citas');
    expect(within(bloque).getByRole('link', { name: /1\s*Cambios de plan por autorizar/ })).toBeInTheDocument();
    expect(within(bloque).getByRole('link', { name: /2\s*Fases por aprobar \(1 vencidas\)/ })).toBeInTheDocument();
    expect(within(bloque).getByRole('link', { name: /2\s*En garantía \(1 ya cumplidas\)/ })).toBeInTheDocument();
    expect(within(bloque).getByRole('link', { name: /3\s*Solicitudes de reunión/ })).toHaveAttribute('href', '/admin/solicitudes');
  });

  it('dibuja las citas por mes, la carga del equipo, las fases, los servicios y los estados', async () => {
    abrir();
    const meses = await screen.findByRole('list', { name: 'Citas por mes' });
    expect(within(meses).getAllByRole('listitem')).toHaveLength(6);
    expect(within(meses).getByLabelText('Nov: 14 citas (mes en curso)')).toBeInTheDocument();
    expect(within(meses).getByLabelText('Oct: 21 citas')).toBeInTheDocument();

    const carga = screen.getByRole('heading', { name: 'Carga del equipo' }).closest('section')!;
    expect(within(carga).getByLabelText('3 de 3 proyectos activos')).toBeInTheDocument();
    expect(within(carga).getByText('Sin cupo')).toBeInTheDocument();
    expect(within(carga).getByText('2 cupos libres')).toBeInTheDocument();
    expect(within(carga).getByText('3 cupos libres')).toBeInTheDocument();

    const fases = screen.getByRole('list', { name: 'Proyectos activos en cada fase' });
    expect(within(fases).getByText('Plan por aprobar')).toBeInTheDocument();
    expect(within(fases).getByText('Desarrollo')).toBeInTheDocument();

    const servicios = screen.getByRole('list', { name: 'Citas por servicio en los últimos 6 meses' });
    expect(within(servicios).getByText('Desarrollo de tienda en línea')).toBeInTheDocument();
    expect(within(servicios).getByText('26')).toBeInTheDocument();

    expect(screen.getByRole('img', { name: 'Pendientes 3, confirmadas 5, atendidas 5, canceladas 1' })).toBeInTheDocument();
    expect(screen.getByText('0,8 días')).toBeInTheDocument();
  });

  it('muestra la actividad reciente con quién hizo qué', async () => {
    abrir();
    const actividad = (await screen.findByRole('heading', { name: 'Actividad reciente' })).closest('section')!;
    expect(within(actividad).getByText('Andrés Pérez')).toBeInTheDocument();
    expect(within(actividad).getByText(/confirmó una cita/)).toBeInTheDocument();
    expect(within(actividad).getByText('C-0059')).toBeInTheDocument();
    expect(within(actividad).getByRole('link', { name: 'Ver auditoría completa' })).toHaveAttribute('href', '/admin/auditoria');
  });

  it('cambiar el periodo vuelve a pedir el tablero con ese periodo', async () => {
    const f = abrir();
    await screen.findByRole('list', { name: 'Indicadores clave' });
    expect(screen.getByRole('button', { name: 'Mes' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('button', { name: 'Semana' }));
    await waitFor(() => expect(f.mock.calls.some(([url]) => url === '/api/v1/dashboard?periodo=semana')).toBe(true));
    expect(screen.getByRole('button', { name: 'Semana' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('button', { name: 'Trimestre' }));
    await waitFor(() => expect(f.mock.calls.some(([url]) => url === '/api/v1/dashboard?periodo=trimestre')).toBe(true));
  });

  it('sin datos en el periodo los indicadores muestran 0 y el texto «Sin datos en este periodo»', async () => {
    abrir(VACIO);
    expect(await screen.findByRole('heading', { name: 'Hola, Patricia' })).toBeInTheDocument();
    const kpis = screen.getByRole('list', { name: 'Indicadores clave' });
    expect(within(kpis).getAllByText('0').length).toBeGreaterThan(0);
    expect(within(kpis).getByText('Sin datos en este periodo')).toBeInTheDocument();
    expect(screen.getAllByText('Sin datos en este periodo').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('Todo en orden')).toBeInTheDocument();
    expect(screen.getByText('Aún no hay proyectos entregados en 2026')).toBeInTheDocument();
  });

  it('si falla muestra el error y deja reintentar', async () => {
    let intentos = 0;
    simularApi(PATRICIA, {
      'GET /dashboard': () => { intentos += 1; return intentos === 1 ? { estado: 500, cuerpo: errores({ mensaje: 'Algo salió mal' }) } : { estado: 200, cuerpo: TABLERO }; },
    });
    abrirEn('/admin');
    expect(await screen.findByText('No pudimos cargar el dashboard')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: 'Requiere atención' })).toBeInTheDocument();
  });
});
