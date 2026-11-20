import { useEffect, useId, useRef, type ReactNode } from 'react';

import { Icono, type NombreIcono } from './Icono';

type TonoDialogo = 'acento' | 'peligro' | 'exito' | 'advertencia';

interface PropiedadesDialogo {
  abierto: boolean;
  /** Se llama al pulsar "Cerrar", Escape o el fondo: el padre decide cerrar (estado controlado). */
  alCerrar: () => void;
  titulo: string;
  texto?: ReactNode;
  icono?: NombreIcono;
  tono?: TonoDialogo;
  ancho?: boolean;
  /** Clase extra del diálogo (por ejemplo, dialogo--editor). */
  clase?: string;
  /** Contenido principal (formulario, resumen…). */
  children?: ReactNode;
  /** Botones del pie (Cancelar, Confirmar…). */
  acciones?: ReactNode;
}

/**
 * Diálogo modal del prototipo sobre el elemento nativo &lt;dialog&gt;: el navegador encierra el foco,
 * cierra con Escape y devuelve el foco al elemento que lo abrió.
 */
export function Dialogo({ abierto, alCerrar, titulo, texto, icono, tono = 'acento', ancho = false, clase, children, acciones }: PropiedadesDialogo) {
  const referencia = useRef<HTMLDialogElement>(null);
  const idTitulo = useId();

  useEffect(() => {
    const dialogo = referencia.current;
    if (!dialogo) return;
    if (abierto && !dialogo.open) {
      // jsdom (pruebas) no implementa showModal: allí basta con el atributo open.
      if (typeof dialogo.showModal === 'function') dialogo.showModal();
      else dialogo.setAttribute('open', '');
    } else if (!abierto && dialogo.open) {
      // Igual que showModal: jsdom tampoco implementa close.
      if (typeof dialogo.close === 'function') dialogo.close();
      else dialogo.removeAttribute('open');
    }
  }, [abierto]);

  return (
    <dialog
      ref={referencia}
      className={['dialogo', ancho && 'dialogo--ancho', clase].filter(Boolean).join(' ')}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault(); // Escape: lo cierra el padre, para mantener el estado sincronizado
        alCerrar();
      }}
      onClick={(evento) => {
        if (evento.target === evento.currentTarget) alCerrar(); // clic en el fondo
      }}
    >
      <div className="dialogo__cabecera">
        {icono && (
          <span className={`dialogo__icono dialogo__icono--${tono}`}>
            <Icono nombre={icono} grande />
          </span>
        )}
        <div>
          <h2 className="dialogo__titulo" id={idTitulo}>{titulo}</h2>
          {texto && <p className="dialogo__texto">{texto}</p>}
        </div>
        <button type="button" className="boton-icono boton-icono--pequeno" onClick={alCerrar} aria-label="Cerrar">
          <Icono nombre="cerrar" />
        </button>
      </div>
      {children && <div className="dialogo__cuerpo">{children}</div>}
      {acciones && <div className="dialogo__acciones">{acciones}</div>}
    </dialog>
  );
}
