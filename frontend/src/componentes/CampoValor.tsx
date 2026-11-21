import { useId, useLayoutEffect, useRef, type ChangeEvent } from 'react';

import { conPuntos, soloDigitos } from '../proyectos/plan';
import { Icono } from './Icono';

interface PropiedadesCampoValor {
  etiqueta: string;
  /** Solo los dígitos del valor en pesos ("7800000"); vacío si no hay valor. */
  valor: string;
  alCambiar: (digitos: string) => void;
  ayuda?: string;
  error?: string;
  id?: string;
}

/**
 * Valor en pesos colombianos, sin centavos (prototipo: .campo-moneda). Pone los puntos de miles mientras se escribe y
 * deja el cursor junto al mismo dígito, aunque se edite en medio del número. Quien lo usa guarda solo los dígitos.
 */
export function CampoValor({ etiqueta, valor, alCambiar, ayuda, error, id }: PropiedadesCampoValor) {
  const entrada = useRef<HTMLInputElement>(null);
  /** Cuántos dígitos había a la izquierda del cursor en el último cambio. */
  const digitosAntesDelCursor = useRef<number | null>(null);

  useLayoutEffect(() => {
    const control = entrada.current;
    const antes = digitosAntesDelCursor.current;
    digitosAntesDelCursor.current = null;
    if (!control || antes === null || document.activeElement !== control) return;
    let posicion = 0;
    let contados = 0;
    while (posicion < control.value.length && contados < antes) {
      if (/\d/.test(control.value[posicion] ?? '')) contados += 1;
      posicion += 1;
    }
    control.setSelectionRange(posicion, posicion);
  }, [valor]);

  function cambio(evento: ChangeEvent<HTMLInputElement>) {
    const texto = evento.target.value;
    const cursor = evento.target.selectionStart ?? texto.length;
    digitosAntesDelCursor.current = soloDigitos(texto.slice(0, cursor)).length;
    alCambiar(soloDigitos(texto).replace(/^0+/, '').slice(0, 9));
  }

  const generado = useId();
  const idControl = id ?? `valor-${generado}`;
  const describe = [ayuda ? `${idControl}-ayuda` : null, error ? `${idControl}-mensaje` : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={['campo', error && 'campo--invalido'].filter(Boolean).join(' ')}>
      <label className="campo__etiqueta" htmlFor={idControl}>{etiqueta}</label>
      <div className="campo-moneda">
        <span className="campo-moneda__simbolo" aria-hidden="true">$</span>
        <input
          ref={entrada} id={idControl} className="campo__control" type="text" inputMode="numeric" maxLength={13}
          placeholder="0" autoComplete="off" value={conPuntos(valor)} onChange={cambio}
          aria-describedby={describe} aria-invalid={error ? true : undefined}
        />
      </div>
      {ayuda && <p className="campo__ayuda" id={`${idControl}-ayuda`}>{ayuda}</p>}
      {error && (
        <p className="campo__mensaje" id={`${idControl}-mensaje`} role="alert">
          <Icono nombre="alerta" />
          <span className="campo__mensaje-texto">{error}</span>
        </p>
      )}
    </div>
  );
}
