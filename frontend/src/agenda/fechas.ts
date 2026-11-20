/**
 * Fechas y horas del agendamiento. La API devuelve instantes con su desplazamiento ("2026-11-17T09:00:00-05:00",
 * "2026-11-17T15:00:00+01:00"); como ya vienen en la zona del cliente, se leen tal cual y no se convierten
 * en el navegador (así nunca dependen de la zona del computador).
 */
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const DIAS_CORTOS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const MESES_CORTOS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

const mayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);
const dosDigitos = (n: number) => String(n).padStart(2, '0');

export interface PartesFecha {
  anio: number;
  mes: number;
  dia: number;
  hora: number;
  minuto: number;
}

/** Descompone "2026-11-17" o "2026-11-17T09:00:00-05:00" sin convertir de zona. */
export function partes(iso: string): PartesFecha {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(iso);
  if (!m) throw new Error(`Fecha inválida: ${iso}`);
  return { anio: Number(m[1]), mes: Number(m[2]), dia: Number(m[3]), hora: Number(m[4] ?? 0), minuto: Number(m[5] ?? 0) };
}

/** "2026-11-17" de un ISO con hora. */
export const soloFecha = (iso: string) => iso.slice(0, 10);

export const armarFecha = (anio: number, mes: number, dia: number) => `${anio}-${dosDigitos(mes)}-${dosDigitos(dia)}`;

/** 0 = domingo … 6 = sábado. */
export const diaSemana = (fecha: string) => {
  const p = partes(fecha);
  return new Date(Date.UTC(p.anio, p.mes - 1, p.dia)).getUTCDay();
};

/** Suma días a "2026-11-17" (días de calendario, sin horas). */
export function sumarDias(fecha: string, dias: number): string {
  const p = partes(fecha);
  const d = new Date(Date.UTC(p.anio, p.mes - 1, p.dia + dias));
  return armarFecha(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
}

/** "2026-11" de "2026-11-17". */
export const mesDe = (fecha: string) => fecha.slice(0, 7);

/** Suma meses a "2026-11" ("2026-12", "2027-01"…). */
export function sumarMeses(mes: string, n: number): string {
  const p = partes(`${mes}-01`);
  const d = new Date(Date.UTC(p.anio, p.mes - 1 + n, 1));
  return `${d.getUTCFullYear()}-${dosDigitos(d.getUTCMonth() + 1)}`;
}

/** Cantidad de días del mes "2026-11". */
export function diasDelMes(mes: string): number {
  const p = partes(`${mes}-01`);
  return new Date(Date.UTC(p.anio, p.mes, 0)).getUTCDate();
}

/** "Noviembre 2026". */
export function tituloMes(mes: string): string {
  const p = partes(`${mes}-01`);
  return `${mayuscula(MESES[p.mes - 1]!)} ${p.anio}`;
}

/** "Jueves 12 de noviembre". */
export function textoDia(fecha: string): string {
  const p = partes(fecha);
  return `${mayuscula(DIAS[diaSemana(fecha)]!)} ${p.dia} de ${MESES[p.mes - 1]!}`;
}

/** "Jueves 12 de noviembre de 2026". */
export const textoDiaCompleto = (fecha: string) => `${textoDia(fecha)} de ${partes(fecha).anio}`;

/** "12 nov" (para el plazo "Del 12 nov al 11 dic"). */
export function textoCorto(fecha: string): string {
  const p = partes(fecha);
  return `${p.dia} ${MESES_CORTOS[p.mes - 1]!.toLowerCase()}`;
}

/** Las tres partes del bloque de fecha de una tarjeta de cita: "Jue", 12, "Nov". */
export function bloqueFecha(fecha: string): { diaSemana: string; dia: number; mes: string } {
  const p = partes(fecha);
  return { diaSemana: DIAS_CORTOS[diaSemana(fecha)]!, dia: p.dia, mes: MESES_CORTOS[p.mes - 1]! };
}

/** "9:00 a. m.", "12:00 m.", "5:00 p. m.". */
export function texto12(hora: number, minuto = 0): string {
  const h12 = hora % 12 === 0 ? 12 : hora % 12;
  const sufijo = hora === 12 && minuto === 0 ? 'm.' : hora < 12 ? 'a. m.' : 'p. m.';
  return `${h12}:${dosDigitos(minuto)} ${sufijo}`;
}

/** Hora de un ISO: "9:00 a. m.". */
export function textoHora(iso: string): string {
  const p = partes(iso);
  return texto12(p.hora, p.minuto);
}

/** Cada cita dura una hora: "9:00 a. m. a 10:00 a. m.". */
export function textoRango(iso: string): string {
  const p = partes(iso);
  return `${texto12(p.hora, p.minuto)} a ${texto12((p.hora + 1) % 24, p.minuto)}`;
}

/**
 * Hora de Colombia de una franja. Si cae en otro día que el del cliente (por ejemplo, Australia), lo dice:
 * "Mar 9:00 a. m." o, en largo, "martes 17 de noviembre, 9:00 a. m.".
 */
export function textoHoraColombia(localIso: string, colombiaIso: string, largo = false): string {
  const hora = textoHora(colombiaIso);
  const dia = soloFecha(colombiaIso);
  if (soloFecha(localIso) === dia) return hora;
  return largo ? `${textoDia(dia).toLowerCase()}, ${hora}` : `${DIAS_CORTOS[diaSemana(dia)]!} ${hora}`;
}

/** Fecha de hoy ("2026-11-20") en una zona IANA. */
export function hoyEn(zona: string, ahora: Date = new Date()): string {
  const formato = new Intl.DateTimeFormat('en-CA', { timeZone: zona, year: 'numeric', month: '2-digit', day: '2-digit' });
  const v: Record<string, string> = {};
  formato.formatToParts(ahora).forEach((parte) => {
    v[parte.type] = parte.value;
  });
  return `${v.year}-${v.month}-${v.day}`;
}
