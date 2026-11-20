import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { api, configurarSesionVencida } from '../api/cliente';

/** Códigos de rol de la API (en pantalla el segundo es "desarrollador (Product Owner)"). */
export type Rol = 'CLIENTE' | 'PRODUCT_OWNER' | 'ADMINISTRADOR';

/** Respuesta de GET /api/v1/auth/yo. */
export interface Usuario {
  usuarioId: string;
  nombre: string;
  email: string;
  rol: Rol;
  permisos: string[];
  debeCambiarPassword: boolean;
}

export type EstadoSesion =
  | { estado: 'cargando' }
  | { estado: 'anonimo' }
  | { estado: 'conectado'; usuario: Usuario };

export interface DatosRegistro {
  nombre: string;
  email: string;
  celular: string;
  empresa: string;
  password: string;
  confirmacion: string;
  aceptaDatos: boolean;
}

interface ValorSesion {
  sesion: EstadoSesion;
  /** POST /auth/login y luego /auth/yo. Lanza ErrorApi (401, 403, 423) sin redirigir. */
  ingresar: (email: string, password: string, recordarme: boolean) => Promise<Usuario>;
  /** POST /auth/registro: crea la cuenta de cliente (HU-05); la cookie de sesión ya queda puesta. */
  registrar: (datos: DatosRegistro) => Promise<Usuario>;
  /** POST /auth/cambiar-password (primer ingreso con la contraseña temporal o cambio voluntario). */
  cambiarPassword: (passwordActual: string, passwordNueva: string, confirmacion: string) => Promise<Usuario>;
  /**
   * Marca la sesión como abierta y navega al destino en el mismo paso. Las tres funciones anteriores
   * no cambian el estado: así la guarda de /ingresar no redirige por su cuenta antes que la página.
   */
  abrirSesion: (usuario: Usuario, destino: string) => void;
  /** POST /auth/logout y vuelve a /ingresar. */
  cerrarSesion: () => Promise<void>;
  /** Vuelve a leer /auth/yo (por ejemplo, después de cambiar la contraseña). */
  recargar: () => Promise<Usuario | null>;
}

const Contexto = createContext<ValorSesion | null>(null);

const leerUsuario = () => api.obtener<Usuario>('/auth/yo', { sinRedirigirEn401: true });

/**
 * Sesión del usuario (HU-06). La cookie es HttpOnly: el JavaScript no la lee; quién está conectado
 * se sabe con GET /auth/yo al cargar la aplicación y después de iniciar o cerrar sesión.
 * Va dentro del enrutador (en la raíz de las rutas), porque navega al vencer la sesión.
 */
export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<EstadoSesion>({ estado: 'cargando' });
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const rutaActual = useRef(ubicacion.pathname + ubicacion.search);
  rutaActual.current = ubicacion.pathname + ubicacion.search;

  const recargar = useCallback(async () => {
    try {
      const usuario = await leerUsuario();
      setSesion({ estado: 'conectado', usuario });
      return usuario;
    } catch {
      setSesion({ estado: 'anonimo' });
      return null;
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  // Un 401 en cualquier petición: la sesión venció. Se va a iniciar sesión y, al entrar, se vuelve aquí.
  useEffect(() => {
    configurarSesionVencida(() => {
      setSesion({ estado: 'anonimo' });
      navegar(`/ingresar?volver=${encodeURIComponent(rutaActual.current)}`, { replace: true });
    });
  }, [navegar]);

  const ingresar = useCallback(async (email: string, password: string, recordarme: boolean) => {
    await api.crear('/auth/login', { email, password, recordarme }, { sinRedirigirEn401: true });
    return leerUsuario();
  }, []);

  const registrar = useCallback(async (datos: DatosRegistro) => {
    await api.crear('/auth/registro', datos, { sinRedirigirEn401: true });
    return leerUsuario();
  }, []);

  const cambiarPassword = useCallback(async (passwordActual: string, passwordNueva: string, confirmacion: string) => {
    await api.crear('/auth/cambiar-password', { passwordActual, passwordNueva, confirmacion });
    return leerUsuario();
  }, []);

  const abrirSesion = useCallback((usuario: Usuario, destino: string) => {
    setSesion({ estado: 'conectado', usuario });
    navegar(destino, { replace: true });
  }, [navegar]);

  const cerrarSesion = useCallback(async () => {
    try {
      await api.crear('/auth/logout', undefined, { sinRedirigirEn401: true });
    } catch {
      // Aunque falle la red, en este navegador la sesión se da por cerrada.
    }
    setSesion({ estado: 'anonimo' });
    navegar('/ingresar', { replace: true });
  }, [navegar]);

  const valor = useMemo(
    () => ({ sesion, ingresar, registrar, cambiarPassword, abrirSesion, cerrarSesion, recargar }),
    [sesion, ingresar, registrar, cambiarPassword, abrirSesion, cerrarSesion, recargar],
  );
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSesion(): ValorSesion {
  const valor = useContext(Contexto);
  if (!valor) throw new Error('useSesion debe usarse dentro de <ProveedorSesion>');
  return valor;
}

/** El usuario conectado; solo para pantallas que ya pasaron por una guarda de sesión. */
export function useUsuario(): Usuario | null {
  const { sesion } = useSesion();
  return sesion.estado === 'conectado' ? sesion.usuario : null;
}
