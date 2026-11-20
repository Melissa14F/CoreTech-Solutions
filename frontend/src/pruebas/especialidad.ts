import type { Desarrollador } from '../especialidad/api';

/** Desarrollador de ejemplo para las pruebas de Desarrolladores y Equipos. */
export const dev = (id: string, nombre: string, especialidad: Desarrollador['especialidad'], extra: Partial<Desarrollador> = {}): Desarrollador => ({
  desarrolladorId: id, nombre, correo: `${id}@coretechsolutions.co`, celular: '3001234567', especialidad, activo: true,
  fechaIngreso: '2025-02-03', tieneCuenta: false, equipo: null, rol: null, ...extra,
});
