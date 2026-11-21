/** Reuniones de seguimiento: solicitarlas, programarlas, rechazarlas y cerrarlas con su resumen (HU-15, HU-16, D-42). */
import { api } from '../api/cliente';

export type EstadoSolicitud = 'PENDIENTE' | 'PROGRAMADA' | 'RECHAZADA' | 'CANCELADA' | 'ATENDIDA';
export type Modalidad = 'PRESENCIAL' | 'VIRTUAL';
export type SiguientePaso = 'NINGUNO' | 'DENTRO_DEL_PLAN' | 'CAMBIO_DE_PLAN';

export const MOTIVOS = ['Revisar el avance', 'Cambio en los requerimientos', 'Duda técnica', 'Reportar un error', 'Otro'] as const;
export const SIGUIENTES_PASOS: { valor: SiguientePaso; texto: string }[] = [
  { valor: 'NINGUNO', texto: 'Nada, solo información' },
  { valor: 'DENTRO_DEL_PLAN', texto: 'Lo hacemos dentro del plan' },
  { valor: 'CAMBIO_DE_PLAN', texto: 'Proponer un cambio de plan' },
];
export const TEXTO_SIGUIENTE_PASO: Record<SiguientePaso, string> = {
  NINGUNO: 'Nada, solo información',
  DENTRO_DEL_PLAN: 'Lo hacemos dentro del plan',
  CAMBIO_DE_PLAN: 'Proponer un cambio de plan',
};

export const MIN_RAZON = 20;
export const MAX_RAZON = 500;
export const MIN_MOTIVO_RESPUESTA = 15;
export const MAX_MOTIVO_RESPUESTA = 300;
export const MIN_RESUMEN = 30;
export const MAX_RESUMEN = 1000;
export const MIN_PIDIO = 20;
export const MAX_PIDIO = 600;

/** Horas de inicio de una reunión, en hora de Colombia (RN-01): de 8 a 11 y de 14 a 17. */
export const HORAS_DE_INICIO = [8, 9, 10, 11, 14, 15, 16, 17];

export interface ResumenReunion {
  resumen: string;
  quePidio: string;
  siguientePaso: SiguientePaso;
  registradoAt: string;
}

export interface Solicitud {
  id: string;
  codigo: string;
  proyectoCodigo: string;
  proyecto: string;
  cliente: string;
  responsable: string;
  fase: string;
  motivo: string;
  razon: string;
  modalidad: Modalidad;
  /** Los horarios que propuso el cliente (uno o dos), como instantes. */
  opciones: string[];
  estado: EstadoSolicitud;
  fechaProgramada: string | null;
  enlace: string | null;
  motivoRechazo: string | null;
  enviadaAt: string;
  resumen: ResumenReunion | null;
  /** Para el Product Owner: la reunión ya ocurrió y falta su resumen. */
  puedeCerrar: boolean;
}

export interface SolicitudesDelProyecto {
  puedeSolicitar: boolean;
  motivos: string[];
  solicitudes: Solicitud[];
}

export interface CuerpoSolicitud {
  motivo: string;
  razon: string;
  modalidad: Modalidad;
  /** Con su desplazamiento: {@code 2026-11-24T10:00:00-05:00}. */
  opcion1: string | null;
  opcion2: string | null;
}

export interface CuerpoResumen {
  resumen: string;
  quePidio: string;
  siguientePaso: SiguientePaso | '';
}

const DIAS = { timeZone: 'America/Bogota' } as const;

/** «jueves 19 de noviembre, 10:00 a. m.», siempre en hora de Colombia. */
export function fechaHoraReunion(instante: string): string {
  return new Date(instante).toLocaleString('es-CO', {
    ...DIAS, weekday: 'long', day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit', hour12: true,
  });
}

/** «19 nov, 10:00 a. m.» para las tablas. */
export function fechaCortaReunion(instante: string): string {
  return new Date(instante).toLocaleString('es-CO', {
    ...DIAS, day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: true,
  }).replace('.,', ',');
}

/** La hora de inicio como la pide el servidor. Colombia no cambia de horario: siempre -05:00. */
export function instanteDeColombia(dia: string, hora: number): string {
  return `${dia}T${String(hora).padStart(2, '0')}:00:00-05:00`;
}

export function etiquetaHora(hora: number): string {
  const h = hora % 12 === 0 ? 12 : hora % 12;
  return `${h}:00 ${hora < 12 ? 'a. m.' : 'p. m.'}`;
}

/** Los próximos días de lunes a viernes (desde mañana), para elegir el día de la reunión. El servidor revisa los festivos. */
export function proximosDiasHabiles(cantidad = 15, desde: Date = new Date()): { valor: string; texto: string }[] {
  const dias: { valor: string; texto: string }[] = [];
  const cursor = new Date(Date.UTC(desde.getUTCFullYear(), desde.getUTCMonth(), desde.getUTCDate()));
  while (dias.length < cantidad) {
    cursor.setUTCDate(cursor.getUTCDate() + 1);
    const semana = cursor.getUTCDay();
    if (semana === 0 || semana === 6) continue;
    dias.push({
      valor: cursor.toISOString().slice(0, 10),
      texto: cursor.toLocaleDateString('es-CO', { timeZone: 'UTC', weekday: 'long', day: 'numeric', month: 'long' }),
    });
  }
  return dias;
}

export const reunionesApi = {
  deProyecto: (codigo: string) => api.obtener<SolicitudesDelProyecto>(`/proyectos/${codigo}/solicitudes-reunion`),
  solicitar: (codigo: string, cuerpo: CuerpoSolicitud) => api.crear<Solicitud>(`/proyectos/${codigo}/solicitudes-reunion`, cuerpo),
  listar: () => api.obtener<Solicitud[]>('/solicitudes-reunion'),
  programar: (id: string, opcion: number, enlace: string) =>
    api.modificar<Solicitud>(`/solicitudes-reunion/${id}`, { accion: 'PROGRAMAR', opcion, enlace }),
  rechazar: (id: string, motivo: string) => api.modificar<Solicitud>(`/solicitudes-reunion/${id}`, { accion: 'RECHAZAR', motivo }),
  cancelar: (id: string, motivo: string) => api.modificar<Solicitud>(`/solicitudes-reunion/${id}`, { accion: 'CANCELAR', motivo }),
  resumen: (id: string, cuerpo: CuerpoResumen) => api.crear<Solicitud>(`/solicitudes-reunion/${id}/resumen`, cuerpo),
};
