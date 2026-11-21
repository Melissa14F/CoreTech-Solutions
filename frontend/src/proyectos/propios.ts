import { api } from '../api/cliente';
import { fechaConDia, nombrePila, type EstadoPlanProyecto } from './plan';

/** «Mis proyectos» del cliente y del Product Owner (GET /proyectos/propios). */
export type PorHacer = 'PREPARAR_PLAN' | 'AJUSTAR_PLAN' | 'REVISAR_PROPUESTA';

export interface ProyectoPropio {
  codigo: string;
  nombre: string;
  servicio: string;
  estado: string;
  estadoPlan: EstadoPlanProyecto;
  cliente: string;
  productOwner: string;
  fechaEstimadaEntrega: string;
  valorCotizado: number | null;
  avancePorcentaje: number;
  fases: number;
  faseActualOrden: number | null;
  faseActualNombre: string | null;
  plazoRespuesta: string | null;
  venceEl: string | null;
  porHacer: PorHacer | null;
}

export const proyectosPropios = {
  listar: () => api.obtener<ProyectoPropio[]>('/proyectos/propios'),
};

export type Vista = 'cliente' | 'equipo';

export interface TextoProyecto {
  /** Lo que se dice abajo a la izquierda, en palabras sencillas. */
  nota: string;
  /** «accion» si espera algo de quien mira; «plan» si es informativo. */
  tono: 'accion' | 'plan' | 'normal';
  /** Botón principal; nulo si todavía no hay nada que abrir. */
  boton: { texto: string; a: string; principal: boolean } | null;
}

/** Qué decir y qué ofrecer de cada proyecto, sin nombres técnicos de estado. */
export function textoDeProyecto(p: ProyectoPropio, vista: Vista): TextoProyecto {
  const po = nombrePila(p.productOwner);
  const cliente = nombrePila(p.cliente);
  const plazo = p.plazoRespuesta ? fechaConDia(p.plazoRespuesta) : '';
  const rutaPlan = vista === 'cliente' ? `/cliente/proyectos/${p.codigo}/propuesta` : `/equipo/proyectos/${p.codigo}/plan`;
  // P18: con el plan aprobado hay un proyecto que ver (el cliente) o en el que registrar trabajo (el equipo)
  const rutaProyecto = vista === 'cliente' ? `/cliente/proyectos/${p.codigo}` : `/equipo/proyectos/${p.codigo}/registrar`;

  if (p.estado !== 'ACTIVO') {
    const sinAprobar = p.estadoPlan === 'RECHAZADO' || p.estadoPlan === 'VENCIDO';
    const nota = p.estado === 'PAUSADO'
      ? (sinAprobar ? 'La propuesta no se aprobó y el proyecto está en pausa.' : 'El proyecto está en pausa.')
      : p.estado === 'CANCELADO' ? 'El proyecto fue cancelado.' : p.estado === 'FINALIZADO' ? 'El proyecto terminó.' : 'El proyecto está en garantía.';
    const boton = sinAprobar ? { texto: 'Ver propuesta', a: rutaPlan, principal: false }
      : p.estadoPlan === 'APROBADO' ? { texto: 'Ver proyecto', a: rutaProyecto, principal: false } : null;
    return { nota, tono: 'normal', boton };
  }

  switch (p.estadoPlan) {
    case 'BORRADOR':
      return vista === 'equipo'
        ? { nota: `Falta preparar el plan y la cotización para ${cliente}.`, tono: 'accion', boton: { texto: 'Preparar plan', a: rutaPlan, principal: true } }
        : { nota: `${po} prepara tu plan de trabajo y tu cotización.`, tono: 'plan', boton: null };
    case 'AJUSTES_PEDIDOS':
      return vista === 'equipo'
        ? { nota: `${cliente} pidió ajustes al plan: ajústalo y reenvíalo.`, tono: 'accion', boton: { texto: 'Ajustar plan', a: rutaPlan, principal: true } }
        : { nota: `${po} ajusta el plan con tus comentarios y te lo enviará de nuevo.`, tono: 'plan', boton: null };
    case 'ENVIADO':
      return vista === 'equipo'
        ? { nota: `Plan enviado: ${cliente} responde hasta el ${plazo}.`, tono: 'plan', boton: { texto: 'Ver plan', a: rutaPlan, principal: false } }
        : { nota: `Plan y cotización listos: apruébalos o pide ajustes antes del ${plazo}.`, tono: 'accion', boton: { texto: 'Revisar propuesta', a: rutaPlan, principal: true } };
    case 'APROBADO':
      return {
        nota: 'Plan aprobado: el trabajo está en marcha.', tono: 'normal',
        boton: vista === 'cliente' ? { texto: 'Ver mi proyecto', a: rutaProyecto, principal: true } : { texto: 'Registrar trabajo', a: rutaProyecto, principal: true },
      };
    default:
      return { nota: 'El plan no está disponible.', tono: 'normal', boton: null };
  }
}

/** Primero lo que espera a quien mira; el resto conserva el orden del servidor. */
export const conPendientesPrimero = (lista: ProyectoPropio[]): ProyectoPropio[] =>
  [...lista.filter((p) => p.porHacer), ...lista.filter((p) => !p.porHacer)];
