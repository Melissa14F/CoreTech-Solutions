import { api } from '../api/cliente';

export type EstadoFranja = 'LIBRE' | 'OCUPADO' | 'NO_HABIL';
export type EstadoCita = 'PENDIENTE' | 'CONFIRMADA' | 'ATENDIDA' | 'CANCELADA';
export type Modalidad = 'PRESENCIAL' | 'VIRTUAL';

/** Una franja de una hora de GET /citas/disponibilidad (docs/04-api.md, flujo 1). */
export interface Franja {
  /** Día en la zona del cliente (AAAA-MM-DD). */
  fecha: string;
  /** Hora en la zona del cliente (HH:mm). */
  hora: string;
  /** Instante con el desplazamiento del cliente: se envía tal cual a POST /citas. */
  fechaHora: string;
  /** El mismo instante en hora de Colombia (puede ser otro día). */
  horaColombia: string;
  estado: EstadoFranja;
  /** El cliente ya tiene una cita a esa hora (RN-03). */
  tuya: boolean;
}

export interface SolicitudCita {
  servicioId: string;
  fechaHora: string;
  modalidad: Modalidad;
  descripcionNecesidad: string;
  pais: string;
  zonaHoraria: string;
}

export interface CitaCreada {
  citaId: string;
  codigo: string;
  estado: EstadoCita;
  fechaHoraLocal: string;
  fechaHoraColombia: string;
}

/** Una fila de "Mis citas" (GET /citas). */
export interface CitaPropia {
  citaId: string;
  codigo: string;
  servicioId: string;
  servicio: string;
  estado: EstadoCita;
  modalidad: Modalidad;
  fechaHoraLocal: string;
  fechaHoraColombia: string;
  pais: string;
  zonaHoraria: string;
  descripcionNecesidad: string;
  enlaceReunion: string | null;
  motivoCancelacion: string | null;
  puedeCancelar: boolean;
}

export const citas = {
  disponibilidad: (servicioId: string, mes: string, zona: string) =>
    api.obtener<Franja[]>(`/citas/disponibilidad?${new URLSearchParams({ servicioId, mes, zona })}`),
  crear: (solicitud: SolicitudCita) => api.crear<CitaCreada>('/citas', solicitud),
  mias: () => api.obtener<CitaPropia[]>('/citas'),
  cancelar: (citaId: string) =>
    api.modificar<{ citaId: string; estado: EstadoCita; canceladaAt: string }>(`/citas/${citaId}/cancelar`),
};
