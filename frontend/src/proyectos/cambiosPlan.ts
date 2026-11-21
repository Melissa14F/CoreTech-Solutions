/** Cambios de un plan aprobado: proponerlos, autorizarlos y aceptarlos (HU-30, RN-30, D-32, D-33). */
import { api } from '../api/cliente';

export type TipoCambio = 'AGREGAR_FASE' | 'QUITAR_FASE' | 'CAMBIAR_FECHA' | 'QUITAR_PASO' | 'AGREGAR_ALCANCE';
export type EstadoCambio = 'PROPUESTO' | 'AUTORIZADO' | 'ACEPTADO' | 'RECHAZADO_ADMIN' | 'RECHAZADO_CLIENTE';

export const MIN_MOTIVO = 20;
export const MAX_MOTIVO = 500;
export const MIN_RECHAZO = 10;
export const MAX_RECHAZO = 400;
export const VALOR_ADICIONAL_MAXIMO = 200_000_000;

/** Cómo lo dice cada pantalla: «Agregar una fase», «Quitar un paso»… */
export const TEXTO_TIPO: Record<TipoCambio, string> = {
  AGREGAR_FASE: 'Agregar una fase',
  QUITAR_FASE: 'Quitar una fase',
  CAMBIAR_FECHA: 'Cambiar la fecha de entrega',
  QUITAR_PASO: 'Quitar un paso',
  AGREGAR_ALCANCE: 'Cotizar un trabajo nuevo',
};

export const TEXTO_ESTADO: Record<EstadoCambio, { texto: string; clase: string }> = {
  PROPUESTO: { texto: 'Por autorizar', clase: 'insignia--pendiente' },
  AUTORIZADO: { texto: 'Por aceptar', clase: 'insignia--pendiente' },
  ACEPTADO: { texto: 'Aplicado', clase: 'insignia--atendida' },
  RECHAZADO_ADMIN: { texto: 'No autorizado', clase: 'insignia--cancelada' },
  RECHAZADO_CLIENTE: { texto: 'No aceptado', clase: 'insignia--cancelada' },
};

export interface FaseDeCambio {
  nombre: string;
  /** Lo que vale la fase en el proyecto: 100 entre el número de fases (RN-08). */
  porcentaje: number;
  estado: string;
}

/** Cómo está o cómo queda el plan. */
export interface Foto {
  fases: FaseDeCambio[];
  avance: number;
  entrega: string;
  valorTotal: number;
}

export interface PasoNuevo {
  titulo: string;
  explicacion: string | null;
}

export interface Cambio {
  id: string;
  proyectoCodigo: string;
  proyecto: string;
  cliente: string;
  tipo: TipoCambio;
  estado: EstadoCambio;
  motivo: string;
  propuestoPor: string;
  creadoAt: string;
  autorizadoAt: string | null;
  respondidoAt: string | null;
  motivoRechazo: string | null;
  /** La fase que se agrega o se quita. */
  faseNombre: string | null;
  posicion: number | null;
  apruebaCliente: boolean | null;
  pasosNuevos: PasoNuevo[] | null;
  pasoTitulo: string | null;
  pasoFase: string | null;
  tituloPaso: string | null;
  explicacionPaso: string | null;
  /** Lo que pidió el cliente y se cotiza (AGREGAR_ALCANCE). */
  solicitud: string | null;
  nuevaEntrega: string | null;
  valorAdicional: number;
  /** Solo mientras el cambio está abierto. */
  antes: Foto | null;
  despues: Foto | null;
  /** Quitar un paso de una fase que cierra el equipo: se aplica al autorizar y solo se avisa al cliente. */
  seAplicaAlAutorizar: boolean;
  puedeAutorizar: boolean;
  puedeAceptar: boolean;
}

export interface AlcancePorCotizar {
  id: string;
  fase: string;
  descripcion: string;
  creadaAt: string;
}

export interface CambiosDelProyecto {
  puedeProponer: boolean;
  hayCambioAbierto: boolean;
  alcancesPorCotizar: AlcancePorCotizar[];
  cambios: Cambio[];
  valorCotizado: number;
  valorAdicionalAceptado: number;
  valorTotal: number;
}

export interface Propuesta {
  tipo: TipoCambio;
  motivo: string;
  nuevaEntrega?: string;
  valorAdicional?: number;
  nombreFase?: string;
  posicion?: number;
  apruebaCliente?: boolean;
  pasos?: PasoNuevo[];
  faseId?: string;
  pasoId?: string;
  solicitudCambioId?: string;
  tituloPaso?: string;
  explicacionPaso?: string;
}

export const cambiosPlanApi = {
  deProyecto: (codigo: string) => api.obtener<CambiosDelProyecto>(`/proyectos/${codigo}/cambios-plan`),
  proponer: (codigo: string, cuerpo: Propuesta) =>
    api.crear<{ cambioId: string; estado: string; fasesAntes: number; fasesDespues: number; avanceDespues: number }>(`/proyectos/${codigo}/cambios-plan`, cuerpo),
  porAutorizar: () => api.obtener<Cambio[]>('/cambios-plan'),
  autorizar: (id: string) => api.modificar<{ cambioId: string; estado: EstadoCambio }>(`/cambios-plan/${id}/autorizar`),
  aceptar: (id: string) =>
    api.modificar<{ estado: string; fases: number; avancePorcentaje: number; fechaEstimadaEntrega: string; valorTotal: number }>(`/cambios-plan/${id}/aceptar`),
  rechazar: (id: string, motivo: string) => api.modificar<{ cambioId: string; estado: EstadoCambio }>(`/cambios-plan/${id}/rechazar`, { motivo }),
};
