/** Formatos de las pantallas de administración, en la hora de Colombia (America/Bogota) como el prototipo. */

const ZONA = 'America/Bogota';
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

const dia = new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' });
const hora = new Intl.DateTimeFormat('es-CO', { timeZone: ZONA, hour: 'numeric', minute: '2-digit', hour12: true });

/** Día en Colombia como AAAA-MM-DD. */
export function diaColombia(fecha: Date): string {
  return dia.format(fecha);
}

/** "11:20 a. m." (con espacios normales, para que se lea y se pruebe igual en todos los navegadores). */
export function horaColombia(fecha: Date): string {
  return hora.format(fecha).replace(/[  ]/g, ' ');
}

function diasEntre(desde: string, hasta: string): number {
  return Math.round((Date.parse(hasta) - Date.parse(desde)) / 86_400_000);
}

function fechaCorta(diaIso: string, conAnio: boolean): string {
  const [anio, mes, d] = diaIso.split('-').map(Number);
  return `${d} ${MESES[(mes ?? 1) - 1]}${conAnio ? ` ${anio}` : ''}`;
}

/** Último acceso de una cuenta: "Hoy, 11:20 a. m.", "Ayer, 5:02 p. m.", "Hace 6 días" o "15 sep 2026". */
export function ultimoAcceso(iso: string | null, ahora = new Date()): string {
  if (!iso) return 'Aún no ha ingresado';
  const fecha = new Date(iso);
  const dias = diasEntre(diaColombia(fecha), diaColombia(ahora));
  if (dias <= 0) return `Hoy, ${horaColombia(fecha)}`;
  if (dias === 1) return `Ayer, ${horaColombia(fecha)}`;
  if (dias < 30) return `Hace ${dias} días`;
  return fechaCorta(diaColombia(fecha), true);
}

/** Fecha de un evento de la auditoría: "Hoy, 11:20 a. m.", "Ayer, …", "9 nov, 4:20 p. m." o "9 nov 2025, …". */
export function fechaEvento(iso: string, ahora = new Date()): string {
  const fecha = new Date(iso);
  const diaEvento = diaColombia(fecha);
  const dias = diasEntre(diaEvento, diaColombia(ahora));
  const horaTexto = horaColombia(fecha);
  if (dias <= 0) return `Hoy, ${horaTexto}`;
  if (dias === 1) return `Ayer, ${horaTexto}`;
  return `${fechaCorta(diaEvento, diaEvento.slice(0, 4) !== diaColombia(ahora).slice(0, 4))}, ${horaTexto}`;
}

/** "Laura Gómez" → "LG". */
export function iniciales(nombre: string): string {
  return nombre.trim().split(/\s+/).slice(0, 2).map((p) => p.charAt(0).toUpperCase()).join('');
}

/** "3 resultados", "1 resultado". */
export function resultados(n: number): string {
  return `${n} ${n === 1 ? 'resultado' : 'resultados'}`;
}

export function plural(n: number, singular: string, varios: string): string {
  return `${n} ${n === 1 ? singular : varios}`;
}
