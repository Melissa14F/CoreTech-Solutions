import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { catalogo, srcImagen, type ServicioPublico } from './api';

/** El servicio de "?servicio=slug" (el visitante pulsó "Agendar cita" en una tarjeta del catálogo). */
export function useServicioElegido(): { slug: string | null; servicio: ServicioPublico | null } {
  const [parametros] = useSearchParams();
  const slug = parametros.get('servicio');
  const [servicio, setServicio] = useState<ServicioPublico | null>(null);

  useEffect(() => {
    if (!slug) return;
    let vigente = true;
    catalogo.visibles()
      .then((lista) => vigente && setServicio(lista.find((s) => s.slug === slug) ?? null))
      .catch(() => undefined);
    return () => {
      vigente = false;
    };
  }, [slug]);

  return { slug, servicio };
}

/**
 * Recuadro del prototipo (login.html y registro.html) que recuerda qué servicio va a agendar quien llega
 * desde el catálogo. Si el servicio ya no está visible, no se muestra.
 */
export function ServicioElegido({ servicio, texto }: { servicio: ServicioPublico | null; texto: string }) {
  if (!servicio) return null;
  return (
    <div className="servicio-elegido">
      <img src={srcImagen(servicio.imagenUrl)} alt="" width={52} height={36} />
      <div>
        <span className="servicio-elegido__dato">{texto}</span>
        <strong className="servicio-elegido__nombre">{servicio.nombre}</strong>
      </div>
      <Link className="servicio-elegido__cambiar enlace" to="/#servicios">Cambiar</Link>
    </div>
  );
}

/** "/cliente/agendar?servicio=slug": adónde va después de crear la cuenta o de iniciar sesión. */
export const agendarServicio = (slug: string | null) =>
  `/cliente/agendar${slug ? `?servicio=${encodeURIComponent(slug)}` : ''}`;
