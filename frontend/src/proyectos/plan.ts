/**
 * Modelo del editor de fases y pasos (plantilla de un servicio, HU-27; el plan de un proyecto lo reutiliza, HU-28).
 * Reglas de RN-28 con los mismos mensajes que el backend (ServicioPlantillas) y el prototipo (js/plan-editor.js).
 */

export const MIN_FASES = 2;
export const MAX_FASES = 8;
export const MAX_PASOS = 12;

export interface PasoEditable {
  /** Identificador local para que React conserve el foco al reordenar; no se envía. */
  clave: string;
  titulo: string;
  explicacion: string;
}

export interface FaseEditable {
  clave: string;
  nombre: string;
  queEs: string;
  apruebaCliente: boolean;
  pasos: PasoEditable[];
}

/** Errores por campo con las mismas claves que devuelve la API: `fases`, `fases[0].nombre`, `fases[0].pasos[1].titulo`… */
export interface ErroresPlan {
  /** Mensajes de la lista de arriba ("Fase 2: completa el nombre y los pasos", "El plan necesita al menos 2 fases"…). */
  generales: string[];
  porCampo: Record<string, string>;
}

export const SIN_ERRORES: ErroresPlan = { generales: [], porCampo: {} };

let contador = 0;
const nuevaClave = (prefijo: string) => `${prefijo}${++contador}`;

export const nuevoPaso = (titulo = '', explicacion = ''): PasoEditable => ({ clave: nuevaClave('p'), titulo, explicacion });

export const nuevaFase = (): FaseEditable => ({ clave: nuevaClave('f'), nombre: '', queEs: '', apruebaCliente: false, pasos: [nuevoPaso()] });

/** Lo que devuelve la API (GET /servicios/{id}/plantilla). */
export interface FaseApi {
  orden: number;
  nombre: string;
  queEs: string | null;
  apruebaCliente: boolean;
  pasos: { orden: number; titulo: string; explicacion: string | null }[];
}

export const aEditable = (fases: FaseApi[]): FaseEditable[] =>
  fases.map((f) => ({
    clave: nuevaClave('f'),
    nombre: f.nombre,
    queEs: f.queEs ?? '',
    apruebaCliente: f.apruebaCliente,
    pasos: f.pasos.map((p) => nuevoPaso(p.titulo, p.explicacion ?? '')),
  }));

/** Lo que se envía al guardar: el orden es el de la lista y los textos van sin espacios de más. */
export const aEnvio = (fases: FaseEditable[]) => ({
  fases: fases.map((f) => ({
    nombre: f.nombre.trim(),
    queEs: f.queEs.trim() || null,
    apruebaCliente: f.apruebaCliente,
    pasos: f.pasos.map((p) => ({ titulo: p.titulo.trim(), explicacion: p.explicacion.trim() || null })),
  })),
});

/** Para saber si hay cambios sin guardar: compara el contenido, sin las claves locales ni los espacios de más. */
export const huella = (fases: FaseEditable[]): string => JSON.stringify(aEnvio(fases));

/** Cada fase vale lo mismo: 100 % dividido entre el número de fases ("20 %", "16,7 %"). */
export function porcentajeFase(fases: number): string {
  const valor = Math.round((100 / fases) * 10) / 10;
  return `${String(valor).replace('.', ',')} %`;
}

export function validarPlan(fases: FaseEditable[], cliente = 'el cliente'): ErroresPlan {
  const porCampo: Record<string, string> = {};
  const generales: string[] = [];
  const vistos = new Set<string>();

  fases.forEach((fase, f) => {
    const campo = `fases[${f}]`;
    let incompleta = false;
    const nombre = fase.nombre.trim();
    if (nombre.length < 3) {
      porCampo[`${campo}.nombre`] = 'Escribe el nombre de la fase (al menos 3 caracteres).';
      incompleta = true;
    } else if (nombre.length > 60) {
      porCampo[`${campo}.nombre`] = 'El nombre de la fase puede tener máximo 60 caracteres.';
    } else if (vistos.has(nombre.toLowerCase())) {
      porCampo[`${campo}.nombre`] = 'Ya hay una fase con ese nombre en este servicio';
    } else {
      vistos.add(nombre.toLowerCase());
    }
    if (fase.queEs.trim().length > 140) porCampo[`${campo}.queEs`] = 'Escribe máximo 140 caracteres.';

    if (fase.pasos.length === 0) {
      porCampo[`${campo}.pasos`] = 'Agrega al menos un paso.';
      incompleta = true;
    } else if (fase.pasos.length > MAX_PASOS) {
      porCampo[`${campo}.pasos`] = `Cada fase puede tener máximo ${MAX_PASOS} pasos.`;
    }
    fase.pasos.forEach((paso, p) => {
      const titulo = paso.titulo.trim();
      if (titulo.length < 3) {
        porCampo[`${campo}.pasos[${p}].titulo`] = 'Escribe qué se hace en este paso.';
        incompleta = true;
      } else if (titulo.length > 80) {
        porCampo[`${campo}.pasos[${p}].titulo`] = 'El título del paso puede tener máximo 80 caracteres.';
      }
      if (paso.explicacion.trim().length > 160) porCampo[`${campo}.pasos[${p}].explicacion`] = 'Escribe máximo 160 caracteres.';
    });
    if (incompleta) generales.push(`Fase ${f + 1}: completa el nombre y los pasos`);
  });

  if (fases.length < MIN_FASES) generales.push(`El plan necesita al menos ${MIN_FASES} fases`);
  if (fases.length > MAX_FASES) generales.push(`El plan puede tener máximo ${MAX_FASES} fases`);
  if (!fases.some((f) => f.apruebaCliente)) generales.push(`Marca al menos una fase que apruebe ${cliente}`);

  const hayErrores = generales.length > 0 || Object.keys(porCampo).length > 0;
  return hayErrores ? { generales, porCampo } : SIN_ERRORES;
}

export const hayErrores = (e: ErroresPlan) => e.generales.length > 0 || Object.keys(e.porCampo).length > 0;
