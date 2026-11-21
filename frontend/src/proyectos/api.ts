import { api } from '../api/cliente';
import {
  aEnvio, aEnvioPlan, type CotizacionEditable, type FaseApi, type FaseEditable, type PlanProyecto,
} from './plan';

/** Peticiones de la plantilla de fases de un servicio (HU-27). Contrato: docs/04-api.md, flujo "Crear proyecto". */
export const plantillas = {
  obtener: (servicioId: string) => api.obtener<{ fases: FaseApi[] }>(`/servicios/${servicioId}/plantilla`),
  guardar: (servicioId: string, fases: FaseEditable[]) =>
    api.reemplazar<{ fases: FaseApi[] }>(`/servicios/${servicioId}/plantilla`, aEnvio(fases)),
};

/** Peticiones del plan de trabajo y la cotización de un proyecto (HU-28, HU-29). Contrato: docs/04-api.md, flujo 4. */
export interface PlanEnviado {
  estadoPlan: 'ENVIADO';
  porcentajePorFase: number;
  plazoRespuesta: string;
  venceEl: string;
}

export interface RespuestaPlan {
  estadoPlan: string;
  estado: string;
  respuestaId: string;
}

export const planes = {
  obtener: (codigo: string) => api.obtener<PlanProyecto>(`/proyectos/${codigo}/plan`),
  guardar: (codigo: string, fases: FaseEditable[], cotizacion: CotizacionEditable) =>
    api.reemplazar<PlanProyecto>(`/proyectos/${codigo}/plan`, aEnvioPlan(fases, cotizacion)),
  enviar: (codigo: string, fases: FaseEditable[], cotizacion: CotizacionEditable) =>
    api.crear<PlanEnviado>(`/proyectos/${codigo}/plan/enviar`, aEnvioPlan(fases, cotizacion)),
  recordar: (codigo: string) => api.crear<void>(`/proyectos/${codigo}/plan/recordar`),
  aprobar: (codigo: string) => api.crear<{ estadoPlan: string; estado: string; planAprobadoAt: string }>(
    `/proyectos/${codigo}/plan/aprobar`, { aceptaPlanYCotizacion: true }),
  pedirAjustes: (codigo: string, comentario: string) =>
    api.crear<RespuestaPlan>(`/proyectos/${codigo}/plan/ajustes`, { comentario }),
  rechazar: (codigo: string, motivo: string) => api.crear<RespuestaPlan>(`/proyectos/${codigo}/plan/rechazar`, { motivo }),
};
