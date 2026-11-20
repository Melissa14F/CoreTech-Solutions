/** Indicador de carga accesible: el lector de pantalla anuncia el texto sin interrumpir. */
export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="cargando" role="status" aria-live="polite">
      <span className="cargando__giro" aria-hidden="true" />
      <span>{texto}</span>
    </div>
  );
}
