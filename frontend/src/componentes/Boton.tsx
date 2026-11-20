import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { Icono, type NombreIcono } from './Icono';

export type VarianteBoton = 'acento' | 'oscuro' | 'contorno' | 'fantasma' | 'peligro' | 'exito' | 'claro' | 'transparente';
export type TamanoBoton = 'pequeno' | 'normal' | 'grande';

interface PropiedadesComunes {
  variante?: VarianteBoton;
  tamano?: TamanoBoton;
  bloque?: boolean;
  icono?: NombreIcono;
  iconoFinal?: NombreIcono;
  children: ReactNode;
}

export function clasesBoton({ variante = 'acento', tamano = 'normal', bloque = false, cargando = false }:
  { variante?: VarianteBoton; tamano?: TamanoBoton; bloque?: boolean; cargando?: boolean }): string {
  return [
    'boton',
    `boton--${variante}`,
    tamano !== 'normal' && `boton--${tamano}`,
    bloque && 'boton--bloque',
    cargando && 'esta-cargando',
  ].filter(Boolean).join(' ');
}

type PropiedadesBoton = PropiedadesComunes & Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  /** Mientras la acción responde: deshabilita el botón y muestra el giro del prototipo. */
  cargando?: boolean;
};

/** Botón del prototipo (.boton y sus variantes). Por defecto es type="button" para no enviar formularios sin querer. */
export function Boton({ variante, tamano, bloque, icono, iconoFinal, cargando = false, type = 'button',
  className, disabled, children, ...resto }: PropiedadesBoton) {
  return (
    <button
      type={type}
      className={[clasesBoton({ variante, tamano, bloque, cargando }), className].filter(Boolean).join(' ')}
      disabled={disabled || cargando}
      aria-busy={cargando || undefined}
      {...resto}
    >
      {icono && <Icono nombre={icono} />}
      {children}
      {iconoFinal && <Icono nombre={iconoFinal} />}
    </button>
  );
}

/** Enlace interno con la apariencia de un botón (navega con React Router). */
export function EnlaceBoton({ a, variante, tamano, bloque, icono, iconoFinal, children }: PropiedadesComunes & { a: string }) {
  return (
    <Link to={a} className={clasesBoton({ variante, tamano, bloque })}>
      {icono && <Icono nombre={icono} />}
      {children}
      {iconoFinal && <Icono nombre={iconoFinal} />}
    </Link>
  );
}
