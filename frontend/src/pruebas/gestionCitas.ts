import type { CitaGestion, EquipoParaAsignar, ListaCitas } from '../agenda/gestion';

/** Hoy en estas pruebas: martes 4 de marzo de 2031, 10:00 a. m. en Colombia (las mismas del agendamiento). */
export { AHORA_PRUEBA } from './agenda';

const EQUIPO_ANDRES = { equipoId: 'e-andres', productOwner: 'Andrés Pérez', emailProductOwner: 'andres.perez@coretechsolutions.co', integrantes: 6, proyectosActivos: 3, maxProyectos: 3 };
const EQUIPO_MARIA = { equipoId: 'e-maria', productOwner: 'María Ruiz', emailProductOwner: 'maria.ruiz@coretechsolutions.co', integrantes: 4, proyectosActivos: 1, maxProyectos: 3 };

const BASE: CitaGestion = {
  citaId: 'c-61', codigo: 'C-0061', servicioId: 's-tienda', servicio: 'Desarrollo de tienda en línea', estado: 'PENDIENTE',
  modalidad: 'VIRTUAL', fechaHoraLocal: '2031-03-11T09:00:00-05:00', fechaHoraColombia: '2031-03-11T09:00:00-05:00',
  pais: 'Colombia', zonaHoraria: 'America/Bogota', descripcionNecesidad: 'Quiero vender ropa por internet.',
  enlaceReunion: null, notaCliente: null, notaAsignacion: null, motivoCancelacion: null,
  cliente: { nombre: 'Laura Gómez', email: 'laura.gomez@correo.com' }, equipo: null, tieneCuestionario: false,
  confirmadaAt: null, atendidaAt: null, canceladaAt: null, canceladaPor: null,
};

export const SIN_EQUIPO: CitaGestion = BASE;
export const CON_EQUIPO: CitaGestion = {
  ...BASE, citaId: 'c-60', codigo: 'C-0060', servicio: 'Página web corporativa', modalidad: 'PRESENCIAL',
  fechaHoraLocal: '2031-03-12T14:00:00-05:00', fechaHoraColombia: '2031-03-12T14:00:00-05:00',
  cliente: { nombre: 'Andrea Villamizar', email: 'andrea.villamizar@correo.com' }, equipo: EQUIPO_ANDRES,
};
export const DESDE_MEXICO: CitaGestion = {
  ...BASE, citaId: 'c-62', codigo: 'C-0062', servicio: 'Integración de pagos', fechaHoraLocal: '2031-03-13T08:00:00-06:00',
  fechaHoraColombia: '2031-03-13T09:00:00-05:00', pais: 'México', zonaHoraria: 'America/Mexico_City',
  cliente: { nombre: 'Natalia Suárez', email: 'natalia.suarez@correo.com' }, equipo: EQUIPO_ANDRES, notaAsignacion: 'Natalia ya es cliente.',
};
export const CONFIRMADA_FUTURA: CitaGestion = {
  ...BASE, citaId: 'c-59', codigo: 'C-0059', servicio: 'Integración de pagos', estado: 'CONFIRMADA',
  fechaHoraLocal: '2031-03-14T10:00:00-05:00', fechaHoraColombia: '2031-03-14T10:00:00-05:00', equipo: EQUIPO_ANDRES,
  enlaceReunion: 'https://meet.google.com/fyo-pqrs-tuv', notaCliente: 'Trae ejemplos que te gusten.',
  cliente: { nombre: 'Felipe Ortega', email: 'felipe.ortega@correo.com' },
};
export const CONFIRMADA_PASADA: CitaGestion = {
  ...BASE, citaId: 'c-58', codigo: 'C-0058', servicio: 'Sistema de inventario', estado: 'CONFIRMADA', modalidad: 'PRESENCIAL',
  fechaHoraLocal: '2031-03-03T09:00:00-05:00', fechaHoraColombia: '2031-03-03T09:00:00-05:00', equipo: EQUIPO_ANDRES,
  cliente: { nombre: 'Jorge Pabón', email: 'jorge.pabon@correo.com' },
};
export const ATENDIDA: CitaGestion = {
  ...BASE, citaId: 'c-52', codigo: 'C-0052', servicio: 'Aplicación móvil', estado: 'ATENDIDA', tieneCuestionario: true,
  fechaHoraLocal: '2031-02-25T10:00:00-05:00', fechaHoraColombia: '2031-02-25T10:00:00-05:00', equipo: EQUIPO_MARIA,
};
export const CANCELADA: CitaGestion = {
  ...BASE, citaId: 'c-55', codigo: 'C-0055', servicio: 'Desarrollo de tienda en línea', estado: 'CANCELADA',
  fechaHoraLocal: '2031-03-05T14:00:00-05:00', fechaHoraColombia: '2031-03-05T14:00:00-05:00',
  motivoCancelacion: 'El cliente pidió cambiar la fecha.', canceladaPor: 'CLIENTE', cliente: { nombre: 'Sofía Navarro', email: 'sofia.navarro@correo.com' },
};

export const LISTA_ADMIN: ListaCitas = {
  citas: [SIN_EQUIPO, CON_EQUIPO, DESDE_MEXICO, CONFIRMADA_FUTURA, ATENDIDA, CANCELADA],
  porEstado: { PENDIENTE: 3, CONFIRMADA: 1, ATENDIDA: 1, CANCELADA: 1 },
  sinEquipo: 1,
  total: 6, pagina: 0, tamano: 20, totalFiltradas: 6, totalPaginas: 1,
};

export const EQUIPOS: EquipoParaAsignar[] = [
  { equipoId: 'e-maria', productOwner: 'María Ruiz', integrantes: 4, proyectosActivos: 1, maxProyectos: 3, citasProximas: 0 },
  { equipoId: 'e-andres', productOwner: 'Andrés Pérez', integrantes: 6, proyectosActivos: 3, maxProyectos: 3, citasProximas: 4 },
];
