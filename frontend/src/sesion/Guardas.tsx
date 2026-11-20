import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { Cargando } from '../componentes/Cargando';
import type { Panel } from '../rutas/mapa';
import { useSesion } from './ContextoSesion';
import { PANEL_DE_ROL, panelDeRol } from './rutas';

/**
 * Panel solo para su rol (RNF-01): sin sesión → iniciar sesión y volver aquí; con la contraseña temporal →
 * cambiarla primero (RF-05); con otro rol → a su propio panel. El backend también lo impide (403/404):
 * esto solo evita mostrar una pantalla que no le corresponde.
 */
export function RutaProtegida({ panel, children }: { panel: Panel; children: ReactNode }) {
  const { sesion } = useSesion();
  const { pathname, search } = useLocation();

  if (sesion.estado === 'cargando') return <Cargando texto="Cargando tu sesión…" />;
  if (sesion.estado === 'anonimo') {
    return <Navigate to={`/ingresar?volver=${encodeURIComponent(pathname + search)}`} replace />;
  }
  const { usuario } = sesion;
  if (usuario.debeCambiarPassword) return <Navigate to="/cambiar-contrasena" replace />;
  if (PANEL_DE_ROL[usuario.rol] !== panel) return <Navigate to={panelDeRol(usuario.rol)} replace />;
  return <>{children}</>;
}

/** Crear cuenta e iniciar sesión: quien ya tiene sesión va directo a su panel. */
export function RutaSoloAnonimos({ children }: { children: ReactNode }) {
  const { sesion } = useSesion();
  if (sesion.estado === 'cargando') return <Cargando texto="Cargando tu sesión…" />;
  if (sesion.estado === 'conectado') {
    const { usuario } = sesion;
    return <Navigate to={usuario.debeCambiarPassword ? '/cambiar-contrasena' : panelDeRol(usuario.rol)} replace />;
  }
  return <>{children}</>;
}

/** Cambiar contraseña: necesita sesión (con la contraseña actual o la temporal). */
export function RutaConSesion({ children }: { children: ReactNode }) {
  const { sesion } = useSesion();
  const { pathname } = useLocation();
  if (sesion.estado === 'cargando') return <Cargando texto="Cargando tu sesión…" />;
  if (sesion.estado === 'anonimo') return <Navigate to={`/ingresar?volver=${encodeURIComponent(pathname)}`} replace />;
  return <>{children}</>;
}
