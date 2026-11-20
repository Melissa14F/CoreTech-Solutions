import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

import { Icono } from './Icono';

export type TipoNotificacion = 'exito' | 'error' | 'info';

interface Notificacion {
  id: number;
  mensaje: string;
  tipo: TipoNotificacion;
}

type Notificar = (mensaje: string, tipo?: TipoNotificacion) => void;

const Contexto = createContext<Notificar>(() => undefined);
const DURACION_MS = 5000;
let siguienteId = 1;

/**
 * Notificaciones flotantes del prototipo (.notificaciones, abajo a la derecha): confirman una acción
 * ("Cambios guardados") y se cierran solas a los 5 segundos. El lector de pantalla las anuncia sin
 * interrumpir (role="status").
 */
export function ProveedorNotificaciones({ children }: { children: ReactNode }) {
  const [lista, setLista] = useState<Notificacion[]>([]);

  const cerrar = useCallback((id: number) => setLista((actual) => actual.filter((n) => n.id !== id)), []);

  const notificar = useCallback<Notificar>((mensaje, tipo = 'exito') => {
    const id = siguienteId++;
    setLista((actual) => [...actual, { id, mensaje, tipo }]);
    window.setTimeout(() => cerrar(id), DURACION_MS);
  }, [cerrar]);

  const valor = useMemo(() => notificar, [notificar]);

  return (
    <Contexto.Provider value={valor}>
      {children}
      <div className="notificaciones" role="status" aria-live="polite">
        {lista.map((n) => (
          <div key={n.id} className={`notificacion notificacion--${n.tipo}`}>
            <Icono nombre={n.tipo === 'exito' ? 'exito' : n.tipo === 'error' ? 'alerta' : 'informacion'} />
            <span className="notificacion__texto">{n.mensaje}</span>
            <button type="button" className="notificacion__cerrar" aria-label="Cerrar mensaje" onClick={() => cerrar(n.id)}>
              <Icono nombre="cerrar" />
            </button>
          </div>
        ))}
      </div>
    </Contexto.Provider>
  );
}

/** Muestra una notificación flotante: notificar('Cambios guardados en "Tienda en línea".'). */
export function useNotificar(): Notificar {
  return useContext(Contexto);
}
