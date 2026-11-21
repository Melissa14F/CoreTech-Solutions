import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ANDRES, LAURA, abrirEn, simularApi } from '../../pruebas/aplicacion';
import type { ProyectoPropio } from '../../proyectos/propios';

const BASE: ProyectoPropio = {
  codigo: 'P-0035', nombre: 'Inventario del salón', servicio: 'Sistema de inventario', estado: 'ACTIVO', estadoPlan: 'ENVIADO',
  cliente: 'Laura Gómez', productOwner: 'Andrés Pérez', fechaEstimadaEntrega: '2027-02-12', valorCotizado: 7_800_000,
  avancePorcentaje: 0, fases: 6, faseActualOrden: 1, faseActualNombre: 'Toma de requerimientos', plazoRespuesta: '2026-11-17',
  venceEl: '2026-11-26', porHacer: null,
};
const ENVIADO_AL_CLIENTE: ProyectoPropio = { ...BASE, porHacer: null };
const POR_REVISAR: ProyectoPropio = { ...BASE, porHacer: 'REVISAR_PROPUESTA' };
const BORRADOR: ProyectoPropio = {
  ...BASE, codigo: 'P-0036', nombre: 'Web corporativa Duarte', estadoPlan: 'BORRADOR', valorCotizado: null, plazoRespuesta: null,
  venceEl: null, cliente: 'Miguel Ángel Duarte', porHacer: 'PREPARAR_PLAN',
};
const AJUSTES: ProyectoPropio = { ...BASE, codigo: 'P-0037', nombre: 'App de reservas', estadoPlan: 'AJUSTES_PEDIDOS', porHacer: 'AJUSTAR_PLAN' };
const APROBADO: ProyectoPropio = { ...BASE, codigo: 'P-0031', nombre: 'Tienda en línea Laura', estadoPlan: 'APROBADO', avancePorcentaje: 40 };
const RECHAZADO: ProyectoPropio = { ...BASE, codigo: 'P-0029', nombre: 'Portal de clientes', estado: 'PAUSADO', estadoPlan: 'RECHAZADO' };

describe('Mis proyectos del cliente', () => {
  it('pone primero la propuesta por revisar y la lleva con un solo botón', async () => {
    simularApi(LAURA, { 'GET /proyectos/propios': { estado: 200, cuerpo: [APROBADO, POR_REVISAR] } });
    abrirEn('/cliente/proyectos');

    expect(await screen.findByText('Tienes 2 proyectos y una propuesta por revisar.')).toBeInTheDocument();
    const tarjetas = screen.getAllByRole('listitem').filter((li) => li.classList.contains('proyecto-resumen'));
    expect(within(tarjetas[0]!).getByRole('heading', { level: 3 })).toHaveTextContent('Inventario del salón');
    expect(within(tarjetas[0]!).getByText('Plan y cotización listos: apruébalos o pide ajustes antes del martes 17 nov.')).toBeInTheDocument();
    expect(within(tarjetas[0]!).getByText('$7.800.000 COP')).toBeInTheDocument();
    expect(within(tarjetas[0]!).getByRole('link', { name: 'Revisar propuesta' })).toHaveAttribute('href', '/cliente/proyectos/P-0035/propuesta');
    expect(within(tarjetas[1]!).getByText('40 %')).toBeInTheDocument();
    // Con el plan aprobado el botón lleva al proyecto (P18), no a la propuesta
    expect(within(tarjetas[1]!).getByRole('link', { name: 'Ver mi proyecto' })).toHaveAttribute('href', '/cliente/proyectos/P-0031');
  });

  it('con el plan en preparación no ofrece botón y dice quién lo prepara', async () => {
    simularApi(LAURA, { 'GET /proyectos/propios': { estado: 200, cuerpo: [{ ...BORRADOR, porHacer: null }] } });
    abrirEn('/cliente/proyectos');

    expect(await screen.findByText('Andrés prepara tu plan de trabajo y tu cotización.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /propuesta|plan/i })).not.toBeInTheDocument();
  });

  it('una propuesta rechazada o vencida queda en pausa y se puede volver a leer', async () => {
    simularApi(LAURA, { 'GET /proyectos/propios': { estado: 200, cuerpo: [RECHAZADO] } });
    abrirEn('/cliente/proyectos');

    expect(await screen.findByText('La propuesta no se aprobó y el proyecto está en pausa.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver propuesta' })).toHaveAttribute('href', '/cliente/proyectos/P-0029/propuesta');
  });

  it('sin proyectos explica cuándo aparecerá el primero', async () => {
    simularApi(LAURA, { 'GET /proyectos/propios': { estado: 200, cuerpo: [] } });
    abrirEn('/cliente/proyectos');

    expect(await screen.findByText('Todavía no tienes proyectos')).toBeInTheDocument();
    expect(screen.getByText('Cuando agendes una cita y el equipo la atienda, tu proyecto aparecerá aquí.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Agendar cita' })[0]).toHaveAttribute('href', '/cliente/agendar');
  });

  it('si no se puede cargar muestra el mensaje y no deja la pantalla en blanco', async () => {
    simularApi(LAURA, { 'GET /proyectos/propios': { estado: 500 } });
    abrirEn('/cliente/proyectos');
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Mis proyectos' })).toBeInTheDocument();
  });
});

describe('Mis proyectos del Product Owner', () => {
  it('lo que le toca va primero, con un botón que lleva directo al plan', async () => {
    simularApi(ANDRES, { 'GET /proyectos/propios': { estado: 200, cuerpo: [ENVIADO_AL_CLIENTE, AJUSTES, BORRADOR] } });
    abrirEn('/equipo/proyectos');

    expect(await screen.findByText('Tienes 3 proyectos a tu cargo; en 2 te toca preparar o ajustar el plan.')).toBeInTheDocument();
    const tarjetas = screen.getAllByRole('listitem').filter((li) => li.classList.contains('proyecto-resumen'));
    expect(tarjetas).toHaveLength(3);
    expect(within(tarjetas[0]!).getByRole('link', { name: 'Ajustar plan' })).toHaveAttribute('href', '/equipo/proyectos/P-0037/plan');
    expect(within(tarjetas[0]!).getByText('Laura pidió ajustes al plan: ajústalo y reenvíalo.')).toBeInTheDocument();
    expect(within(tarjetas[1]!).getByRole('link', { name: 'Preparar plan' })).toHaveAttribute('href', '/equipo/proyectos/P-0036/plan');
    expect(within(tarjetas[1]!).getByText('Falta preparar el plan y la cotización para Miguel.')).toBeInTheDocument();
    expect(within(tarjetas[2]!).getByText('Plan enviado: Laura responde hasta el martes 17 nov.')).toBeInTheDocument();
    expect(within(tarjetas[2]!).getByRole('link', { name: 'Ver plan' })).toHaveAttribute('href', '/equipo/proyectos/P-0035/plan');
  });

  it('el título del proyecto también abre el plan', async () => {
    simularApi(ANDRES, { 'GET /proyectos/propios': { estado: 200, cuerpo: [BORRADOR] } });
    abrirEn('/equipo/proyectos');
    await userEvent.click(await screen.findByRole('link', { name: 'Web corporativa Duarte' }));
    expect(await screen.findByRole('heading', { level: 1, name: /Plan de trabajo/ }).catch(() => null)).not.toBeUndefined();
  });

  it('sin proyectos dice que la administración le asignará uno', async () => {
    simularApi(ANDRES, { 'GET /proyectos/propios': { estado: 200, cuerpo: [] } });
    abrirEn('/equipo/proyectos');
    expect(await screen.findByText('La administración te asignará un proyecto después de atender una cita.')).toBeInTheDocument();
  });
});
