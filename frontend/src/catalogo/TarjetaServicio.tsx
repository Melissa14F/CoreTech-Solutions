import { useState, type ReactNode } from 'react';

import { srcImagen } from './api';

interface PropiedadesTarjeta {
  nombre: string;
  descripcion: string;
  imagenUrl: string;
  imagenAlt: string;
  /** Pie de la tarjeta: el botón "Agendar cita" (o su imitación en la vista previa). */
  pie: ReactNode;
  carga?: 'eager' | 'lazy';
}

/**
 * Tarjeta del catálogo (plantillas/tarjeta-servicio.css). Si la imagen no carga, muestra una genérica con
 * el mismo texto alternativo (HU-07, "Imagen de servicio no disponible").
 */
export function TarjetaServicio({ nombre, descripcion, imagenUrl, imagenAlt, pie, carga = 'eager' }: PropiedadesTarjeta) {
  const [fallo, setFallo] = useState(false);
  return (
    <article className="tarjeta-servicio">
      <div className="tarjeta-servicio__imagen">
        {imagenUrl && !fallo ? (
          <img src={srcImagen(imagenUrl)} alt={imagenAlt} width={1200} height={800} loading={carga} onError={() => setFallo(true)} />
        ) : (
          <span className="tarjeta-servicio__generica" role="img" aria-label={imagenAlt || nombre} />
        )}
      </div>
      <div className="tarjeta-servicio__cuerpo">
        <h3 className="tarjeta-servicio__titulo">{nombre}</h3>
        <p className="tarjeta-servicio__descripcion">{descripcion}</p>
        <div className="tarjeta-servicio__pie">{pie}</div>
      </div>
    </article>
  );
}
