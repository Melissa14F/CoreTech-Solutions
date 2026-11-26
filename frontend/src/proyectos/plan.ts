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

export function validarPlan(fases: FaseEditable[], cliente = 'el cliente', ambito = 'este servicio'): ErroresPlan {
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
      porCampo[`${campo}.nombre`] = `Ya hay una fase con ese nombre en ${ambito}`;
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

// ------------------------------------------------------------------------------------------ Plan de un proyecto (HU-28, HU-29)

export type EstadoPlanProyecto = 'BORRADOR' | 'ENVIADO' | 'AJUSTES_PEDIDOS' | 'APROBADO' | 'VENCIDO' | 'RECHAZADO';

export const VALOR_MINIMO = 1_000_000;
export const VALOR_MAXIMO = 200_000_000;

/** Comentario de ajustes o motivo de rechazo que escribió el cliente. */
export interface ComentarioCliente {
  texto: string;
  fecha: string;
}

/** GET /proyectos/{codigo}/plan: el plan como lo ven el equipo y el cliente. */
export interface PlanProyecto {
  codigo: string;
  nombre: string;
  estado: string;
  estadoPlan: EstadoPlanProyecto;
  cliente: string;
  productOwner: string;
  servicio: string;
  citaCodigo: string | null;
  citaFecha: string | null;
  fechaInicio: string;
  fechaEstimadaEntrega: string;
  valorCotizado: number | null;
  condicionesCotizacion: string | null;
  creadoAt: string;
  enviadoAt: string | null;
  aprobadoAt: string | null;
  plazoRespuesta: string | null;
  venceEl: string | null;
  fases: FaseApi[];
  porcentajePorFase: number;
  ajustesPedidos: ComentarioCliente | null;
  rechazo: ComentarioCliente | null;
  editable: boolean;
}

/** Lo que se escribe en la tarjeta de cotización; el valor son solo los dígitos ("7800000"). */
export interface CotizacionEditable {
  valor: string;
  entrega: string;
  condiciones: string;
}

export const cotizacionDe = (plan: PlanProyecto): CotizacionEditable => ({
  valor: plan.valorCotizado === null ? '' : String(plan.valorCotizado),
  entrega: plan.fechaEstimadaEntrega,
  condiciones: plan.condicionesCotizacion ?? '',
});

/** Cuerpo de PUT .../plan y POST .../plan/enviar. */
export const aEnvioPlan = (fases: FaseEditable[], c: CotizacionEditable) => ({
  ...aEnvio(fases),
  valorCotizado: c.valor === '' ? null : Number(c.valor),
  condicionesCotizacion: c.condiciones.trim() || null,
  fechaEstimadaEntrega: c.entrega || null,
});

/** Para saber si hay cambios sin guardar en el plan completo. */
export const huellaPlan = (fases: FaseEditable[], c: CotizacionEditable): string => JSON.stringify(aEnvioPlan(fases, c));

export const soloDigitos = (texto: string): string => texto.replace(/\D/g, '');

/** "7800000" → "7.800.000". */
export const conPuntos = (digitos: string): string => digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

/** AAAA-MM-DD más {@code dias} días (sin depender de la zona horaria). */
export function sumarDiasIso(iso: string, dias: number): string {
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(Date.UTC(a ?? 1970, (m ?? 1) - 1, (d ?? 1) + dias)).toISOString().slice(0, 10);
}

/**
 * La entrega más lejana que se acepta: 5 años desde hoy (AAAA-MM-DD). Sirve para atrapar un año mal escrito; la misma regla
 * la aplica el backend (`ServicioPlan.ultimaFechaDeEntrega`). Si hoy es 29 de febrero y el año de llegada no es bisiesto, cae en el 28.
 */
export function ultimaFechaDeEntrega(hoy: Date = new Date()): string {
  const anio = hoy.getFullYear() + 5;
  const ultimoDelMes = new Date(Date.UTC(anio, hoy.getMonth() + 1, 0)).getUTCDate();
  return new Date(Date.UTC(anio, hoy.getMonth(), Math.min(hoy.getDate(), ultimoDelMes))).toISOString().slice(0, 10);
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

/** "2026-11-17" → "martes 17 nov". */
export function fechaConDia(iso: string): string {
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
  const dia = new Date(Date.UTC(a ?? 1970, (m ?? 1) - 1, d ?? 1)).getUTCDay();
  return `${DIAS[dia]} ${d} ${MESES[(m ?? 1) - 1]}`;
}

/** "Miguel Ángel Duarte" → "Miguel": los textos del prototipo llaman al cliente por su nombre. */
export const nombrePila = (nombre: string): string => nombre.trim().split(/\s+/)[0] || 'el cliente';

/**
 * Cotización: valor entre $1.000.000 y $200.000.000, entrega al menos 2 semanas después del inicio y qué incluye (20 a 600).
 * Mismos mensajes que el backend (ServicioPlan) y el prototipo (js/vistas/plan-de-trabajo.js).
 */
export function validarCotizacion(c: CotizacionEditable, inicio: string, fechaLegible: (iso: string) => string): Record<string, string> {
  const errores: Record<string, string> = {};
  if (c.valor === '') errores.valorCotizado = 'Escribe el valor del proyecto.';
  else {
    const valor = Number(c.valor);
    if (valor < VALOR_MINIMO || valor > VALOR_MAXIMO) errores.valorCotizado = 'El valor debe estar entre $1.000.000 y $200.000.000.';
  }
  if (!c.entrega) errores.fechaEstimadaEntrega = 'Elige la fecha estimada de entrega.';
  else if (c.entrega < sumarDiasIso(inicio, 14)) {
    errores.fechaEstimadaEntrega = `La entrega debe ser al menos 2 semanas después del inicio (${fechaLegible(inicio)}).`;
  } else if (c.entrega > ultimaFechaDeEntrega()) errores.fechaEstimadaEntrega = 'Revisa el año de la fecha.';
  const condiciones = c.condiciones.trim();
  if (!condiciones) errores.condicionesCotizacion = 'Escribe qué incluye el valor.';
  else if (condiciones.length < 20) errores.condicionesCotizacion = 'Escribe al menos 20 caracteres.';
  return errores;
}
