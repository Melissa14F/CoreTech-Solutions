import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { Icono } from '../componentes/Icono';
import { Marca } from '../componentes/Marca';
import { useTituloDocumento } from '../componentes/useTituloDocumento';

interface PropiedadesPlantillaAcceso {
  /** Título de la pestaña (el de la pantalla en src/rutas/mapa.ts). */
  tituloPestana: string;
  /** Formulario y textos de la columna principal (dentro de .acceso__caja). */
  children: ReactNode;
  /** Columna oscura de la derecha; cada pantalla tiene la suya en el prototipo. Se oculta en pantallas pequeñas. */
  lateral: { etiqueta: string; pildora: string; titulo: string; texto: string; contenido?: ReactNode };
}

/**
 * Pantallas de acceso (crear cuenta, iniciar sesión, restablecer y cambiar la contraseña):
 * plantillas/acceso.css, con la misma estructura de prototipo/login.html.
 */
export function PlantillaAcceso({ tituloPestana, children, lateral }: PropiedadesPlantillaAcceso) {
  useTituloDocumento(tituloPestana);
  return (
    <div className="acceso">
      <div className="acceso__principal">
        <header className="acceso__superior">
          <Marca />
          <Link className="enlace-flecha" to="/">
            Volver al inicio
            <Icono nombre="flechaDerecha" />
          </Link>
        </header>
        <main className="acceso__caja" id="contenido" tabIndex={-1}>
          {children}
        </main>
      </div>
      <aside className="acceso__lateral superficie-noche" aria-label={lateral.etiqueta}>
        <div className="acceso__lateral-contenido">
          <span className="pildora">{lateral.pildora}</span>
          <h2>{lateral.titulo}</h2>
          <p>{lateral.texto}</p>
          {lateral.contenido}
        </div>
      </aside>
    </div>
  );
}
