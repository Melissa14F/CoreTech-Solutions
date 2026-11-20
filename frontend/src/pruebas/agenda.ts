import type { CitaPropia, EstadoFranja, Franja } from '../agenda/api';

/** Hoy en las pruebas del agendamiento: martes 4 de marzo de 2031, 10:00 a. m. en Colombia (15:00 UTC). */
export const AHORA_PRUEBA = new Date('2031-03-04T15:00:00Z');

/** Franja en hora de Colombia (UTC−5). */
export const franjaColombia = (fecha: string, hora: string, estado: EstadoFranja = 'LIBRE', tuya = false): Franja => ({
  fecha, hora, fechaHora: `${fecha}T${hora}:00-05:00`, horaColombia: `${fecha}T${hora}:00-05:00`, estado, tuya,
});

/** Franja de una cliente en España (UTC+1 en marzo): las 9:00 de Colombia son las 15:00 allá. */
export const franjaMadrid = (fecha: string, hora: string, horaCol: string, estado: EstadoFranja = 'LIBRE'): Franja => ({
  fecha, hora, fechaHora: `${fecha}T${hora}:00+01:00`, horaColombia: `${fecha}T${horaCol}:00-05:00`, estado, tuya: false,
});

/** Marzo de 2031 para Colombia: jueves 6 con tres horas libres, miércoles 5 sin cupos, viernes 7 con una hora, lunes 10 festivo. */
export const MARZO_COLOMBIA: Franja[] = [
  franjaColombia('2031-03-05', '09:00', 'OCUPADO'),
  franjaColombia('2031-03-05', '10:00', 'OCUPADO'),
  franjaColombia('2031-03-06', '09:00'),
  franjaColombia('2031-03-06', '10:00', 'OCUPADO'),
  franjaColombia('2031-03-06', '11:00'),
  franjaColombia('2031-03-06', '14:00'),
  franjaColombia('2031-03-06', '15:00', 'OCUPADO', true),
  franjaColombia('2031-03-07', '08:00'),
  franjaColombia('2031-03-10', '08:00', 'NO_HABIL'),
];

export const MARZO_MADRID: Franja[] = [
  franjaMadrid('2031-03-06', '15:00', '09:00'),
  franjaMadrid('2031-03-06', '17:00', '11:00'),
];

export const CITA_PENDIENTE: CitaPropia = {
  citaId: 'c-61', codigo: 'C-0061', servicioId: 's-tienda', servicio: 'Desarrollo de tienda en línea', estado: 'PENDIENTE',
  modalidad: 'VIRTUAL', fechaHoraLocal: '2031-03-11T09:00:00-05:00', fechaHoraColombia: '2031-03-11T09:00:00-05:00',
  pais: 'Colombia', zonaHoraria: 'America/Bogota', descripcionNecesidad: 'Quiero vender ropa por internet.',
  enlaceReunion: null, motivoCancelacion: null, puedeCancelar: true,
};
export const CITA_CONFIRMADA: CitaPropia = {
  ...CITA_PENDIENTE, citaId: 'c-62', codigo: 'C-0062', estado: 'CONFIRMADA', fechaHoraLocal: '2031-03-12T10:00:00-05:00',
  fechaHoraColombia: '2031-03-12T10:00:00-05:00', enlaceReunion: 'https://meet.google.com/abc-defg-hij', puedeCancelar: false,
};
export const CITA_ATENDIDA: CitaPropia = {
  ...CITA_PENDIENTE, citaId: 'c-52', codigo: 'C-0052', servicio: 'Aplicación móvil', estado: 'ATENDIDA',
  modalidad: 'PRESENCIAL', fechaHoraLocal: '2031-02-25T10:00:00-05:00', fechaHoraColombia: '2031-02-25T10:00:00-05:00', puedeCancelar: false,
};
export const CITA_EN_MADRID: CitaPropia = {
  ...CITA_PENDIENTE, citaId: 'c-70', codigo: 'C-0070', fechaHoraLocal: '2031-03-13T15:00:00+01:00',
  fechaHoraColombia: '2031-03-13T09:00:00-05:00', pais: 'España', zonaHoraria: 'Europe/Madrid',
};
