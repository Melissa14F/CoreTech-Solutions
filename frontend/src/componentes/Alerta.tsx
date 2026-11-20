import type { ReactNode } from 'react';

import { Icono } from './Icono';

export type TipoAlerta = 'info' | 'exito' | 'advertencia' | 'error';

interface PropiedadesAlerta {
  tipo?: TipoAlerta;
  titulo?: ReactNode;
  children?: ReactNode;
}

/**
 * Alerta del prototipo (.alerta). Los errores y advertencias se anuncian de inmediato (role="alert");
 * la información y el éxito, sin interrumpir (role="status").
 */
export function Alerta({ tipo = 'info', titulo, children }: PropiedadesAlerta) {
  const urgente = tipo === 'error' || tipo === 'advertencia';
  return (
    <div className={`alerta alerta--${tipo}`} role={urgente ? 'alert' : 'status'}>
      <Icono nombre={tipo === 'exito' ? 'exito' : 'alerta'} />
      <div>
        {titulo && <strong>{titulo}</strong>}
        {children && <p>{children}</p>}
      </div>
    </div>
  );
}
