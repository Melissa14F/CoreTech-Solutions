import type { NombreIcono } from '../componentes/Icono';

/**
 * Mapa de pantallas de la SPA (docs/07-prototipo.md, "Pantallas → rutas de la SPA").
 * Una sola fuente para el enrutador, el menú lateral, la ruta de navegación y el título de la pestaña.
 */
export interface Pantalla {
  ruta: string;
  titulo: string;
  /** Historias de usuario que se construyen en esta pantalla. */
  hu: string[];
  /** Archivo del prototipo que sirve de referencia visual. */
  prototipo: string;
}

export type Area = 'publico' | 'acceso' | 'cliente' | 'equipo' | 'admin';

export const PANTALLAS: Record<Area, Pantalla[]> = {
  publico: [
    { ruta: '/', titulo: 'Inicio', hu: ['HU-07'], prototipo: 'index.html' },
    { ruta: '/legal', titulo: 'Avisos legales', hu: [], prototipo: 'legal.html' },
  ],
  acceso: [
    { ruta: '/registro', titulo: 'Crear cuenta', hu: ['HU-05'], prototipo: 'registro.html' },
    { ruta: '/ingresar', titulo: 'Iniciar sesión', hu: ['HU-06'], prototipo: 'login.html' },
    { ruta: '/cambiar-contrasena', titulo: 'Cambiar contraseña', hu: ['HU-06', 'HU-20', 'HU-26'], prototipo: 'cambiar-contrasena.html' },
    // Sin prototipo (RF-50, D-50): quien llega con Google o Microsoft completa celular, empresa y la política; usa el diseño de registro.html
    { ruta: '/completar-registro', titulo: 'Completa tu registro', hu: ['HU-05'], prototipo: 'registro.html' },
    // Sin prototipo (D-37): usa el diseño de cambiar-contrasena.html
    { ruta: '/restablecer', titulo: 'Restablecer contraseña', hu: ['HU-06'], prototipo: 'cambiar-contrasena.html' },
  ],
  cliente: [
    { ruta: '/cliente/proyectos', titulo: 'Mis proyectos', hu: ['HU-04'], prototipo: 'cliente/mis-proyectos.html' },
    { ruta: '/cliente/proyectos/:codigo', titulo: 'Detalle del proyecto', hu: ['HU-04', 'HU-11', 'HU-14', 'HU-15'], prototipo: 'cliente/proyecto-tienda-en-linea.html' },
    { ruta: '/cliente/proyectos/:codigo/propuesta', titulo: 'Plan y cotización', hu: ['HU-29', 'HU-30'], prototipo: 'cliente/plan-inventario-salon.html' },
    { ruta: '/cliente/citas', titulo: 'Mis citas', hu: ['HU-08'], prototipo: 'cliente/mis-citas.html' },
    { ruta: '/cliente/agendar', titulo: 'Agendar cita', hu: ['HU-01'], prototipo: 'cliente/agendar-servicio.html' },
  ],
  equipo: [
    { ruta: '/equipo/proyectos', titulo: 'Mis proyectos', hu: ['HU-03'], prototipo: 'desarrollador/proyectos.html' },
    { ruta: '/equipo/proyectos/:codigo/registrar', titulo: 'Registrar trabajo', hu: ['HU-03', 'HU-12', 'HU-13'], prototipo: 'desarrollador/registrar-avance-tienda-en-linea.html' },
    { ruta: '/equipo/proyectos/:codigo/plan', titulo: 'Plan de trabajo', hu: ['HU-28', 'HU-30'], prototipo: 'desarrollador/plan-de-trabajo-inventario-salon.html' },
    { ruta: '/equipo/citas', titulo: 'Citas y reuniones', hu: ['HU-10', 'HU-16'], prototipo: 'desarrollador/citas-reuniones.html' },
  ],
  admin: [
    { ruta: '/admin', titulo: 'Dashboard', hu: ['HU-23'], prototipo: 'admin/dashboard.html' },
    { ruta: '/admin/citas', titulo: 'Citas', hu: ['HU-09'], prototipo: 'admin/citas.html' },
    { ruta: '/admin/proyectos', titulo: 'Proyectos', hu: ['HU-17'], prototipo: 'admin/proyectos.html' },
    { ruta: '/admin/proyectos/nuevo', titulo: 'Crear proyecto', hu: ['HU-02'], prototipo: 'admin/crear-proyecto.html' },
    { ruta: '/admin/proyectos/:codigo/plan', titulo: 'Plan de trabajo', hu: ['HU-28', 'HU-30'], prototipo: 'admin/plan-de-trabajo-web-duarte.html' },
    { ruta: '/admin/plantillas', titulo: 'Plantillas de fases', hu: ['HU-27'], prototipo: 'admin/plantillas-fases.html' },
    { ruta: '/admin/solicitudes', titulo: 'Solicitudes de reunión', hu: ['HU-16'], prototipo: 'admin/solicitudes.html' },
    { ruta: '/admin/especialidad/desarrolladores', titulo: 'Desarrolladores', hu: ['HU-25'], prototipo: 'admin/desarrolladores.html' },
    { ruta: '/admin/especialidad/equipos', titulo: 'Equipos', hu: ['HU-26'], prototipo: 'admin/equipos.html' },
    { ruta: '/admin/servicios', titulo: 'Servicios', hu: ['HU-18'], prototipo: 'admin/servicios.html' },
    { ruta: '/admin/contenido', titulo: 'Contenido del sitio', hu: ['HU-19'], prototipo: 'admin/contenido.html' },
    { ruta: '/admin/usuarios', titulo: 'Usuarios', hu: ['HU-20'], prototipo: 'admin/usuarios.html' },
    { ruta: '/admin/roles', titulo: 'Roles y permisos', hu: ['HU-21'], prototipo: 'admin/roles-permisos.html' },
    { ruta: '/admin/auditoria', titulo: 'Auditoría', hu: ['HU-22'], prototipo: 'admin/auditoria.html' },
    // D-51: pantalla nueva sin prototipo (conectar la cuenta de Gmail que envía los correos)
    { ruta: '/admin/correo', titulo: 'Correo del sistema', hu: ['HU-24'], prototipo: '' },
  ],
};

export interface EnlaceMenu {
  texto: string;
  a: string;
  icono: NombreIcono;
}

export interface GrupoMenu {
  titulo: string;
  enlaces: EnlaceMenu[];
}

export type Panel = 'cliente' | 'equipo' | 'admin';

/** Configuración de cada panel: los mismos textos, grupos e íconos del prototipo. */
export const PANELES: Record<Panel, { rol: string; descripcion: string; etiquetaMenu: string; inicio: string; menu: GrupoMenu[] }> = {
  cliente: {
    rol: 'Cliente',
    descripcion: 'Tus citas y proyectos',
    etiquetaMenu: 'Menú del cliente',
    inicio: '/cliente/proyectos',
    menu: [
      {
        titulo: 'Mi cuenta',
        enlaces: [
          { texto: 'Mis proyectos', a: '/cliente/proyectos', icono: 'carpeta' },
          { texto: 'Mis citas', a: '/cliente/citas', icono: 'calendario' },
          { texto: 'Agendar cita', a: '/cliente/agendar', icono: 'calendarioMas' },
        ],
      },
    ],
  },
  equipo: {
    rol: 'Desarrollador (Product Owner)',
    descripcion: 'Tus proyectos y reuniones',
    etiquetaMenu: 'Menú del equipo',
    inicio: '/equipo/proyectos',
    menu: [
      {
        titulo: 'Mi trabajo',
        enlaces: [
          { texto: 'Mis proyectos', a: '/equipo/proyectos', icono: 'carpeta' },
          { texto: 'Citas y reuniones', a: '/equipo/citas', icono: 'calendario' },
        ],
      },
    ],
  },
  admin: {
    rol: 'Administrador(a)',
    descripcion: 'Gestión de CoreTech',
    etiquetaMenu: 'Menú del administrador',
    inicio: '/admin',
    menu: [
      {
        titulo: 'Gestión',
        enlaces: [
          { texto: 'Dashboard', a: '/admin', icono: 'dashboard' },
          { texto: 'Citas', a: '/admin/citas', icono: 'calendario' },
          { texto: 'Proyectos', a: '/admin/proyectos', icono: 'carpeta' },
          { texto: 'Plantillas de fases', a: '/admin/plantillas', icono: 'lista' },
          { texto: 'Solicitudes de reunión', a: '/admin/solicitudes', icono: 'mensaje' },
        ],
      },
      {
        titulo: 'Especialidad',
        enlaces: [
          { texto: 'Desarrolladores', a: '/admin/especialidad/desarrolladores', icono: 'codigo' },
          { texto: 'Equipos', a: '/admin/especialidad/equipos', icono: 'equipo' },
        ],
      },
      {
        titulo: 'Sitio web',
        enlaces: [
          { texto: 'Servicios', a: '/admin/servicios', icono: 'capas' },
          { texto: 'Contenido del sitio', a: '/admin/contenido', icono: 'plantilla' },
        ],
      },
      {
        titulo: 'Seguridad',
        enlaces: [
          { texto: 'Usuarios', a: '/admin/usuarios', icono: 'usuarios' },
          { texto: 'Roles y permisos', a: '/admin/roles', icono: 'escudo' },
          { texto: 'Auditoría', a: '/admin/auditoria', icono: 'portapapeles' },
          { texto: 'Correo del sistema', a: '/admin/correo', icono: 'correo' },
        ],
      },
    ],
  },
};
