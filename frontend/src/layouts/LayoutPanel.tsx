import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, matchPath, useLocation } from 'react-router-dom';

import { Icono } from '../componentes/Icono';
import { Marca } from '../componentes/Marca';
import { PANELES, PANTALLAS, type Panel } from '../rutas/mapa';
import { useSesion, useUsuario } from '../sesion/ContextoSesion';
import { Campana } from './Campana';

/** "Laura Gómez" → "LG", como el avatar del prototipo. */
function inicialesDe(nombre: string): string {
  return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p.charAt(0).toUpperCase()).join('');
}

/**
 * Estructura de los paneles del cliente, del equipo y del administrador (plantillas/panel.css):
 * menú lateral oscuro, barra superior con la ruta de navegación y la campana, y el contenido.
 * En celular el menú lateral se abre con el botón de la barra (clase panel-menu-abierto en &lt;html&gt;).
 */
export function LayoutPanel({ panel }: { panel: Panel }) {
  const configuracion = PANELES[panel];
  const usuario = useUsuario();
  const { cerrarSesion } = useSesion();
  const iniciales = inicialesDe(usuario?.nombre ?? '');
  const { pathname } = useLocation();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const botonMenu = useRef<HTMLButtonElement>(null);
  const lateral = useRef<HTMLElement>(null);

  const pantalla = PANTALLAS[panel].find((p) => matchPath(p.ruta, pathname));

  // Al navegar se cierra el menú del celular.
  useEffect(() => setMenuAbierto(false), [pathname]);

  useEffect(() => {
    const raiz = document.documentElement;
    raiz.classList.toggle('panel-menu-abierto', menuAbierto);
    if (menuAbierto) lateral.current?.querySelector('a')?.focus();
    const cerrarConEscape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape' && menuAbierto) {
        setMenuAbierto(false);
        botonMenu.current?.focus();
      }
    };
    document.addEventListener('keydown', cerrarConEscape);
    return () => {
      document.removeEventListener('keydown', cerrarConEscape);
      raiz.classList.remove('panel-menu-abierto');
    };
  }, [menuAbierto]);

  return (
    <div className="panel">
      <aside className="panel__lateral superficie-noche" aria-label={configuracion.etiquetaMenu} ref={lateral}>
        <Marca etiqueta="CoreTech Solutions, ir al sitio público" />
        <p className="panel__rol">
          <strong>{panel === 'admin' ? 'Panel de administración' : panel === 'equipo' ? 'Panel del equipo' : 'Panel del cliente'}</strong>
          {configuracion.descripcion}
        </p>

        {configuracion.menu.map((grupo) => (
          <nav key={grupo.titulo} aria-label={`Secciones: ${grupo.titulo}`}>
            <p className="navegacion-panel__titulo">{grupo.titulo}</p>
            <ul className="navegacion-panel__lista">
              {grupo.enlaces.map((enlace) => (
                <li key={enlace.a}>
                  {/* "end" en la página de inicio del panel, para que no quede marcada en todas las demás */}
                  <NavLink className="navegacion-panel__enlace" to={enlace.a} end={enlace.a === configuracion.inicio && panel === 'admin'}>
                    <Icono nombre={enlace.icono} />
                    {enlace.texto}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        ))}

        <div className="panel__usuario">
          <span className="avatar avatar--acento" aria-hidden="true">{iniciales}</span>
          <span className="panel__usuario-datos">
            <span className="panel__usuario-nombre">{usuario?.nombre}</span>
            <span className="panel__usuario-correo">{usuario?.email}</span>
          </span>
          <button type="button" className="panel__salir" aria-label="Cerrar sesión" title="Cerrar sesión" onClick={() => void cerrarSesion()}>
            <Icono nombre="salir" />
          </button>
        </div>
      </aside>

      <div className="panel__cuerpo">
        <header className="panel__barra">
          <button
            type="button"
            ref={botonMenu}
            className="boton-icono boton-icono--pequeno boton-menu-panel"
            aria-expanded={menuAbierto}
            aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
            onClick={() => setMenuAbierto((valor) => !valor)}
          >
            <Icono nombre="menu" />
          </button>
          <nav className="ruta" aria-label="Ruta de navegación">
            <Link to={configuracion.inicio}>Panel</Link>
            {pantalla && (
              <>
                <Icono nombre="separadorRuta" />
                <span aria-current="page">{pantalla.titulo}</span>
              </>
            )}
          </nav>
          <div className="panel__barra-acciones">
            <Campana />
            <div className="panel__perfil">
              <span className="avatar avatar--acento" aria-hidden="true">{iniciales}</span>
              <span className="panel__perfil-nombre">
                {usuario?.nombre}
                <small>{configuracion.rol}</small>
              </span>
            </div>
          </div>
        </header>

        <main className="panel__contenido" id="contenido" tabIndex={-1}>
          <Outlet />
        </main>
      </div>

      <div className="panel__velo" onClick={() => setMenuAbierto(false)} aria-hidden="true" />
    </div>
  );
}
