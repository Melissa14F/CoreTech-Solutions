import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { api } from '../api/cliente';
import { Alerta } from '../componentes/Alerta';

/** Un proveedor activo en el servidor (GET /auth/proveedores): solo se muestran los que tienen su cliente configurado. */
interface Proveedor {
  codigo: 'GOOGLE' | 'MICROSOFT';
  nombre: string;
  /** Ruta de la API que lleva el navegador a la página del proveedor. */
  url: string;
}

/** Marcas oficiales en línea (Google y Microsoft las exigen tal cual en sus botones de ingreso). */
const LOGOS: Record<Proveedor['codigo'], JSX.Element> = {
  GOOGLE: (
    <svg viewBox="0 0 48 48" aria-hidden="true" focusable="false">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  ),
  MICROSOFT: (
    <svg viewBox="0 0 21 21" aria-hidden="true" focusable="false">
      <rect x="1" y="1" width="9" height="9" fill="#F25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7FBA00" />
      <rect x="1" y="11" width="9" height="9" fill="#00A4EF" />
      <rect x="11" y="11" width="9" height="9" fill="#FFB900" />
    </svg>
  ),
};

/**
 * «Continuar con Google» y «Continuar con Microsoft» (RF-50, D-50), con el separador «o» antes del formulario de
 * correo y contraseña. Son enlaces normales: el navegador va al proveedor y vuelve a la API, que redirige a la SPA.
 * Si el servidor no tiene ningún proveedor configurado (o la API no responde), no se muestra nada.
 */
export function BotonesIngresoExterno({ accion }: { accion: 'ingresar' | 'registrar' }) {
  const [proveedores, setProveedores] = useState<Proveedor[]>([]);

  useEffect(() => {
    let activo = true;
    api.obtener<Proveedor[]>('/auth/proveedores', { sinRedirigirEn401: true })
      .then((lista) => activo && setProveedores(lista))
      .catch(() => undefined);
    return () => {
      activo = false;
    };
  }, []);

  if (proveedores.length === 0) return null;
  return (
    <>
      <ul className="ingreso-externo" aria-label={accion === 'ingresar' ? 'Ingresar con otra cuenta' : 'Registrarse con otra cuenta'}>
        {proveedores.map((p) => (
          <li key={p.codigo}>
            <a className="boton boton--contorno boton--bloque boton-externo" href={p.url}>
              {LOGOS[p.codigo]}
              <span>Continuar con {p.nombre}</span>
            </a>
          </li>
        ))}
      </ul>
      <p className="separador">o {accion === 'ingresar' ? 'ingresa' : 'regístrate'} con tu correo</p>
    </>
  );
}

const PROVEEDORES: Record<string, string> = { google: 'Google', microsoft: 'Microsoft' };

/** Lo que se le dice a quien vuelve a «Iniciar sesión» sin haber entrado (?externo=motivo&proveedor=google). */
function mensajeDe(motivo: string, proveedor: string): string | null {
  const de = PROVEEDORES[proveedor] ?? 'esa cuenta';
  switch (motivo) {
    case 'cancelado': return `Cancelaste el ingreso con ${de}. Puedes intentarlo de nuevo o usar tu correo y contraseña.`;
    case 'vencido': return 'El ingreso tardó demasiado. Vuelve a intentarlo.';
    case 'sin_correo': return `${de} no nos compartió tu correo y lo necesitamos para crear tu cuenta. Usa tu correo y contraseña.`;
    case 'correo_sin_verificar': return `${de} aún no ha verificado tu correo. Verifícalo allá o ingresa con tu correo y contraseña.`;
    case 'solo_clientes': return 'Esa cuenta es del equipo de CoreTech: ingresa con tu correo y contraseña.';
    case 'desactivada': return 'Tu cuenta está desactivada, contacta al administrador';
    case 'bloqueada': return 'Cuenta bloqueada 15 minutos por seguridad';
    case 'no_disponible': return 'Esa forma de ingresar no está disponible por ahora. Usa tu correo y contraseña.';
    case 'error': return `No pudimos ingresar con ${de}. Intenta de nuevo en unos minutos.`;
    default: return null;
  }
}

/** Alerta de «Iniciar sesión» cuando el ingreso con Google o Microsoft no se pudo completar. */
export function AvisoIngresoExterno() {
  const [parametros] = useSearchParams();
  const motivo = parametros.get('externo');
  const texto = motivo ? mensajeDe(motivo, parametros.get('proveedor') ?? '') : null;
  return texto ? <Alerta tipo="error" titulo={texto} /> : null;
}
