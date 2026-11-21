import type { ReactElement } from 'react';
import { Outlet, ScrollRestoration, createBrowserRouter, type RouteObject } from 'react-router-dom';

import { LayoutPanel } from '../layouts/LayoutPanel';
import { LayoutPublico } from '../layouts/LayoutPublico';
import { PaginaCambiarContrasena } from '../paginas/acceso/PaginaCambiarContrasena';
import { PaginaCompletarRegistro } from '../paginas/acceso/PaginaCompletarRegistro';
import { PaginaIngresar } from '../paginas/acceso/PaginaIngresar';
import { PaginaRegistro } from '../paginas/acceso/PaginaRegistro';
import { PaginaRestablecer } from '../paginas/acceso/PaginaRestablecer';
import { PaginaContenido } from '../paginas/admin/PaginaContenido';
import { PaginaPlantillas } from '../paginas/admin/PaginaPlantillas';
import { PaginaPlanTrabajo } from '../paginas/proyectos/PaginaPlanTrabajo';
import { PaginaDetalleProyecto } from '../paginas/proyectos/PaginaDetalleProyecto';
import { PaginaPropuesta } from '../paginas/proyectos/PaginaPropuesta';
import { PaginaRegistrarTrabajo } from '../paginas/proyectos/PaginaRegistrarTrabajo';
import { PaginaMisProyectos } from '../paginas/proyectos/PaginaMisProyectos';
import { PaginaServicios } from '../paginas/admin/PaginaServicios';
import { PaginaCitasAdmin } from '../paginas/admin/PaginaCitasAdmin';
import { PaginaCitasEquipo } from '../paginas/equipo/PaginaCitasEquipo';
import { PaginaUsuarios } from '../paginas/admin/PaginaUsuarios';
import { PaginaDesarrolladores } from '../paginas/admin/PaginaDesarrolladores';
import { PaginaEquipos } from '../paginas/admin/PaginaEquipos';
import { PaginaRolesPermisos } from '../paginas/admin/PaginaRolesPermisos';
import { PaginaAuditoria } from '../paginas/admin/PaginaAuditoria';
import { PaginaCorreoSistema } from '../paginas/admin/PaginaCorreoSistema';
import { PaginaCrearProyecto } from '../paginas/admin/PaginaCrearProyecto';
import { PaginaDashboard } from '../paginas/admin/PaginaDashboard';
import { PaginaProyectosAdmin } from '../paginas/admin/PaginaProyectosAdmin';
import { PaginaSolicitudes } from '../paginas/admin/PaginaSolicitudes';
import { PaginaAgendar } from '../paginas/cliente/PaginaAgendar';
import { PaginaMisCitas } from '../paginas/cliente/PaginaMisCitas';
import { PaginaInicio } from '../paginas/PaginaInicio';
import { PaginaLegal } from '../paginas/PaginaLegal';
import { PaginaNoEncontrada } from '../paginas/PaginaNoEncontrada';
import { ProveedorNotificaciones } from '../componentes/Notificaciones';
import { PaginaProvisional } from '../paginas/PaginaProvisional';
import { ProveedorSesion } from '../sesion/ContextoSesion';
import { RutaConSesion, RutaProtegida, RutaSoloAnonimos } from '../sesion/Guardas';
import { PANTALLAS, type Panel, type Pantalla } from './mapa';

/** Raíz de la aplicación: sesión, enlace "Saltar al contenido" y desplazamiento entre vistas. */
function Raiz() {
  return (
    <ProveedorSesion>
      <ProveedorNotificaciones>
        <a className="saltar-contenido" href="#contenido">Saltar al contenido</a>
        <Outlet />
        {/*
          Cada vista nueva se abre desde arriba; si la URL trae un ancla (#servicios) va a esa sección,
          y al volver atrás recupera la posición que tenía (docs/07-prototipo.md, "Desplazamiento").
        */}
        <ScrollRestoration />
      </ProveedorNotificaciones>
    </ProveedorSesion>
  );
}

/** Pantallas ya construidas; las demás siguen con su página provisional hasta su paso. */
const PAGINAS: Record<string, ReactElement> = {
  '/': <PaginaInicio />,
  '/legal': <PaginaLegal />,
  '/admin/servicios': <PaginaServicios />,
  '/admin/plantillas': <PaginaPlantillas />,
  '/admin/contenido': <PaginaContenido />,
  '/admin/usuarios': <PaginaUsuarios />,
  '/admin/especialidad/desarrolladores': <PaginaDesarrolladores />,
  '/admin/especialidad/equipos': <PaginaEquipos />,
  '/admin/roles': <PaginaRolesPermisos />,
  '/admin/auditoria': <PaginaAuditoria />,
  '/admin/correo': <PaginaCorreoSistema />,
  '/admin/citas': <PaginaCitasAdmin />,
  '/admin': <PaginaDashboard />,
  '/admin/proyectos': <PaginaProyectosAdmin />,
  '/admin/solicitudes': <PaginaSolicitudes />,
  '/admin/proyectos/nuevo': <PaginaCrearProyecto />,
  '/admin/proyectos/:codigo/plan': <PaginaPlanTrabajo />,
  '/equipo/proyectos/:codigo/plan': <PaginaPlanTrabajo />,
  '/cliente/proyectos/:codigo/propuesta': <PaginaPropuesta />,
  '/cliente/proyectos/:codigo': <PaginaDetalleProyecto />,
  '/equipo/proyectos/:codigo/registrar': <PaginaRegistrarTrabajo />,
  '/cliente/proyectos': <PaginaMisProyectos vista="cliente" />,
  '/equipo/proyectos': <PaginaMisProyectos vista="equipo" />,
  '/equipo/citas': <PaginaCitasEquipo />,
  '/cliente/citas': <PaginaMisCitas />,
  '/cliente/agendar': <PaginaAgendar />,
};

const provisionales = (pantallas: Pantalla[]): RouteObject[] =>
  pantallas.map((p) => ({ path: p.ruta, element: PAGINAS[p.ruta] ?? <PaginaProvisional pantalla={p} /> }));

/** Pantallas de acceso (P07). Cada una trae su propia plantilla, porque el lateral cambia. */
const PAGINAS_ACCESO: Record<string, ReactElement> = {
  '/registro': <RutaSoloAnonimos><PaginaRegistro /></RutaSoloAnonimos>,
  '/ingresar': <RutaSoloAnonimos><PaginaIngresar /></RutaSoloAnonimos>,
  '/cambiar-contrasena': <RutaConSesion><PaginaCambiarContrasena /></RutaConSesion>,
  '/restablecer': <PaginaRestablecer />,
  // Todavía no tiene sesión: viene de Google o Microsoft con una cookie de 10 minutos
  '/completar-registro': <RutaSoloAnonimos><PaginaCompletarRegistro /></RutaSoloAnonimos>,
};

const acceso: RouteObject[] = PANTALLAS.acceso.map((p) => ({
  path: p.ruta,
  element: PAGINAS_ACCESO[p.ruta] ?? <PaginaProvisional pantalla={p} />,
}));

/** Cada panel solo para su rol; sin sesión lleva a iniciar sesión (RNF-01). */
const panel = (nombre: Panel): RouteObject => ({
  element: <RutaProtegida panel={nombre}><LayoutPanel panel={nombre} /></RutaProtegida>,
  children: provisionales(PANTALLAS[nombre]),
});

export const rutas: RouteObject[] = [
  {
    element: <Raiz />,
    children: [
      { element: <LayoutPublico />, children: provisionales(PANTALLAS.publico) },
      ...acceso,
      panel('cliente'),
      panel('equipo'),
      panel('admin'),
      { path: '*', element: <PaginaNoEncontrada /> },
    ],
  },
];

export const crearEnrutador = () =>
  createBrowserRouter(rutas, {
    future: {
      v7_relativeSplatPath: true,
      v7_fetcherPersist: true,
      v7_normalizeFormMethod: true,
      v7_partialHydration: true,
      v7_skipActionErrorRevalidation: true,
    },
  });
