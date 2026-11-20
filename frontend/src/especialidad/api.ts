import { api } from '../api/cliente';

/** Peticiones de Desarrolladores (HU-25) y Equipos (HU-26). Contrato: docs/04-api.md, flujo "Especialidad". */

export type Especialidad = 'FRONTEND' | 'BACKEND' | 'FULL_STACK' | 'MOVIL' | 'QA' | 'UX_UI';
export type RolEquipo = Especialidad | 'PRODUCT_OWNER';

export const ESPECIALIDADES: { valor: Especialidad; texto: string }[] = [
  { valor: 'FRONTEND', texto: 'Frontend' },
  { valor: 'BACKEND', texto: 'Backend' },
  { valor: 'FULL_STACK', texto: 'Full stack' },
  { valor: 'MOVIL', texto: 'Móvil' },
  { valor: 'QA', texto: 'QA' },
  { valor: 'UX_UI', texto: 'UX/UI' },
];

export const textoRol = (rol: RolEquipo): string =>
  rol === 'PRODUCT_OWNER' ? 'Product Owner' : ESPECIALIDADES.find((e) => e.valor === rol)?.texto ?? rol;

/** Roles de integrante que permite cada especialidad (RN-26); el primero es el que se propone. */
export const ROLES_POSIBLES: Record<Especialidad, Especialidad[]> = {
  FRONTEND: ['FRONTEND'],
  BACKEND: ['BACKEND'],
  FULL_STACK: ['FULL_STACK', 'FRONTEND', 'BACKEND'],
  MOVIL: ['MOVIL'],
  QA: ['QA'],
  UX_UI: ['UX_UI'],
};

export interface Desarrollador {
  desarrolladorId: string;
  nombre: string;
  correo: string;
  celular: string;
  especialidad: Especialidad;
  activo: boolean;
  fechaIngreso: string;
  tieneCuenta: boolean;
  equipo: { equipoId: string; productOwner: string } | null;
  rol: RolEquipo | null;
}

export interface DatosDesarrollador {
  nombre: string;
  celular: string;
  correo: string;
  especialidad: Especialidad | '';
}

export interface Miembro {
  desarrolladorId: string;
  nombre: string;
  correo: string;
  especialidad: Especialidad;
  activo: boolean;
  rol: RolEquipo;
}

export interface Equipo {
  equipoId: string;
  productOwner: Miembro;
  integrantes: Miembro[];
  proyectosActivos: number;
  maxProyectos: number;
}

export interface DatosEquipo {
  productOwnerId: string;
  integrantes: { desarrolladorId: string; rol: RolEquipo }[];
}

export const desarrolladores = {
  listar: () => api.obtener<Desarrollador[]>('/desarrolladores'),
  registrar: (datos: DatosDesarrollador) => api.crear<Desarrollador>('/desarrolladores', datos),
  editar: (id: string, datos: DatosDesarrollador) => api.reemplazar<Desarrollador>(`/desarrolladores/${id}`, datos),
  cambiarActivo: (id: string, activo: boolean) => api.modificar<Desarrollador>(`/desarrolladores/${id}/activo`, { activo }),
};

export const equipos = {
  listar: () => api.obtener<Equipo[]>('/equipos'),
  crear: (datos: DatosEquipo) => api.crear<Equipo>('/equipos', datos),
  editar: (id: string, datos: DatosEquipo) => api.reemplazar<Equipo>(`/equipos/${id}`, datos),
};
