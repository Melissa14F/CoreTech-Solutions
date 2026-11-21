import type { CitaAtendida, EquipoParaProyecto, ListaProyectos, ProyectoAdmin } from '../proyectos/admin';

/** Hoy en estas pruebas: miércoles 11 de noviembre de 2026, 10:00 a. m. en Colombia. */
export const HOY_PRUEBA = new Date('2026-11-11T15:00:00Z');

const BASE: ProyectoAdmin = {
  proyectoId: 'p-24', codigo: 'P-0024', nombre: 'Portal de clientes Peñaranda', alcance: 'Portal con acceso para clientes, descarga de facturas y estado de pedidos.',
  citaId: 'c-40', cliente: { clienteId: 'u-ricardo', nombre: 'Ricardo Peñaranda', correo: 'ricardo@correo.com' },
  servicioId: 's-web', servicio: 'Página web corporativa',
  equipo: { equipoId: 'e-maria', productOwner: 'María Ruiz', proyectosActivos: 2, maxProyectos: 3 },
  fechaInicio: '2026-08-01', fechaEstimadaEntrega: '2026-10-30', estado: 'ACTIVO', estadoPlan: 'APROBADO', valorCotizado: 9000000,
  planPlazoRespuesta: null, planVenceEl: null, avancePorcentaje: 60, fases: 5, faseActual: { orden: 4, nombre: 'Pruebas' },
  motivoEstado: null, garantiaHasta: null, finalizadoAt: null, creadoAt: '2026-08-01T15:00:00Z', actualizadoAt: '2026-10-01T15:00:00Z',
  ultimoAvance: '2026-11-05', riesgo: 'ENTREGA_VENCIDA', enRiesgo: true,
};

export const VENCIDO: ProyectoAdmin = BASE;
export const EN_CURSO: ProyectoAdmin = {
  ...BASE, proyectoId: 'p-27', codigo: 'P-0027', nombre: 'Tienda de repuestos Ortega', cliente: { clienteId: 'u-felipe', nombre: 'Felipe Ortega', correo: 'felipe@correo.com' },
  servicio: 'Desarrollo de tienda en línea', fechaEstimadaEntrega: '2026-12-04', riesgo: null, enRiesgo: false,
};
export const SIN_AVANCES: ProyectoAdmin = {
  ...BASE, proyectoId: 'p-19', codigo: 'P-0019', nombre: 'Inventario Ferretería Ortiz', cliente: { clienteId: 'u-val', nombre: 'Valentina Ortiz', correo: 'val@correo.com' },
  servicio: 'Sistema de inventario', fechaEstimadaEntrega: '2027-01-22', avancePorcentaje: 17, fases: 6, faseActual: { orden: 2, nombre: 'Análisis y diseño' },
  ultimoAvance: '2026-10-27', riesgo: 'SIN_AVANCES', enRiesgo: true,
};
export const PLAN_ENVIADO: ProyectoAdmin = {
  ...BASE, proyectoId: 'p-35', codigo: 'P-0035', nombre: 'Inventario del salón', cliente: { clienteId: 'u-laura', nombre: 'Laura Gómez', correo: 'laura@correo.com' },
  servicio: 'Sistema de inventario', equipo: { equipoId: 'e-andres', productOwner: 'Andrés Pérez', proyectosActivos: 3, maxProyectos: 3 },
  fechaEstimadaEntrega: '2027-02-12', estadoPlan: 'ENVIADO', valorCotizado: 7800000, planPlazoRespuesta: '2026-11-17', planVenceEl: '2026-11-26',
  avancePorcentaje: 0, fases: 6, faseActual: { orden: 1, nombre: 'Toma de requerimientos' }, riesgo: null, enRiesgo: false,
};
export const BORRADOR: ProyectoAdmin = {
  ...BASE, proyectoId: 'p-36', codigo: 'P-0036', nombre: 'Web corporativa Duarte', cliente: { clienteId: 'u-miguel', nombre: 'Miguel Ángel Duarte', correo: 'miguel@correo.com' },
  fechaEstimadaEntrega: '2027-01-29', estadoPlan: 'BORRADOR', valorCotizado: null, avancePorcentaje: 0, creadoAt: '2026-11-11T15:00:00Z', riesgo: null, enRiesgo: false,
};
export const PAUSADO_RECHAZADO: ProyectoAdmin = {
  ...BASE, proyectoId: 'p-29', codigo: 'P-0029', nombre: 'Web Taller Pabón', cliente: { clienteId: 'u-jorge', nombre: 'Jorge Pabón', correo: 'jorge@correo.com' },
  fechaEstimadaEntrega: '2027-03-12', estado: 'PAUSADO', estadoPlan: 'RECHAZADO', avancePorcentaje: 0, motivoEstado: 'El cliente rechazó el plan y la cotización (RN-29)',
  actualizadoAt: '2026-11-02T15:00:00Z', riesgo: null, enRiesgo: false,
};
export const EN_GARANTIA: ProyectoAdmin = {
  ...BASE, proyectoId: 'p-17', codigo: 'P-0017', nombre: 'App de turnos Rojas', estado: 'EN_GARANTIA', avancePorcentaje: 100, garantiaHasta: '2026-12-03', riesgo: null, enRiesgo: false,
};

export const EQUIPOS: EquipoParaProyecto[] = [
  { equipoId: 'e-camila', productOwner: 'Camila Jaimes', integrantes: 4, proyectosActivos: 0, maxProyectos: 3, cupoLibre: 3 },
  { equipoId: 'e-maria', productOwner: 'María Ruiz', integrantes: 4, proyectosActivos: 2, maxProyectos: 3, cupoLibre: 1 },
  { equipoId: 'e-andres', productOwner: 'Andrés Pérez', integrantes: 6, proyectosActivos: 3, maxProyectos: 3, cupoLibre: 0 },
];

export const LISTA: ListaProyectos = {
  proyectos: [VENCIDO, SIN_AVANCES, PLAN_ENVIADO, EN_CURSO, BORRADOR, PAUSADO_RECHAZADO, EN_GARANTIA],
  porFiltro: { todas: 7, activo: 5, riesgo: 2, plan: 1, pausado: 1, garantia: 1, finalizado: 0, cancelado: 0 },
  pagina: 0, tamano: 20, totalFiltrados: 7, totalPaginas: 1,
};

export const CITA_SIN_PROYECTO: CitaAtendida = {
  citaId: 'c-57', codigo: 'C-0057', cliente: 'Miguel Ángel Duarte', correoCliente: 'miguel@correo.com', servicioId: 's-web', servicio: 'Página web corporativa',
  fechaHora: '2026-11-05T15:00:00Z', equipoId: 'e-camila', productOwner: 'Camila Jaimes', proyecto: null,
};
export const CITA_CON_PROYECTO: CitaAtendida = { ...CITA_SIN_PROYECTO, citaId: 'c-49', codigo: 'C-0049', cliente: 'Laura Gómez', proyecto: 'P-0035' };

export const CUESTIONARIO = {
  cuestionarioId: 'q-1', citaId: 'c-57', resumen: 'Miguel quiere renovar la web de Duarte Asesores para presentar sus servicios y recibir solicitudes.',
  descripcionCliente: 'Socio fundador de una firma de asesoría tributaria.', presupuesto: 'DE_5_A_15M', fechaDeseada: '2027-02-01', idioma: 'ESPANOL', urgencia: 'MEDIA',
  registradoAt: '2026-11-05T17:00:00Z',
};

export const PLANTILLA = {
  fases: [
    { orden: 1, nombre: 'Requerimientos y contenido', queEs: '', apruebaCliente: true, pasos: [{ orden: 1, titulo: 'Reunión', explicacion: '' }] },
    { orden: 2, nombre: 'Diseño', queEs: '', apruebaCliente: true, pasos: [{ orden: 1, titulo: 'Boceto', explicacion: '' }] },
    { orden: 3, nombre: 'Desarrollo', queEs: '', apruebaCliente: false, pasos: [{ orden: 1, titulo: 'Programar', explicacion: '' }] },
  ],
};
