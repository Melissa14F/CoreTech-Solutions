import type { MatrizPermisos, PaginaAuditoria, ResumenAuditoria, UsuarioGestion } from '../administracion/api';

/** Datos de ejemplo de Usuarios, Roles y permisos y Auditoría, como los devuelve la API. */

const SIN_ACTIVIDAD = {
  equipo: null, proyectosEnCurso: 0, proyectosFinalizados: 0, citasPendientes: 0, citasConfirmadas: 0, citasAtendidas: 0, citasCanceladas: 0,
};

const base = {
  empresa: null, activo: true, bloqueadaHasta: null, debeCambiarPassword: false, anonimizada: false,
  creadaEn: '2026-01-10T15:00:00Z', ultimoAcceso: null, actividad: SIN_ACTIVIDAD,
};

export const U_PATRICIA: UsuarioGestion = {
  ...base, usuarioId: 'u-patricia', nombre: 'Patricia León', email: 'patricia.leon@coretechsolutions.co', celular: '3157781200', rol: 'ADMINISTRADOR',
};
export const U_ANDRES: UsuarioGestion = {
  ...base, usuarioId: 'u-andres', nombre: 'Andrés Pérez', email: 'andres.perez@coretechsolutions.co', celular: '3204459021', rol: 'PRODUCT_OWNER',
  actividad: { ...SIN_ACTIVIDAD, equipo: { equipoId: 'e-1', proyectosActivos: 2, maxProyectos: 3 } },
};
export const U_DIEGO: UsuarioGestion = {
  ...base, usuarioId: 'u-diego', nombre: 'Diego Ramírez', email: 'diego.ramirez@coretechsolutions.co', celular: '3016643275', rol: 'PRODUCT_OWNER', activo: false,
};
export const U_LAURA: UsuarioGestion = {
  ...base, usuarioId: 'u-laura', nombre: 'Laura Gómez', email: 'laura.gomez@correo.com', celular: '3142208815', rol: 'CLIENTE',
  actividad: { ...SIN_ACTIVIDAD, proyectosEnCurso: 2, citasPendientes: 1 },
};
export const U_SOFIA: UsuarioGestion = {
  ...base, usuarioId: 'u-sofia', nombre: 'Sofía Navarro', email: 'sofia.navarro@correo.com', celular: '3193307742', rol: 'CLIENTE',
  bloqueadaHasta: '2099-01-01T00:00:00Z', actividad: { ...SIN_ACTIVIDAD, citasCanceladas: 1 },
};

export const USUARIOS = [U_PATRICIA, U_ANDRES, U_DIEGO, U_LAURA, U_SOFIA];

const fijoAdmin = { rol: 'ADMINISTRADOR' as const, permitido: true, fijo: true, motivo: 'El administrador siempre lo tiene' };

export const MATRIZ: MatrizPermisos = {
  roles: [
    { codigo: 'CLIENTE', nombre: 'Cliente', usuarios: 9, permisos: 2 },
    { codigo: 'PRODUCT_OWNER', nombre: 'Product Owner', usuarios: 5, permisos: 1 },
    { codigo: 'ADMINISTRADOR', nombre: 'Administrador', usuarios: 1, permisos: 2 },
  ],
  permisos: [
    {
      codigo: 'citas.agendar', nombre: 'Agendar citas', ayuda: 'Elegir servicio, fecha, hora y modalidad.', regla: 'RN-01 a RN-03', grupo: 'Citas',
      celdas: [
        { rol: 'CLIENTE', permitido: true, fijo: false, motivo: null },
        { rol: 'PRODUCT_OWNER', permitido: false, fijo: false, motivo: null },
        fijoAdmin,
      ],
    },
    {
      codigo: 'proyectos.registrar_avance', nombre: 'Registrar avances', ayuda: null, regla: 'RN-07', grupo: 'Proyectos',
      celdas: [
        { rol: 'CLIENTE', permitido: false, fijo: true, motivo: 'Fijo por RN-07' },
        { rol: 'PRODUCT_OWNER', permitido: true, fijo: true, motivo: 'Fijo por RN-07' },
        { rol: 'ADMINISTRADOR', permitido: false, fijo: true, motivo: 'Fijo por RN-07' },
      ],
    },
    {
      codigo: 'proyectos.ver_propios', nombre: 'Ver sus proyectos y avances', ayuda: null, regla: 'RN-10 y RN-11', grupo: 'Proyectos',
      celdas: [
        { rol: 'CLIENTE', permitido: true, fijo: true, motivo: 'Fijo por RN-10 y RN-11' },
        { rol: 'PRODUCT_OWNER', permitido: false, fijo: false, motivo: null },
        fijoAdmin,
      ],
    },
  ],
};

export const RESUMEN_AUDITORIA: ResumenAuditoria = {
  eventosHoy: 5, alertasSemana: 1, cambiosPermisosMes: 2,
  usuarios: [{ usuarioId: 'u-patricia', nombre: 'Patricia León' }, { usuarioId: 'u-andres', nombre: 'Andrés Pérez' }],
  categorias: [
    { valor: 'citas', nombre: 'Citas' }, { valor: 'proyectos', nombre: 'Proyectos' }, { valor: 'avances', nombre: 'Avances' },
    { valor: 'solicitudes', nombre: 'Solicitudes' }, { valor: 'usuarios', nombre: 'Usuarios' }, { valor: 'permisos', nombre: 'Permisos' },
    { valor: 'sitio', nombre: 'Sitio web' }, { valor: 'sesiones', nombre: 'Sesiones' }, { valor: 'seguridad', nombre: 'Seguridad' },
  ],
};

export const PAGINA_AUDITORIA: PaginaAuditoria = {
  pagina: 0, tamano: 20, total: 2, totalPaginas: 1, porCategoria: { sitio: 1, seguridad: 1 },
  registros: [
    {
      id: 10472, fecha: '2026-11-08T15:02:00Z', usuario: { usuarioId: 'u-patricia', nombre: 'Patricia León', rol: 'ADMINISTRADOR' },
      categoria: 'sitio', accion: 'Editó un servicio', entidad: 'servicio', entidadId: 'Integración de pagos', elemento: 'Integración de pagos',
      cambios: [{ campo: 'descripcionCorta', anterior: 'Conecta tu sitio con pasarelas de pago seguras.', nuevo: 'Pagos seguros y conciliación automática.' }],
      ip: '181.52.200.4', agente: 'Chrome en macOS', alerta: false,
    },
    {
      id: 10476, fecha: '2026-11-10T15:15:00Z', usuario: null, categoria: 'seguridad', accion: 'Bloqueó la cuenta por intentos fallidos',
      entidad: 'usuario', entidadId: 'u-andres', elemento: 'Andrés Pérez', cambios: [], ip: '45.183.12.77', agente: null, alerta: true,
    },
  ],
};
