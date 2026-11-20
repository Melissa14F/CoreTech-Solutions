import { EnlaceBoton } from '../componentes/Boton';
import { EstadoVacio } from '../componentes/EstadoVacio';
import { useTituloDocumento } from '../componentes/useTituloDocumento';

/** Ruta que no existe. */
export function PaginaNoEncontrada() {
  useTituloDocumento('Página no encontrada');
  return (
    <main id="contenido" className="contenedor pagina-no-encontrada" tabIndex={-1}>
      <EstadoVacio
        titulo="No encontramos esta página"
        texto="Puede que el enlace esté mal escrito o que la página ya no exista."
        accion={<EnlaceBoton a="/" variante="contorno" tamano="pequeno">Ir al inicio</EnlaceBoton>}
      />
    </main>
  );
}
