import { api, ErrorApi } from '../api/cliente';
import type { EstadoPlanProyecto } from '../proyectos/plan';

/**
 * El servidor respondió 409 sin un campo concreto: lo que se veía ya cambió (otra pestaña se adelantó, HU-03 «Datos
 * desactualizados»; o la fase ya no estaba en ese estado). La pantalla muestra el mensaje y vuelve a pedir el proyecto.
 */
export function esConflictoDeEstado(e: unknown): e is ErrorApi {
  return e instanceof ErrorApi && e.estado === 409 && Object.keys(e.porCampo).length === 0;
}

/** Lo que el cliente, el equipo y el administrador ven de un proyecto (GET /proyectos/{codigo}, HU-04). */
export type EstadoFase = 'PENDIENTE' | 'EN_CURSO' | 'EN_REVISION' | 'APROBADA' | 'EN_CORRECCION';
export type EstadoPaso = 'PENDIENTE' | 'EN_CURSO' | 'HECHO';
export type TipoPaso = 'PASO' | 'CORRECCION' | 'AJUSTE' | 'ALCANCE';

export interface Adjunto {
  id: string;
  titulo: string;
  tipoMime: string;
  tamanoBytes: number;
  /** Ruta de la API con control de acceso: se abre con la sesión, no es un enlace público. */
  url: string;
}

export interface PasoDetalle {
  id: string;
  orden: number;
  tipo: TipoPaso;
  titulo: string;
  explicacion: string | null;
  origen: string | null;
  estado: EstadoPaso;
  nota: string | null;
  hechoAt: string | null;
  adjuntos: Adjunto[];
}

/** Algo que pidió el cliente al revisar una fase: un ajuste o algo nuevo por cotizar (HU-11). */
export interface CambioPedido {
  tipo: 'AJUSTE' | 'ALCANCE';
  descripcion: string;
  estado: 'ABIERTO' | 'POR_COTIZAR' | 'COTIZADO' | 'ACEPTADO' | 'RECHAZADO' | 'RESUELTO';
  creadoAt: string;
}

export interface FaseDetalle {
  id: string;
  orden: number;
  nombre: string;
  queEs: string | null;
  apruebaCliente: boolean;
  estado: EstadoFase;
  /** Lo que vale la fase en el proyecto: 100 entre el número de fases (RN-08). */
  porcentaje: number;
  iniciadaAt: string | null;
  entregadaAt: string | null;
  aprobadaAt: string | null;
  rondasAjusteUsadas: number;
  aprobacionAutomatica: boolean;
  /** Último día para que el cliente responda; solo con la fase EN_REVISION (RN-17). */
  plazoAprobacion: string | null;
  pasosHechos: number;
  pasosTotal: number;
  pasos: PasoDetalle[];
  cambios: CambioPedido[];
}

export interface Persona {
  nombre: string;
  rol: string;
}

export interface DetalleProyecto {
  codigo: string;
  nombre: string;
  servicio: string;
  citaCodigo: string | null;
  estado: 'ACTIVO' | 'PAUSADO' | 'EN_GARANTIA' | 'FINALIZADO' | 'CANCELADO';
  estadoPlan: EstadoPlanProyecto;
  avancePorcentaje: number;
  fechaInicio: string;
  fechaEstimadaEntrega: string;
  /** Fin de la garantía de 30 días (RN-31); nulo hasta que se completa la última fase. */
  garantiaHasta: string | null;
  faseActual: { orden: number; nombre: string } | null;
  pasosHechos: number;
  ultimoPaso: { fecha: string; titulo: string } | null;
  cliente: string;
  clienteCorreo: string;
  productOwner: string;
  productOwnerCorreo: string;
  equipo: Persona[];
  puedeRegistrar: boolean;
  fases: FaseDetalle[];
}

export interface PasoRegistrado {
  pasoId: string;
  hechoAt: string;
  /** "4 de 5" en la fase del paso. */
  pasosHechos: string;
}

/** Límites de HU-03: hasta 5 archivos PNG, JPG, SVG o PDF de máximo 5 MB. El servidor los vuelve a revisar por su contenido. */
export const MAX_ADJUNTOS = 5;
export const MAX_BYTES_ADJUNTO = 5 * 1024 * 1024;
export const EXTENSIONES_ADJUNTO = ['png', 'jpg', 'jpeg', 'svg', 'pdf'];
export const MIN_NOTA = 20;
export const MAX_NOTA = 400;
export const MAX_NOTA_CORRECCION = 500;

/** Resultado de entregar o cerrar una fase (HU-13). */
export interface FaseEntregada {
  estado: 'EN_REVISION' | 'APROBADA';
  plazoAprobacion: string | null;
  avancePorcentaje: number;
  estadoProyecto: DetalleProyecto['estado'];
  garantiaHasta: string | null;
}

export interface FaseAprobada {
  estado: 'APROBADA';
  avancePorcentaje: number;
  estadoProyecto: DetalleProyecto['estado'];
  garantiaHasta: string | null;
}

export interface CambioCreado {
  solicitudId: string;
  tipo: 'AJUSTE' | 'ALCANCE';
  estado: string;
  rondasUsadas: number;
  estadoFase: EstadoFase;
  pasoId: string | null;
}

export interface CorreccionRegistrada {
  pasoId: string;
  estadoFase: EstadoFase;
  avancePorcentaje: number;
}

/** De dónde sale una corrección (HU-12): los tres textos que valida el servidor. */
export const ORIGENES_CORRECCION = ['Lo pidió el cliente', 'La encontramos al construir', 'La encontramos en pruebas'] as const;
export const MAX_RONDAS_AJUSTE = 2;
export const MIN_CAMBIO = 20;
export const MAX_CAMBIO = 800;
export const MAX_EXPLICACION = 400;

export const seguimiento = {
  detalle: (codigo: string) => api.obtener<DetalleProyecto>(`/proyectos/${codigo}`),
  marcarHecho: (pasoId: string, nota: string) =>
    api.modificar<PasoRegistrado>(`/pasos/${pasoId}`, { estado: 'HECHO', nota }),
  entregarFase: (faseId: string) => api.crear<FaseEntregada>(`/fases/${faseId}/entregar`),
  aprobarFase: (faseId: string) => api.crear<FaseAprobada>(`/fases/${faseId}/aprobar`),
  pedirCambio: (faseId: string, tipo: 'AJUSTE' | 'ALCANCE', descripcion: string) =>
    api.crear<CambioCreado>(`/fases/${faseId}/solicitudes-cambio`, { tipo, descripcion }),
  registrarCorreccion: (faseId: string, origen: string, titulo: string, explicacion: string) =>
    api.crear<CorreccionRegistrada>(`/fases/${faseId}/pasos`, { origen, titulo, explicacion }),
  adjuntar: (pasoId: string, archivos: File[]) => {
    const datos = new FormData();
    archivos.forEach((a) => datos.append('archivos', a));
    return api.crear<Adjunto[]>(`/pasos/${pasoId}/adjuntos`, datos);
  },
};

/** Revisa los archivos elegidos antes de enviarlos (la misma regla del servidor, para avisar sin esperar). */
export function validarAdjuntos(archivos: File[]): string | null {
  if (archivos.length > MAX_ADJUNTOS) return `Elige máximo ${MAX_ADJUNTOS} archivos.`;
  for (const a of archivos) {
    const extension = a.name.split('.').pop()?.toLowerCase() ?? '';
    if (!EXTENSIONES_ADJUNTO.includes(extension)) return `«${a.name}» no es un PNG, JPG, SVG o PDF.`;
    if (a.size > MAX_BYTES_ADJUNTO) return `«${a.name}» pesa más de 5 MB.`;
    if (a.size === 0) return `«${a.name}» está vacío.`;
  }
  return null;
}

/** El paso que aparece "en curso": el primero que falta de la fase actual (solo se muestra, no se guarda). */
export function pasoEnCurso(fase: FaseDetalle): string | null {
  if (fase.estado !== 'EN_CURSO') return null;
  return fase.pasos.find((p) => p.estado !== 'HECHO')?.id ?? null;
}

/** La fase en la que se trabaja: la primera que está en curso o esperando al cliente. Una anterior en corrección no lo es. */
export const faseActual = (d: DetalleProyecto): FaseDetalle | undefined =>
  d.fases.find((f) => f.estado === 'EN_CURSO' || f.estado === 'EN_REVISION');

const abierto = (p: PasoDetalle) => p.estado !== 'HECHO';

/** Correcciones sin terminar de una fase (RN-16): bloquean entregarla. */
export const correccionesAbiertas = (fase: FaseDetalle): PasoDetalle[] => fase.pasos.filter((p) => p.tipo === 'CORRECCION' && abierto(p));

/** Pasos por hacer de una fase: del plan, ajustes y correcciones. */
export const pasosPorHacer = (fase: FaseDetalle): PasoDetalle[] => fase.pasos.filter(abierto);

/** La fase que sigue a otra en el plan, si hay. */
export const faseSiguiente = (d: DetalleProyecto, fase: FaseDetalle): FaseDetalle | undefined =>
  d.fases.find((f) => f.orden > fase.orden);

/** El proyecto ya cumplió sus 30 días de garantía (RN-31): solo se terminan las correcciones abiertas. */
export const garantiaCumplida = (d: DetalleProyecto, hoy: string): boolean =>
  d.estado === 'EN_GARANTIA' && d.garantiaHasta !== null && hoy >= d.garantiaHasta;
