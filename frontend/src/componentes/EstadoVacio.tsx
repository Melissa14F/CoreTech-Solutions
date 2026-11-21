import type { ReactNode } from 'react';

import { Icono, type NombreIcono } from './Icono';

interface PropiedadesEstadoVacio {
  titulo: string;
  texto?: ReactNode;
  icono?: NombreIcono;
  /** Acción sugerida, por ejemplo un botón "Agendar cita" o "Limpiar filtros". */
  accion?: ReactNode;
  /** Nivel del título: h2 por defecto; una página que solo muestra este mensaje (por ejemplo, la de «no encontrada») usa h1. */
  encabezado?: 'h1' | 'h2';
}

/** Mensaje cuando no hay datos que mostrar (.sin-resultados del prototipo). */
export function EstadoVacio({ titulo, texto, icono = 'buscar', accion, encabezado: Encabezado = 'h2' }: PropiedadesEstadoVacio) {
  return (
    <div className="sin-resultados">
      <span className="sin-resultados__icono">
        <Icono nombre={icono} grande />
      </span>
      <Encabezado className="sin-resultados__titulo">{titulo}</Encabezado>
      {texto && <p>{texto}</p>}
      {accion}
    </div>
  );
}
