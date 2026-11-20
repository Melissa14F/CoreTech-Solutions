import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Al enviar un formulario con errores, lleva el foco al primer campo marcado (aria-invalid), como el
 * prototipo: quien usa teclado o lector de pantalla llega directo a lo que debe corregir.
 * Devuelve la referencia del formulario y la función que se llama después de marcar los errores.
 */
export function useEnfocarError() {
  const formulario = useRef<HTMLFormElement>(null);
  const [pedido, setPedido] = useState(0);

  useEffect(() => {
    if (pedido === 0) return;
    formulario.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [pedido]);

  const enfocar = useCallback(() => setPedido((n) => n + 1), []);
  return { formulario, enfocar };
}
