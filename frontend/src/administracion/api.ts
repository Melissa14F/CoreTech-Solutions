import { PREFIJO_API, api } from '../api/cliente';

/** Peticiones de Usuarios (HU-20), Roles y permisos (HU-21) y Auditoría (HU-22). Contrato: docs/04-api.md, flujo 8. */

export type CodigoRol = 'CLIENTE' | 'PRODUCT_OWNER' | 'ADMINISTRADOR';

// ------------------------------------------------------------------ Usuarios

export interface ActividadUsuario {
  /** Equipo que lidera un Product Owner; null si no lidera ninguno. */
  equipo: { equipoId: string; proyectosActivos: number; maxProyectos: number } | null;
  proyectosEnCurso: number;
  proyectosFinalizados: number;
  citasPendientes: number;
  citasConfirmadas: number;
  citasAtendidas: number;
  citasCanceladas: number;
}

export interface UsuarioGestion {
  usuarioId: string;
  nombre: string;
  email: string;
  celular: string;
  empresa: string | null;
  rol: CodigoRol;
  activo: boolean;
  /** Fin del bloqueo por intentos fallidos, solo si sigue vigente. */
  bloqueadaHasta: string | null;
  debeCambiarPassword: boolean;
  ultimoAcceso: string | null;
  anonimizada: boolean;
  creadaEn: string;
  actividad: ActividadUsuario;
}

export interface DatosCuenta {
  nombre: string;
  celular: string;
  email: string;
}

export const usuarios = {
  listar: () => api.obtener<UsuarioGestion[]>('/usuarios'),
  crear: (datos: DatosCuenta) => api.crear<UsuarioGestion>('/usuarios', datos),
  editar: (id: string, datos: DatosCuenta) => api.reemplazar<UsuarioGestion>(`/usuarios/${id}`, datos),
  cambiarActivo: (id: string, activo: boolean) => api.modificar<UsuarioGestion>(`/usuarios/${id}/activo`, { activo }),
  desbloquear: (id: string) => api.crear<UsuarioGestion>(`/usuarios/${id}/desbloquear`),
  anonimizar: (id: string) => api.crear<UsuarioGestion>(`/usuarios/${id}/anonimizar`),
  enviarRecuperacion: (id: string) => api.crear<{ enviadoA: string; expiraEnMinutos: number }>(`/usuarios/${id}/recuperacion`),
};

// ------------------------------------------------------------------ Roles y permisos

export interface CeldaPermiso {
  rol: CodigoRol;
  permitido: boolean;
  /** Casilla con candado: la fija una regla del negocio (RN-19). */
  fijo: boolean;
  motivo: string | null;
}

export interface FilaPermiso {
  codigo: string;
  nombre: string;
  ayuda: string | null;
  regla: string | null;
  grupo: string;
  celdas: CeldaPermiso[];
}

export interface MatrizPermisos {
  roles: { codigo: CodigoRol; nombre: string; usuarios: number; permisos: number }[];
  permisos: FilaPermiso[];
}

export interface CambioPermiso {
  rol: CodigoRol;
  permiso: string;
  permitido: boolean;
}

export const permisos = {
  matriz: () => api.obtener<MatrizPermisos>('/roles/permisos'),
  guardar: (cambios: CambioPermiso[]) => api.reemplazar<MatrizPermisos>('/roles/permisos', { cambios }),
};

// ------------------------------------------------------------------ Auditoría

export interface CambioAuditoria {
  campo: string;
  anterior: unknown;
  nuevo: unknown;
}

export interface RegistroAuditoria {
  id: number;
  fecha: string;
  /** null: sin sesión (por ejemplo, un intento fallido) o lo hizo el sistema. */
  usuario: { usuarioId: string; nombre: string; rol: CodigoRol | null } | null;
  categoria: string;
  accion: string;
  entidad: string;
  entidadId: string | null;
  elemento: string | null;
  cambios: CambioAuditoria[];
  ip: string | null;
  agente: string | null;
  alerta: boolean;
}

export interface PaginaAuditoria {
  registros: RegistroAuditoria[];
  pagina: number;
  tamano: number;
  total: number;
  totalPaginas: number;
  porCategoria: Record<string, number>;
}

export interface ResumenAuditoria {
  eventosHoy: number;
  alertasSemana: number;
  cambiosPermisosMes: number;
  usuarios: { usuarioId: string; nombre: string }[];
  categorias: { valor: string; nombre: string }[];
}

export interface FiltrosAuditoria {
  categoria?: string;
  usuarioId?: string;
  desde?: string;
  hasta?: string;
  q?: string;
}

/** Arma la consulta sin los filtros vacíos. */
export function consultaAuditoria(filtros: FiltrosAuditoria, extra: Record<string, string> = {}): string {
  const parametros = new URLSearchParams();
  for (const [clave, valor] of Object.entries({ ...filtros, ...extra })) {
    if (valor) parametros.set(clave, valor);
  }
  const texto = parametros.toString();
  return texto ? `?${texto}` : '';
}

export const auditoria = {
  consultar: (filtros: FiltrosAuditoria, pagina: number, tamano: number) =>
    api.obtener<PaginaAuditoria>(`/auditoria${consultaAuditoria(filtros, { pagina: String(pagina), tamano: String(tamano) })}`),
  resumen: () => api.obtener<ResumenAuditoria>('/auditoria/resumen'),
  /** Enlace de descarga del CSV con los mismos filtros (la cookie de sesión viaja sola). */
  urlExportar: (filtros: FiltrosAuditoria) => `${PREFIJO_API}/auditoria/exportar${consultaAuditoria(filtros)}`,
};
