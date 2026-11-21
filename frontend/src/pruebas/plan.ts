import type { PlanProyecto } from '../proyectos/plan';

/** Planes de ejemplo (P-0035 de Laura Gómez, Product Owner Andrés Pérez) como los devuelve GET /proyectos/{codigo}/plan. */
export const FASES_PLAN = [
  {
    orden: 1, nombre: 'Toma de requerimientos', queEs: 'Conocemos tus bodegas y tus productos.', apruebaCliente: true,
    pasos: [
      { orden: 1, titulo: 'Visita al salón', explicacion: 'Vemos cómo registras hoy los productos.' },
      { orden: 2, titulo: 'Lista de funciones', explicacion: null },
    ],
  },
  {
    orden: 2, nombre: 'Desarrollo', queEs: 'Construimos el sistema.', apruebaCliente: false,
    pasos: [{ orden: 1, titulo: 'Productos y bodegas', explicacion: 'Registro con código y existencias.' }],
  },
  {
    orden: 3, nombre: 'Pruebas', queEs: 'Revisamos que los saldos sean correctos.', apruebaCliente: true,
    pasos: [{ orden: 1, titulo: 'Prueba contigo', explicacion: 'Tu equipo lo usa con datos de ejemplo.' }],
  },
];

const BASE: PlanProyecto = {
  codigo: 'P-0035', nombre: 'Inventario del salón', estado: 'ACTIVO', estadoPlan: 'ENVIADO', cliente: 'Laura Gómez',
  productOwner: 'Andrés Pérez', servicio: 'Sistema de inventario', citaCodigo: 'C-0049', citaFecha: '2026-11-05T15:00:00Z',
  fechaInicio: '2026-11-16', fechaEstimadaEntrega: '2027-02-12', valorCotizado: 7_800_000,
  condicionesCotizacion: 'Incluye 2 rondas de ajustes en cada fase y 30 días de garantía. No incluye el hosting.',
  creadoAt: '2026-11-11T15:00:00Z', enviadoAt: '2026-11-11T16:00:00Z', aprobadoAt: null, plazoRespuesta: '2026-11-17',
  venceEl: '2026-11-26', fases: FASES_PLAN, porcentajePorFase: 33.3, ajustesPedidos: null, rechazo: null, editable: false,
};

export const PLAN_ENVIADO: PlanProyecto = BASE;

export const PLAN_BORRADOR: PlanProyecto = {
  ...BASE, estadoPlan: 'BORRADOR', valorCotizado: null, condicionesCotizacion: null, enviadoAt: null, plazoRespuesta: null,
  venceEl: null, fechaEstimadaEntrega: '2027-01-29', editable: true,
};

export const PLAN_AJUSTES: PlanProyecto = {
  ...BASE, estadoPlan: 'AJUSTES_PEDIDOS', editable: true,
  ajustesPedidos: { texto: 'Falta una fase de capacitación del personal.', fecha: '2026-11-12T15:00:00Z' },
};

export const PLAN_APROBADO: PlanProyecto = { ...BASE, estadoPlan: 'APROBADO', aprobadoAt: '2026-11-13T15:00:00Z' };

export const PLAN_RECHAZADO: PlanProyecto = {
  ...BASE, estadoPlan: 'RECHAZADO', estado: 'PAUSADO',
  rechazo: { texto: 'El valor supera nuestro presupuesto de este año.', fecha: '2026-11-13T15:00:00Z' },
};
