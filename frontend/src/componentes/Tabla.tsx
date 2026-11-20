import type { ReactNode } from 'react';

export interface ColumnaTabla<T> {
  /** Clave estable de la columna. */
  clave: string;
  /** Encabezado; también es la etiqueta de la celda cuando la fila se ve como tarjeta (hasta 1365 px). */
  titulo: string;
  celda: (fila: T) => ReactNode;
  /** Oculta el encabezado a la vista pero lo deja para lectores de pantalla (por ejemplo, "Acciones"). */
  tituloOculto?: boolean;
  /** Celda que ocupa todo el ancho de la tarjeta en pantallas medianas (.celda--ancha). */
  ancha?: boolean;
  /** Clase extra de la celda (por ejemplo, celda-evento). */
  clase?: string;
}

interface PropiedadesTabla<T> {
  /** Descripción de la tabla para lectores de pantalla. */
  titulo: string;
  columnas: ColumnaTabla<T>[];
  filas: T[];
  claveFila: (fila: T) => string;
  /** Contenido cuando no hay filas (normalmente un EstadoVacio). */
  vacio?: ReactNode;
  /** Clase de la tabla además de tabla-datos (por ejemplo, tabla-auditoria). */
  clase?: string;
  /** Clase de cada fila (por ejemplo, fila--apagada para una cuenta inactiva). */
  claseFila?: (fila: T) => string | undefined;
}

/**
 * Tabla de gestión del prototipo (.tabla-datos). Hasta 1365 px cada fila se muestra como tarjeta:
 * por eso cada celda lleva data-etiqueta con el título de su columna (lo usa plantillas/tabla.css).
 */
export function Tabla<T>({ titulo, columnas, filas, claveFila, vacio, clase, claseFila }: PropiedadesTabla<T>) {
  return (
    <div className="tabla-contenedor">
      <table className={['tabla-datos', clase].filter(Boolean).join(' ')}>
        <caption className="solo-lector">{titulo}</caption>
        <thead>
          <tr>
            {columnas.map((c) => (
              <th key={c.clave} scope="col">
                {c.tituloOculto ? <span className="solo-lector">{c.titulo}</span> : c.titulo}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((fila) => (
            <tr key={claveFila(fila)} className={claseFila?.(fila) || undefined}>
              {columnas.map((c) => (
                <td key={c.clave} data-etiqueta={c.tituloOculto ? undefined : c.titulo} className={[c.ancha && 'celda--ancha', c.clase].filter(Boolean).join(' ') || undefined}>
                  {c.celda(fila)}
                </td>
              ))}
            </tr>
          ))}
          {filas.length === 0 && vacio && (
            <tr className="tabla-datos__vacio">
              <td colSpan={columnas.length}>{vacio}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
