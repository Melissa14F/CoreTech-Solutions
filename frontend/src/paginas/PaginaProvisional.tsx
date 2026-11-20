import { Alerta } from '../componentes/Alerta';
import { Insignia } from '../componentes/Insignia';
import { useTituloDocumento } from '../componentes/useTituloDocumento';
import type { Pantalla } from '../rutas/mapa';

/**
 * Página provisional de P04: dice qué pantalla es, qué HU la construyen y cuál es su referencia en el
 * prototipo. Cada una se reemplaza por la pantalla real en el paso de su HU (docs/09-prompts-desarrollo.md).
 */
export function PaginaProvisional({ pantalla }: { pantalla: Pantalla }) {
  useTituloDocumento(pantalla.titulo);
  return (
    <section className="provisional contenedor">
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">{pantalla.titulo}</h1>
          <p className="encabezado-vista__texto">
            Referencia visual: <code>prototipo/{pantalla.prototipo}</code>
          </p>
        </div>
      </div>
      <Alerta titulo="Pantalla en construcción">
        Esta vista se construye con sus historias de usuario; mientras tanto, la ruta ya existe.
      </Alerta>
      {pantalla.hu.length > 0 && (
        <p className="provisional__hu" aria-label="Historias de usuario">
          {pantalla.hu.map((hu) => <Insignia key={hu} variante="confirmada">{hu}</Insignia>)}
        </p>
      )}
    </section>
  );
}
