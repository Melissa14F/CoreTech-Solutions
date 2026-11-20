import type { CitaGestion } from './gestion';
import { bloqueFecha, soloFecha, textoCorto, textoDia, textoHora } from './fechas';
import { COLOMBIA } from './paises';

/** "Mar 17 nov · 9:00 a. m." con la hora de Colombia (la hora de atención, RN-01). */
export function fechaColombia(cita: CitaGestion): string {
  const dia = soloFecha(cita.fechaHoraColombia);
  return `${bloqueFecha(dia).diaSemana} ${textoCorto(dia)} · ${textoHora(cita.fechaHoraColombia)}`;
}

/** "Martes 17 de noviembre, 9:00 a. m." con la hora de Colombia. */
export function fechaColombiaLarga(cita: CitaGestion): string {
  return `${textoDia(soloFecha(cita.fechaHoraColombia))}, ${textoHora(cita.fechaHoraColombia)}`;
}

/** Para un cliente fuera de Colombia: "México · para el cliente son las 8:00 a. m." (RN-23). Vacío si vive en Colombia. */
export function horaDelCliente(cita: CitaGestion): string | null {
  if (cita.zonaHoraria === COLOMBIA) return null;
  const mismoDia = soloFecha(cita.fechaHoraLocal) === soloFecha(cita.fechaHoraColombia);
  const hora = textoHora(cita.fechaHoraLocal);
  return `${cita.pais} · para el cliente son las ${hora}${mismoDia ? '' : ` (${textoDia(soloFecha(cita.fechaHoraLocal)).toLowerCase()})`}`;
}

/** ¿Ya pasó la hora de la cita? (RN-24: antes de la reunión no se llena el cuestionario). Las fechas llevan su desplazamiento. */
export const yaOcurrio = (cita: CitaGestion, ahora: Date = new Date()) => new Date(cita.fechaHoraColombia).getTime() <= ahora.getTime();
