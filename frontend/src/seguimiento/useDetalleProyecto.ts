import { useCallback, useEffect, useState } from 'react';

import { ErrorApi } from '../api/cliente';
import { separarErrores } from '../paginas/acceso/erroresFormulario';
import { seguimiento, type DetalleProyecto } from './api';

export interface ErrorDetalle {
  estado: number;
  mensaje: string;
}

/**
 * Carga el detalle de un proyecto (GET /proyectos/{codigo}). Un 404 significa «no encontrado»: el proyecto no existe o no es
 * del cliente, y la pantalla no distingue cuál de las dos (RN-10). {@code recargar} vuelve a pedirlo sin mostrar el giro.
 */
export function useDetalleProyecto(codigo: string) {
  const [detalle, setDetalle] = useState<DetalleProyecto | null>(null);
  const [error, setError] = useState<ErrorDetalle | null>(null);

  const cargar = useCallback(async (vigente: () => boolean) => {
    try {
      const recibido = await seguimiento.detalle(codigo);
      if (vigente()) {
        setDetalle(recibido);
        setError(null);
      }
    } catch (e) {
      if (vigente()) {
        setError({ estado: e instanceof ErrorApi ? e.estado : 0, mensaje: separarErrores(e).general ?? 'No pudimos cargar el proyecto.' });
      }
    }
  }, [codigo]);

  useEffect(() => {
    let vigente = true;
    setDetalle(null);
    setError(null);
    void cargar(() => vigente);
    return () => { vigente = false; };
  }, [cargar]);

  const recargar = useCallback(() => cargar(() => true), [cargar]);
  return { detalle, error, recargar };
}
