import { useEffect, useRef, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { Icono, type NombreIcono } from './Icono';

export type OpcionMenu =
  | { texto: string; icono: NombreIcono; a: string; peligro?: boolean }
  | { texto: string; icono: NombreIcono; alElegir: () => void; peligro?: boolean };

/**
 * "Más acciones" de una fila (.menu-acciones del prototipo, sobre &lt;details&gt;). Se cierra al elegir una
 * opción, con Escape (el foco vuelve al botón) o al hacer clic fuera.
 */
export function MenuAcciones({ etiqueta, opciones }: { etiqueta: string; opciones: OpcionMenu[] }) {
  const referencia = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    const alClicFuera = (evento: MouseEvent) => {
      const menu = referencia.current;
      if (menu?.open && !menu.contains(evento.target as Node)) menu.open = false;
    };
    document.addEventListener('click', alClicFuera);
    return () => document.removeEventListener('click', alClicFuera);
  }, []);

  const cerrar = () => {
    if (referencia.current) referencia.current.open = false;
  };

  const contenido = (o: OpcionMenu): ReactNode => (
    <>
      <Icono nombre={o.icono} />
      {o.texto}
    </>
  );
  const clase = (o: OpcionMenu) => ['menu-acciones__opcion', o.peligro && 'menu-acciones__opcion--peligro'].filter(Boolean).join(' ');

  return (
    <details
      ref={referencia}
      className="menu-acciones"
      onKeyDown={(evento) => {
        if (evento.key === 'Escape' && referencia.current?.open) {
          cerrar();
          referencia.current.querySelector('summary')?.focus();
        }
      }}
    >
      <summary className="boton-icono boton-icono--pequeno" aria-label={etiqueta} title="Más acciones">
        <Icono nombre="puntos" />
      </summary>
      <div className="menu-acciones__lista" role="menu">
        {opciones.map((o) =>
          'a' in o ? (
            <Link key={o.texto} className={clase(o)} role="menuitem" to={o.a} onClick={cerrar}>{contenido(o)}</Link>
          ) : (
            <button key={o.texto} type="button" className={clase(o)} role="menuitem" onClick={() => { cerrar(); o.alElegir(); }}>
              {contenido(o)}
            </button>
          ),
        )}
      </div>
    </details>
  );
}
