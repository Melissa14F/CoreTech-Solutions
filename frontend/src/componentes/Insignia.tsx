import type { ReactNode } from 'react';

/** Variantes de .insignia del prototipo (estados de citas, proyectos, fases y usuarios). */
export type VarianteInsignia =
  | 'neutra' | 'pendiente' | 'confirmada' | 'atendida' | 'finalizado' | 'cancelada' | 'activo'
  | 'programada' | 'sin-asignar' | 'vencido' | 'pausado' | 'garantia' | 'plan' | 'borrador' | 'aprueba'
  | 'oscuro' | 'cliente' | 'especialidad' | 'urgencia-alta' | 'urgencia-media' | 'urgencia-baja';

export function Insignia({ variante = 'neutra', children }: { variante?: VarianteInsignia; children: ReactNode }) {
  return <span className={`insignia insignia--${variante}`}>{children}</span>;
}
