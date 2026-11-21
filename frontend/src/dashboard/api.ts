/** Dashboard del administrador (HU-23, RF-49): GET /dashboard?periodo=semana|mes|trimestre. */
import { api } from '../api/cliente';

export type Periodo = 'semana' | 'mes' | 'trimestre';
export type NivelAlerta = 'CRITICA' | 'ADVERTENCIA' | 'INFO';

export const PERIODOS: { valor: Periodo; texto: string }[] = [
  { valor: 'semana', texto: 'Semana' },
  { valor: 'mes', texto: 'Mes' },
  { valor: 'trimestre', texto: 'Trimestre' },
];

export interface Alerta {
  id: string;
  nivel: NivelAlerta;
  titulo: string;
  texto: string;
  /** Ruta de la SPA donde se resuelve. */
  enlace: string;
  accion: string;
}

export interface Conteos {
  citasSemana: number;
  citasSinAsignar: number;
  planesPorAprobar: number;
  cambiosPorAutorizar: number;
  fasesPorAprobar: number;
  fasesVencidas: number;
  proyectosEnGarantia: number;
  garantiasCumplidas: number;
  solicitudesPendientes: number;
  proyectosActivos: number;
  capacidad: number;
}

export interface Tablero {
  generadoAt: string;
  corte: string;
  periodo: Periodo;
  etiquetaPeriodo: string;
  /** Sin citas ni proyectos en el periodo: la pantalla dice «Sin datos en este periodo». */
  sinDatos: boolean;
  indicadores: {
    citas: { etiqueta: string; total: number; anterior: number; variacion: number | null; etiquetaAnterior: string };
    conversion: { proyectos: number; citas: number; porcentaje: number | null };
    proyectos: { activos: number; capacidad: number; porcentaje: number; planesPorAprobar: number };
    entregas: { aTiempo: number; total: number; porcentaje: number | null; anio: number };
  };
  conteos: Conteos;
  alertas: Alerta[];
  citasPorMes: { mes: string; etiqueta: string; citas: number; parcial: boolean }[];
  carga: { id: string; productOwner: string; activos: number; maximo: number; libres: number }[];
  proyectosPorFase: { fase: string; proyectos: number }[];
  servicios: { servicio: string; citas: number }[];
  citasPorEstado: { pendientes: number; confirmadas: number; atendidas: number; canceladas: number; total: number; promedioDiasParaConfirmar: number | null };
  actividad: { fecha: string; usuario: string; categoria: string; accion: string; entidad: string; entidadId: string | null }[];
}

export const dashboardApi = {
  tablero: (periodo: Periodo) => api.obtener<Tablero>(`/dashboard?periodo=${periodo}`),
};
