import { api } from '../api/cliente';
import type { EstadoPlanProyecto } from '../proyectos/plan';

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
  pasosHechos: number;
  pasosTotal: number;
  pasos: PasoDetalle[];
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

export const seguimiento = {
  detalle: (codigo: string) => api.obtener<DetalleProyecto>(`/proyectos/${codigo}`),
  marcarHecho: (pasoId: string, nota: string) =>
    api.modificar<PasoRegistrado>(`/pasos/${pasoId}`, { estado: 'HECHO', nota }),
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

/** La fase en la que se trabaja: la primera que todavía no está aprobada. */
export const faseActual = (d: DetalleProyecto): FaseDetalle | undefined =>
  d.fases.find((f) => f.estado !== 'APROBADA');
