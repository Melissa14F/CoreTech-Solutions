import { EnlaceBoton } from '../../componentes/Boton';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';

/**
 * «Proyecto no encontrado» (prototipo/cliente/proyecto-no-encontrado.html): lo que se ve cuando el proyecto no existe o es de
 * otro cliente. No muestra el código, el nombre ni el dueño del proyecto pedido (RN-10, HU-04).
 */
export function ProyectoNoEncontrado({ vista = 'cliente' }: { vista?: 'cliente' | 'equipo' }) {
  useTituloDocumento('Proyecto no encontrado');
  const cliente = vista === 'cliente';
  return (
    <div className="no-encontrado">
      <p className="no-encontrado__codigo aparecer" aria-hidden="true">404</p>
      <span className="no-encontrado__icono aparecer" aria-hidden="true">
        <svg className="icono icono--grande" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3M8.5 11h5" /></svg>
      </span>
      <h1 className="no-encontrado__titulo aparecer">Proyecto no encontrado</h1>
      <p className="no-encontrado__texto aparecer" style={{ ['--retraso' as string]: '80ms' }}>
        {cliente
          ? 'No encontramos este proyecto en tu cuenta. Revisa que la dirección esté bien escrita o vuelve a tus proyectos.'
          : 'No encontramos este proyecto entre los de tu equipo. Revisa que la dirección esté bien escrita o vuelve a tus proyectos.'}
      </p>
      <div className="no-encontrado__acciones aparecer" style={{ ['--retraso' as string]: '160ms' }}>
        <EnlaceBoton a={cliente ? '/cliente/proyectos' : '/equipo/proyectos'} variante="acento" tamano="grande" icono="carpeta">
          Ir a mis proyectos
        </EnlaceBoton>
        {cliente && <EnlaceBoton a="/cliente/citas" variante="contorno" tamano="grande">Ver mis citas</EnlaceBoton>}
      </div>
      <p className="no-encontrado__ayuda">
        ¿Crees que es un error? Escríbenos a <a className="enlace" href="mailto:contacto@coretechsolutions.co">contacto@coretechsolutions.co</a>.
      </p>
    </div>
  );
}
