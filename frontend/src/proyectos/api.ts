import { api } from '../api/cliente';
import { aEnvio, type FaseApi, type FaseEditable } from './plan';

/** Peticiones de la plantilla de fases de un servicio (HU-27). Contrato: docs/04-api.md, flujo "Crear proyecto". */
export const plantillas = {
  obtener: (servicioId: string) => api.obtener<{ fases: FaseApi[] }>(`/servicios/${servicioId}/plantilla`),
  guardar: (servicioId: string, fases: FaseEditable[]) =>
    api.reemplazar<{ fases: FaseApi[] }>(`/servicios/${servicioId}/plantilla`, aEnvio(fases)),
};
