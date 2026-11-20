import { Icono } from './Icono';

interface PropiedadesPaginacion {
  /** Página actual, desde 0. */
  pagina: number;
  totalPaginas: number;
  alCambiar: (pagina: number) => void;
  /** Para lectores de pantalla: "Páginas de la auditoría". */
  etiqueta: string;
}

/** Anterior / "Página 2 de 5" / Siguiente, bajo una tabla. No se muestra si hay una sola página. */
export function Paginacion({ pagina, totalPaginas, alCambiar, etiqueta }: PropiedadesPaginacion) {
  if (totalPaginas <= 1) return null;
  return (
    <nav className="paginacion" aria-label={etiqueta}>
      <button type="button" className="boton boton--contorno boton--pequeno" disabled={pagina === 0} onClick={() => alCambiar(pagina - 1)}>
        <Icono nombre="chevronIzquierda" />
        Anterior
      </button>
      <span className="paginacion__estado" aria-live="polite">Página {pagina + 1} de {totalPaginas}</span>
      <button type="button" className="boton boton--contorno boton--pequeno" disabled={pagina >= totalPaginas - 1} onClick={() => alCambiar(pagina + 1)}>
        Siguiente
        <Icono nombre="chevronDerecha" />
      </button>
    </nav>
  );
}
