import { PLAN_ENVIADO } from '../pruebas/plan';
import { DETALLE, DETALLE_DEL_EQUIPO } from '../pruebas/seguimiento';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ANDRES, LAURA, PATRICIA, abrirEn, simularApi } from '../pruebas/aplicacion';
import type { Usuario } from '../sesion/ContextoSesion';
import { PANTALLAS, type Area } from './mapa';

/** Con qué sesión se abre cada área: los paneles piden su rol; el sitio público y el acceso, ninguna. */
const SESION_DEL_AREA: Record<Area, Usuario | null> = {
  publico: null,
  acceso: null,
  cliente: LAURA,
  equipo: ANDRES,
  admin: PATRICIA,
};

/** Las pantallas de acceso usan el título del prototipo ("Inicia sesión"); las demás, el del mapa. */
const TITULO_EN_PANTALLA: Record<string, string> = {
  '/': 'Tu idea, hecha software. Y cada avance, a la vista.',
  '/registro': 'Crea tu cuenta',
  '/ingresar': 'Inicia sesión',
  '/restablecer': 'Restablece tu contraseña',
  '/completar-registro': 'Completa tu registro',
  '/legal': 'Tus datos y las reglas del servicio',
  '/cliente/agendar': 'Agendar una cita',
  '/admin': 'Hola, Patricia',
  '/admin/proyectos/:codigo/plan': 'Plan de trabajo Enviado',
  '/equipo/proyectos/:codigo/plan': 'Plan de trabajo Enviado',
  '/cliente/proyectos/:codigo/propuesta': 'Inventario del salón Propuesta por aprobar',
  '/cliente/proyectos/:codigo': 'Tienda en línea Laura En desarrollo',
};

const casos = (Object.entries(PANTALLAS) as [Area, (typeof PANTALLAS)[Area]][]).flatMap(([area, pantallas]) =>
  pantallas.map((p) => [p.ruta.replace(':codigo', 'P-0031'), TITULO_EN_PANTALLA[p.ruta] ?? p.titulo, area] as const),
);

describe('enrutador', () => {
  it.each(casos)('%s muestra la pantalla "%s"', async (ruta, titulo, area) => {
    // Cambiar contraseña necesita sesión (la de alguien con la contraseña temporal)
    simularApi(ruta === '/cambiar-contrasena' ? { ...ANDRES, debeCambiarPassword: true } : SESION_DEL_AREA[area], {
      'GET /proyectos/P-0031/plan': { estado: 200, cuerpo: PLAN_ENVIADO },
      'GET /proyectos/P-0031': { estado: 200, cuerpo: area === 'equipo' ? DETALLE_DEL_EQUIPO : DETALLE },
    });
    abrirEn(ruta);
    expect(await screen.findByRole('heading', { level: 1, name: titulo })).toBeInTheDocument();
  });

  it('tiene el enlace "Saltar al contenido" hacia el contenido principal', async () => {
    simularApi(LAURA);
    abrirEn('/cliente/citas');
    await screen.findByRole('heading', { level: 1, name: 'Mis citas' });
    expect(screen.getByRole('link', { name: 'Saltar al contenido' })).toHaveAttribute('href', '#contenido');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenido');
  });

  it('marca en el menú la sección actual y la muestra en la ruta de navegación', async () => {
    simularApi(PATRICIA);
    abrirEn('/admin/citas');
    expect(await screen.findByRole('link', { name: 'Citas' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('navigation', { name: 'Ruta de navegación' })).toHaveTextContent('PanelCitas');
  });

  it('el botón del menú del panel lo abre y lo cierra', async () => {
    simularApi(ANDRES);
    abrirEn('/equipo/proyectos');
    const boton = await screen.findByRole('button', { name: 'Abrir menú' });

    await userEvent.click(boton);
    expect(document.documentElement).toHaveClass('panel-menu-abierto');
    expect(boton).toHaveAttribute('aria-expanded', 'true');

    await userEvent.keyboard('{Escape}');
    expect(document.documentElement).not.toHaveClass('panel-menu-abierto');
  });

  it('una ruta que no existe muestra "No encontramos esta página"', async () => {
    simularApi(null);
    abrirEn('/no-existe');
    expect(await screen.findByRole('heading', { name: 'No encontramos esta página' })).toBeInTheDocument();
  });

  it('pone el título de la pestaña', async () => {
    simularApi(PATRICIA);
    abrirEn('/admin/auditoria');
    await screen.findByRole('heading', { level: 1, name: 'Auditoría' });
    expect(document.title).toBe('Auditoría · CoreTech Solutions');
  });
});
