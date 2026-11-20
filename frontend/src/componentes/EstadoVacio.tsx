import type { ReactNode } from 'react';

import { Icono, type NombreIcono } from './Icono';

interface PropiedadesEstadoVacio {
  titulo: string;
  texto?: ReactNode;
  icono?: NombreIcono;
  /** Acción sugerida, por ejemplo un botón "Agendar cita" o "Limpiar filtros". */
  accion?: ReactNode;
}

/** Mensaje cuando no hay datos que mostrar (.sin-resultados del prototipo). */
export function EstadoVacio({ titulo, texto, icono = 'buscar', accion }: PropiedadesEstadoVacio) {
  return (
    <div className="sin-resultados">
      <span className="sin-resultados__icono">
        <Icono nombre={icono} grande />
      </span>
      <h2 className="sin-resultados__titulo">{titulo}</h2>
      {texto && <p>{texto}</p>}
      {accion}
    </div>
  );
}
