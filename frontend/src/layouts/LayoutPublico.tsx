import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';

import { ProveedorContenido, enlaceTelefono, useContenido } from '../catalogo/ContenidoSitio';
import { Icono } from '../componentes/Icono';
import { Marca } from '../componentes/Marca';

const SECCIONES = [
  { texto: 'Servicios', a: '/#servicios' },
  { texto: 'Proceso', a: '/#proceso' },
  { texto: 'Contacto', a: '/#contacto' },
];

/**
 * Sitio público: encabezado y pie de página de index.html. Los textos (descripción, misión, visión y
 * contacto) salen de GET /api/v1/contenido, que edita el administrador (HU-07, HU-19).
 */
export function LayoutPublico() {
  return (
    <ProveedorContenido>
      <SitioPublico />
    </ProveedorContenido>
  );
}

function SitioPublico() {
  const { texto } = useContenido();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const { pathname, hash } = useLocation();

  useEffect(() => setMenuAbierto(false), [pathname, hash]);

  useEffect(() => {
    if (!menuAbierto) return;
    const cerrarConEscape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') setMenuAbierto(false);
    };
    document.addEventListener('keydown', cerrarConEscape);
    return () => document.removeEventListener('keydown', cerrarConEscape);
  }, [menuAbierto]);

  return (
    <>
      <header className="encabezado encabezado--oscuro">
        <div className="contenedor encabezado__contenido">
          <Marca />
          <nav className="menu-principal" aria-label="Secciones">
            <ul className="menu-principal__lista">
              {SECCIONES.map((s) => (
                <li key={s.a}><Link className="menu-principal__enlace" to={s.a}>{s.texto}</Link></li>
              ))}
            </ul>
          </nav>
          <div className="encabezado__acciones">
            <Link className="boton encabezado__boton encabezado__boton--ingresar" to="/ingresar">
              <Icono nombre="ingresar" />
              Iniciar sesión
            </Link>
            <Link className="boton encabezado__boton encabezado__boton--registro" to="/registro">
              Crear cuenta
              <Icono nombre="flechaDerecha" className="icono--mover" />
            </Link>
            <button
              type="button"
              className="boton-menu"
              aria-expanded={menuAbierto}
              aria-controls="menu-movil"
              aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
              onClick={() => setMenuAbierto((valor) => !valor)}
            >
              <Icono nombre="menu" className="boton-menu__abrir" />
              <Icono nombre="cerrar" className="boton-menu__cerrar" />
            </button>
          </div>
        </div>

        <nav className={['menu-movil', menuAbierto && 'esta-abierto'].filter(Boolean).join(' ')} id="menu-movil" aria-label="Menú móvil">
          {SECCIONES.map((s) => (
            <Link key={s.a} className="menu-movil__enlace" to={s.a}>{s.texto}</Link>
          ))}
          <div className="menu-movil__acciones">
            <Link className="boton boton--transparente boton--bloque" to="/ingresar">Iniciar sesión</Link>
            <Link className="boton boton--acento boton--bloque" to="/registro">Crear cuenta</Link>
          </div>
        </nav>
      </header>

      <main id="contenido" tabIndex={-1}>
        <Outlet />
      </main>

      <footer className="pie-pagina pie-pagina--oscuro">
        <div className="contenedor">
          <div className="pie-pagina__grilla">
            <div className="pie-pagina__marca-bloque">
              <Marca />
              <p className="pie-pagina__descripcion">{texto('pie-descripcion')}</p>
            </div>
            <div className="pie-pagina__columna">
              <h2 className="pie-pagina__titulo">Misión</h2>
              <p className="pie-pagina__texto">{texto('pie-mision')}</p>
            </div>
            <div className="pie-pagina__columna">
              <h2 className="pie-pagina__titulo">Visión</h2>
              <p className="pie-pagina__texto">{texto('pie-vision')}</p>
            </div>
            <div className="pie-pagina__columna">
              <h2 className="pie-pagina__titulo">Contacto</h2>
              <address>
                <ul className="pie-pagina__lista pie-pagina__lista--contacto">
                  <li><span>{texto('contacto-direccion')}</span></li>
                  <li><a href={enlaceTelefono(texto('contacto-telefono'))}>{texto('contacto-telefono')}</a></li>
                  <li><a href={`mailto:${texto('contacto-correo')}`}>{texto('contacto-correo')}</a></li>
                  <li><span>{texto('contacto-horario')}</span></li>
                </ul>
              </address>
            </div>
          </div>
          <div className="pie-pagina__inferior">
            <p className="pie-pagina__derechos">© {new Date().getFullYear()} CoreTech Solutions. Todos los derechos reservados.</p>
            <nav aria-label="Avisos legales">
              <ul className="pie-pagina__legales">
                <li><Link to="/legal#datos">Política de tratamiento de datos</Link></li>
                <li><Link to="/legal#terminos">Términos y condiciones</Link></li>
              </ul>
            </nav>
          </div>
        </div>
      </footer>
    </>
  );
}
