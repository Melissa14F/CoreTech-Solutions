import { PANELES, type Panel } from '../rutas/mapa';
import type { Rol } from './ContextoSesion';

/** Panel de cada rol (RF-02): el cliente, el desarrollador (Product Owner) y el administrador. */
export const PANEL_DE_ROL: Record<Rol, Panel> = {
  CLIENTE: 'cliente',
  PRODUCT_OWNER: 'equipo',
  ADMINISTRADOR: 'admin',
};

/** Página de inicio del panel del rol: /cliente/proyectos, /equipo/proyectos o /admin. */
export function panelDeRol(rol: Rol): string {
  return PANELES[PANEL_DE_ROL[rol]].inicio;
}

/**
 * El destino de ?volver=…, solo si es una ruta de esta aplicación. Rechaza direcciones de otro sitio
 * ("https://…", "//otro.com") para que nadie use el enlace de inicio de sesión para llevar al usuario fuera.
 */
export function destinoSeguro(volver: string | null): string | null {
  if (!volver || !volver.startsWith('/') || volver.startsWith('//') || volver.startsWith('/\\')) return null;
  return volver;
}

/** Adónde va el usuario después de iniciar sesión: a la página que pedía si es de su panel; si no, a su inicio. */
export function destinoAlIngresar(rol: Rol, volver: string | null): string {
  const destino = destinoSeguro(volver);
  const inicio = panelDeRol(rol);
  const prefijo = `/${PANEL_DE_ROL[rol]}`;
  return destino && (destino === prefijo || destino.startsWith(`${prefijo}/`)) ? destino : inicio;
}
