import { diaColombia } from '../administracion/formato';
import { api } from '../api/cliente';

/** Proyectos del administrador (HU-02, HU-17). Contrato: docs/04-api.md, flujos "Crear proyecto" y "Proyectos". */

export type EstadoProyecto = 'ACTIVO' | 'PAUSADO' | 'EN_GARANTIA' | 'FINALIZADO' | 'CANCELADO';
export type EstadoPlan = 'BORRADOR' | 'ENVIADO' | 'AJUSTES_PEDIDOS' | 'APROBADO' | 'VENCIDO' | 'RECHAZADO';
export type RiesgoProyecto = 'ENTREGA_VENCIDA' | 'POCO_AVANCE' | 'SIN_AVANCES' | 'PLAN_SIN_RESPUESTA';
export type FiltroProyectos = 'todas' | 'activo' | 'riesgo' | 'plan' | 'pausado' | 'garantia' | 'finalizado' | 'cancelado';

export interface ProyectoAdmin {
  proyectoId: string;
  codigo: string;
  nombre: string;
  alcance: string;
  citaId: string;
  cliente: { clienteId: string; nombre: string; correo: string };
  servicioId: string;
  servicio: string;
  equipo: { equipoId: string; productOwner: string; proyectosActivos: number; maxProyectos: number };
  fechaInicio: string;
  fechaEstimadaEntrega: string;
  estado: EstadoProyecto;
  estadoPlan: EstadoPlan;
  valorCotizado: number | null;
  planPlazoRespuesta: string | null;
  planVenceEl: string | null;
  avancePorcentaje: number;
  fases: number;
  faseActual: { orden: number; nombre: string } | null;
  motivoEstado: string | null;
  garantiaHasta: string | null;
  finalizadoAt: string | null;
  creadoAt: string;
  actualizadoAt: string;
  ultimoAvance: string | null;
  riesgo: RiesgoProyecto | null;
  enRiesgo: boolean;
}

export interface ListaProyectos {
  proyectos: ProyectoAdmin[];
  porFiltro: Record<FiltroProyectos, number>;
  pagina: number;
  tamano: number;
  totalFiltrados: number;
  totalPaginas: number;
}

export interface EquipoParaProyecto {
  equipoId: string;
  productOwner: string;
  integrantes: number;
  proyectosActivos: number;
  maxProyectos: number;
  cupoLibre: number;
}

export interface CitaAtendida {
  citaId: string;
  codigo: string;
  cliente: string;
  correoCliente: string;
  servicioId: string;
  servicio: string;
  fechaHora: string;
  equipoId: string | null;
  productOwner: string | null;
  /** Código del proyecto si la cita ya se convirtió en uno (RN-04). */
  proyecto: string | null;
}

export interface DatosNuevoProyecto {
  citaId: string;
  nombre: string;
  alcance: string;
  fechaInicio: string;
  fechaEstimadaEntrega: string;
  equipoId: string;
}

export interface EdicionProyecto {
  nombre?: string;
  alcance?: string;
  fechaEstimadaEntrega?: string;
  equipoId?: string;
  motivo: string;
}

export interface ProyectoEditado {
  proyecto: ProyectoAdmin;
  /** Solo con el plan aprobado: la fecha aún no cambia, el cliente debe aceptarla (D-33). */
  cambioFecha: { cambioId: string; estado: 'AUTORIZADO'; nuevaFecha: string } | null;
}

export const proyectosAdmin = {
  lista: (f: { estado?: FiltroProyectos; equipoId?: string; q?: string; pagina?: number } = {}) => {
    const consulta = new URLSearchParams();
    if (f.estado && f.estado !== 'todas') consulta.set('estado', f.estado);
    if (f.equipoId) consulta.set('equipoId', f.equipoId);
    if (f.q?.trim()) consulta.set('q', f.q.trim());
    if (f.pagina) consulta.set('pagina', String(f.pagina));
    const texto = consulta.toString();
    return api.obtener<ListaProyectos>(`/proyectos${texto ? `?${texto}` : ''}`);
  },
  equipos: () => api.obtener<EquipoParaProyecto[]>('/proyectos/equipos'),
  citasAtendidas: () => api.obtener<CitaAtendida[]>('/proyectos/citas-atendidas'),
  crear: (d: DatosNuevoProyecto) =>
    api.crear<{ proyectoId: string; codigo: string; estadoPlan: EstadoPlan; fases: number }>('/proyectos', d),
  editar: (codigo: string, d: EdicionProyecto) => api.modificar<ProyectoEditado>(`/proyectos/${codigo}`, d),
  cambiarEstado: (codigo: string, estado: 'PAUSADO' | 'ACTIVO' | 'CANCELADO', motivo: string, equipoId?: string) =>
    api.modificar<ProyectoAdmin>(`/proyectos/${codigo}/estado`, { estado, motivo, equipoId }),
};

// ------------------------------------------------------------------------------------------ Textos de la lista

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** "2027-01-29" → "29 ene 2027". */
export function fechaLegible(iso: string): string {
  const [anio, mes, dia] = iso.slice(0, 10).split('-').map(Number);
  return `${dia} ${MESES[(mes ?? 1) - 1]} ${anio}`;
}

/** "2027-01-29" → "29 ene" (sin año). */
export function fechaSinAnio(iso: string): string {
  return fechaLegible(iso).split(' ').slice(0, 2).join(' ');
}

const unDia = 86_400_000;

/** Días de calendario de {@code desde} a {@code hasta} (AAAA-MM-DD); negativo si hasta es anterior. */
export function diasEntre(desde: string, hasta: string): number {
  return Math.round((Date.parse(hasta.slice(0, 10)) - Date.parse(desde.slice(0, 10))) / unDia);
}

export const hoyColombia = (ahora = new Date()) => diaColombia(ahora);

const dias = (n: number) => `${n} ${n === 1 ? 'día' : 'días'}`;

export type TonoPlazo = 'normal' | 'aviso' | 'vencido';

export interface TextoFila {
  insignia: { variante: 'activo' | 'vencido' | 'pendiente' | 'plan' | 'pausado' | 'garantia' | 'finalizado' | 'cancelada'; texto: string };
  plazo: { texto: string; tono: TonoPlazo };
  /** El avance de la fila: "60 %" con "Fase 4/5 · Pruebas", o el estado del plan. */
  avance: { titulo: string; detalle: string; porcentaje: number | null };
}

/** Lo que se lee en la fila de un proyecto, con los textos del prototipo (admin/proyectos.html). */
export function textosDeFila(p: ProyectoAdmin, hoy = hoyColombia()): TextoFila {
  const faltan = diasEntre(hoy, p.fechaEstimadaEntrega);
  const faltanTexto = faltan >= 0 ? `Faltan ${dias(faltan)}` : `Vencida hace ${dias(-faltan)}`;
  const planAbierto = p.estadoPlan === 'BORRADOR' || p.estadoPlan === 'ENVIADO' || p.estadoPlan === 'AJUSTES_PEDIDOS';
  const nFases = `${p.fases} ${p.fases === 1 ? 'fase' : 'fases'}`;

  let avance: TextoFila['avance'];
  if (p.estado === 'ACTIVO' && planAbierto) {
    avance = p.estadoPlan === 'ENVIADO'
      ? { titulo: 'Plan enviado', detalle: p.valorCotizado ? `${nFases} · ${pesos(p.valorCotizado)}` : nFases, porcentaje: null }
      : p.estadoPlan === 'AJUSTES_PEDIDOS'
        ? { titulo: 'Ajustes pedidos', detalle: `${nFases} · el equipo los atiende`, porcentaje: null }
        : { titulo: 'Plan en borrador', detalle: `${nFases} · el equipo lo ajusta`, porcentaje: null };
  } else {
    const porcentaje = Math.round(p.avancePorcentaje);
    const fase = p.faseActual ? `Fase ${p.faseActual.orden}/${p.fases} · ${p.faseActual.nombre}` : nFases;
    avance = { titulo: `${porcentaje} %`, detalle: fase, porcentaje };
  }

  if (p.estado === 'PAUSADO') {
    const motivoPlan = p.estadoPlan === 'RECHAZADO' ? ' · el cliente rechazó el plan' : p.estadoPlan === 'VENCIDO' ? ' · el plan venció sin respuesta' : '';
    return { insignia: { variante: 'pausado', texto: 'Pausado' }, plazo: { texto: `Pausado desde el ${fechaSinAnio(p.actualizadoAt)}${motivoPlan}`, tono: 'normal' }, avance };
  }
  if (p.estado === 'EN_GARANTIA') {
    return {
      insignia: { variante: 'garantia', texto: 'En garantía' },
      plazo: { texto: p.garantiaHasta ? `Garantía hasta el ${fechaSinAnio(p.garantiaHasta)}` : 'En garantía', tono: 'normal' }, avance,
    };
  }
  if (p.estado === 'FINALIZADO') {
    return {
      insignia: { variante: 'finalizado', texto: 'Finalizado' },
      plazo: { texto: p.finalizadoAt ? `Entregado el ${fechaSinAnio(p.finalizadoAt)} · garantía terminada` : 'Garantía terminada', tono: 'normal' }, avance,
    };
  }
  if (p.estado === 'CANCELADO') {
    return { insignia: { variante: 'cancelada', texto: 'Cancelado' }, plazo: { texto: p.motivoEstado ?? 'Cancelado', tono: 'normal' }, avance };
  }

  switch (p.riesgo) {
    case 'ENTREGA_VENCIDA':
      return { insignia: { variante: 'vencido', texto: 'Fuera de plazo' }, plazo: { texto: faltanTexto, tono: 'vencido' }, avance };
    case 'POCO_AVANCE':
      return { insignia: { variante: 'vencido', texto: 'Entrega cercana' }, plazo: { texto: `${faltanTexto} y menos del 80 % de avance`, tono: 'aviso' }, avance };
    case 'SIN_AVANCES': {
      const sin = p.ultimoAvance ? diasEntre(p.ultimoAvance, hoy) : null;
      return { insignia: { variante: 'pendiente', texto: 'Sin avances' }, plazo: { texto: sin === null ? 'Sin avances registrados' : `Sin avances hace ${dias(sin)}`, tono: 'aviso' }, avance };
    }
    case 'PLAN_SIN_RESPUESTA':
      return {
        insignia: { variante: 'plan', texto: 'Plan sin respuesta' },
        plazo: { texto: `${p.cliente.nombre.split(' ')[0]} no responde desde el ${p.planPlazoRespuesta ? fechaSinAnio(p.planPlazoRespuesta) : 'plazo'}`, tono: 'aviso' }, avance,
      };
    default:
  }
  if (p.estadoPlan === 'ENVIADO') {
    const hasta = p.planPlazoRespuesta && p.planVenceEl
      ? `${p.cliente.nombre.split(' ')[0]} responde hasta el ${fechaSinAnio(p.planPlazoRespuesta)} · vence el ${fechaSinAnio(p.planVenceEl)}`
      : faltanTexto;
    return { insignia: { variante: 'plan', texto: 'Plan por aprobar' }, plazo: { texto: hasta, tono: 'normal' }, avance };
  }
  const creado = diasEntre(p.creadoAt, hoy) === 0 ? 'Creado hoy · ' : '';
  return { insignia: { variante: 'activo', texto: 'En curso' }, plazo: { texto: `${creado}${faltanTexto}`, tono: 'normal' }, avance };
}

export const pesos = (valor: number) => `$${valor.toLocaleString('es-CO')} COP`;
