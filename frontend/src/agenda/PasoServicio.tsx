import { useState } from 'react';
import { Link } from 'react-router-dom';

import { Boton } from '../componentes/Boton';
import { EstadoVacio } from '../componentes/EstadoVacio';
import { Icono } from '../componentes/Icono';
import { srcImagen, type ServicioPublico } from '../catalogo/api';

interface Propiedades {
  servicios: ServicioPublico[];
  elegido: ServicioPublico | null;
  alElegir: (servicio: ServicioPublico) => void;
  alContinuar: () => void;
}

/** Paso 1 de 3: ¿Qué servicio necesitas? (prototipo/cliente/agendar-servicio.html). */
export function PasoServicio({ servicios, elegido, alElegir, alContinuar }: Propiedades) {
  const [error, setError] = useState<string | null>(null);

  if (servicios.length === 0) {
    return (
      <EstadoVacio
        icono="capas"
        titulo="Pronto publicaremos nuestros servicios"
        texto="Por ahora no hay servicios para agendar. Vuelve a intentarlo en unos días."
      />
    );
  }

  return (
    <form
      className="agendamiento__principal"
      noValidate
      onSubmit={(evento) => {
        evento.preventDefault();
        if (!elegido) setError('Elige el servicio que necesitas para continuar.');
        else alContinuar();
      }}
    >
      <section className="tarjeta" aria-labelledby="titulo-elegir">
        <fieldset className={['grupo-servicios', 'campo', error && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend>
            <h2 className="agendamiento__titulo-bloque" id="titulo-elegir">¿Qué servicio necesitas?</h2>
          </legend>
          <p className="agendamiento__ayuda">Elige uno. En la reunión revisamos contigo los detalles del proyecto.</p>
          <div className="opciones-servicio">
            {servicios.map((s) => (
              <label className="opcion-servicio" key={s.servicioId}>
                <input
                  className="opcion-servicio__control"
                  type="radio"
                  name="servicio"
                  value={s.servicioId}
                  checked={elegido?.servicioId === s.servicioId}
                  aria-describedby={error ? 'servicio-mensaje' : undefined}
                  aria-invalid={error ? true : undefined}
                  onChange={() => {
                    setError(null);
                    alElegir(s);
                  }}
                />
                <img className="opcion-servicio__imagen" src={srcImagen(s.imagenUrl)} alt="" width={96} height={64} />
                <span>
                  <span className="opcion-servicio__nombre">{s.nombre}</span>
                  <span className="opcion-servicio__descripcion">{s.descripcionCorta}</span>
                </span>
                <span className="opcion-servicio__marca" aria-hidden="true" />
              </label>
            ))}
          </div>
          <p className="campo__mensaje grupo-servicios__mensaje" id="servicio-mensaje" role={error ? 'alert' : undefined}>
            {error && (
              <>
                <Icono nombre="alerta" />
                <span className="campo__mensaje-texto">{error}</span>
              </>
            )}
          </p>
        </fieldset>
      </section>

      <div className="acciones-paso">
        <Link className="boton boton--fantasma" to="/cliente/citas">Cancelar</Link>
        <div className="acciones-paso__derecha">
          <Boton type="submit" variante="acento" tamano="grande" iconoFinal="flechaDerecha">Continuar</Boton>
        </div>
      </div>
    </form>
  );
}
