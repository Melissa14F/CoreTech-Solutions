import { api } from '../api/cliente';
import type { EstadoCita, Modalidad } from './api';

/** Una cita para el administrador o el desarrollador (Product Owner): GET /citas/gestion y GET /citas/equipo (docs/04-api.md). */
export interface CitaGestion {
  citaId: string;
  codigo: string;
  servicioId: string;
  servicio: string;
  estado: EstadoCita;
  modalidad: Modalidad;
  /** Hora en la zona del cliente y en la de Colombia, con su desplazamiento (RN-23). */
  fechaHoraLocal: string;
  fechaHoraColombia: string;
  pais: string;
  zonaHoraria: string;
  descripcionNecesidad: string;
  enlaceReunion: string | null;
  notaCliente: string | null;
  notaAsignacion: string | null;
  motivoCancelacion: string | null;
  cliente: { nombre: string; email: string };
  equipo: { equipoId: string; productOwner: string; emailProductOwner: string | null; integrantes: number; proyectosActivos: number; maxProyectos: number } | null;
  tieneCuestionario: boolean;
  confirmadaAt: string | null;
  atendidaAt: string | null;
  canceladaAt: string | null;
  /** Quién la canceló: el cliente o la administración; nulo si no está cancelada. */
  canceladaPor: 'CLIENTE' | 'ADMINISTRACION' | null;
}

export interface ListaCitas {
  citas: CitaGestion[];
  porEstado: Record<EstadoCita, number>;
  sinEquipo: number;
  /** Todas las del rango y el equipo, sin los demás filtros. */
  total: number;
  pagina: number;
  tamano: number;
  /** Las que cumplen todos los filtros (el total de la lista, para paginar). */
  totalFiltradas: number;
  totalPaginas: number;
}

export interface EquipoParaAsignar {
  equipoId: string;
  productOwner: string;
  integrantes: number;
  proyectosActivos: number;
  maxProyectos: number;
  citasProximas: number;
}

export type Presupuesto = 'MENOS_5M' | 'DE_5_A_15M' | 'DE_15_A_30M' | 'MAS_30M' | 'SIN_DEFINIR';
export type Idioma = 'ESPANOL' | 'INGLES' | 'PORTUGUES' | 'OTRO';
export type Urgencia = 'BAJA' | 'MEDIA' | 'ALTA';

export interface DatosCuestionario {
  resumen: string;
  descripcionCliente: string;
  presupuesto: Presupuesto | '';
  fechaDeseada: string;
  idioma: Idioma | '';
  urgencia: Urgencia | '';
}

export interface Cuestionario {
  cuestionarioId: string;
  citaId: string;
  resumen: string;
  descripcionCliente: string;
  presupuesto: Presupuesto;
  fechaDeseada: string | null;
  idioma: Idioma;
  urgencia: Urgencia;
  registradoAt: string;
}

/** Las listas del cuestionario, con el texto del prototipo (prototipo/desarrollador/citas-reuniones.html). */
export const PRESUPUESTOS: [Presupuesto, string][] = [
  ['MENOS_5M', 'Menos de $5 millones'], ['DE_5_A_15M', 'Entre $5 y $15 millones'], ['DE_15_A_30M', 'Entre $15 y $30 millones'],
  ['MAS_30M', 'Más de $30 millones'], ['SIN_DEFINIR', 'Aún no lo define'],
];
export const IDIOMAS: [Idioma, string][] = [['ESPANOL', 'Español'], ['INGLES', 'Inglés'], ['PORTUGUES', 'Portugués'], ['OTRO', 'Otro']];
export const URGENCIAS: [Urgencia, string, string][] = [
  ['BAJA', 'Baja', 'Puede esperar'], ['MEDIA', 'Media', 'En los próximos meses'], ['ALTA', 'Alta', 'Lo necesita pronto'],
];

export interface FiltrosCitas {
  estado?: EstadoCita;
  sinEquipo?: boolean;
  equipoId?: string;
  q?: string;
  /** Desde 0. */
  pagina?: number;
}

export const gestionCitas = {
  todas: (filtros: FiltrosCitas = {}) => {
    const consulta = new URLSearchParams();
    if (filtros.estado) consulta.set('estado', filtros.estado);
    if (filtros.sinEquipo) consulta.set('sinEquipo', 'true');
    if (filtros.equipoId) consulta.set('equipoId', filtros.equipoId);
    if (filtros.q?.trim()) consulta.set('q', filtros.q.trim());
    if (filtros.pagina) consulta.set('pagina', String(filtros.pagina));
    const texto = consulta.toString();
    return api.obtener<ListaCitas>(`/citas/gestion${texto ? `?${texto}` : ''}`);
  },
  equiposDisponibles: () => api.obtener<EquipoParaAsignar[]>('/citas/equipos-disponibles'),
  asignar: (citaId: string, equipoId: string, nota: string) =>
    api.modificar<CitaGestion>(`/citas/${citaId}/asignar`, { equipoId, nota: nota.trim() || null }),
  cancelar: (citaId: string, motivo: string) => api.modificar<CitaGestion>(`/citas/${citaId}/cancelar-admin`, { motivo }),
  deMiEquipo: () => api.obtener<CitaGestion[]>('/citas/equipo'),
  confirmar: (citaId: string, enlaceReunion: string, nota: string) =>
    api.modificar<{ citaId: string; estado: EstadoCita; confirmadaAt: string }>(`/citas/${citaId}/confirmar`, {
      enlaceReunion: enlaceReunion.trim() || null,
      nota: nota.trim() || null,
    }),
  llenarCuestionario: (citaId: string, d: DatosCuestionario) =>
    api.crear<{ cuestionarioId: string; citaEstado: EstadoCita }>(`/citas/${citaId}/cuestionario`, {
      ...d,
      fechaDeseada: d.fechaDeseada.trim() || null,
    }),
  cuestionario: (citaId: string) => api.obtener<Cuestionario>(`/citas/${citaId}/cuestionario`),
};
