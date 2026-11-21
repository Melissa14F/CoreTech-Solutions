import type { DetalleProyecto, FaseDetalle, PasoDetalle } from '../seguimiento/api';

/** Detalle de proyecto como lo devuelve GET /proyectos/{codigo} (HU-04): plan aprobado, fase 2 en curso con un paso hecho. */
export const paso = (id: string, orden: number, titulo: string, extra: Partial<PasoDetalle> = {}): PasoDetalle => ({
  id, orden, tipo: 'PASO', titulo, explicacion: `Explicación de ${titulo}.`, origen: null, estado: 'PENDIENTE',
  nota: null, hechoAt: null, adjuntos: [], ...extra,
});

export const FASE_1: FaseDetalle = {
  id: 'f-1', orden: 1, nombre: 'Toma de requerimientos', queEs: 'Entendemos tu negocio.', apruebaCliente: true, estado: 'APROBADA',
  porcentaje: 50, iniciadaAt: '2026-09-29T15:00:00Z', entregadaAt: '2026-10-05T15:00:00Z', aprobadaAt: '2026-10-06T15:00:00Z',
  rondasAjusteUsadas: 1, aprobacionAutomatica: false, pasosHechos: 1, pasosTotal: 1,
  pasos: [paso('s-1', 1, 'Reunión inicial', { estado: 'HECHO', nota: 'Hablamos de los accesorios que vendes.', hechoAt: '2026-09-30T15:00:00Z' })],
};

export const FASE_2: FaseDetalle = {
  id: 'f-2', orden: 2, nombre: 'Desarrollo', queEs: 'Construimos tu proyecto.', apruebaCliente: false, estado: 'EN_CURSO',
  porcentaje: 50, iniciadaAt: '2026-10-24T15:00:00Z', entregadaAt: null, aprobadaAt: null, rondasAjusteUsadas: 0,
  aprobacionAutomatica: false, pasosHechos: 1, pasosTotal: 3,
  pasos: [
    paso('s-2', 1, 'Catálogo de productos', {
      estado: 'HECHO', nota: 'El catálogo ya muestra los productos con sus filtros.', hechoAt: '2026-11-03T15:00:00Z',
      adjuntos: [{ id: 'a-1', titulo: 'Página de producto', tipoMime: 'image/png', tamanoBytes: 2048, url: '/api/v1/adjuntos/a-1' }],
    }),
    paso('s-3', 2, 'Pedidos y correos de confirmación'),
    paso('s-4', 3, 'Pago contra entrega'),
  ],
};

export const DETALLE: DetalleProyecto = {
  codigo: 'P-0031', nombre: 'Tienda en línea Laura', servicio: 'Desarrollo de tienda en línea', citaCodigo: 'C-0045',
  estado: 'ACTIVO', estadoPlan: 'APROBADO', avancePorcentaje: 50, fechaInicio: '2026-09-29', fechaEstimadaEntrega: '2026-12-18',
  faseActual: { orden: 2, nombre: 'Desarrollo' }, pasosHechos: 2, ultimoPaso: { fecha: '2026-11-03T15:00:00Z', titulo: 'Catálogo de productos' },
  cliente: 'Laura Gómez', clienteCorreo: 'laura.gomez@correo.com', productOwner: 'Andrés Pérez',
  productOwnerCorreo: 'andres.perez@coretechsolutions.co',
  equipo: [{ nombre: 'Valeria Rincón', rol: 'Diseño de pantallas' }, { nombre: 'Camilo Suárez', rol: 'Pruebas' }],
  puedeRegistrar: false, fases: [FASE_1, FASE_2],
};

/** El mismo proyecto visto por el Product Owner del equipo. */
export const DETALLE_DEL_EQUIPO: DetalleProyecto = { ...DETALLE, puedeRegistrar: true };
