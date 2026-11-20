import type { CSSProperties, ReactNode } from 'react';

import { Icono } from '../componentes/Icono';
import type { ServicioPublico } from '../catalogo/api';
import type { Franja, Modalidad } from './api';
import { COLOMBIA, PAIS_NEGOCIO } from './paises';
import { textoDia, textoHora, textoHoraColombia } from './fechas';

/** Lo que el cliente va eligiendo en los tres pasos. Vive en la página, así volver atrás no pierde nada. */
export interface Borrador {
  servicio: ServicioPublico | null;
  pais: string;
  zona: string;
  /** Día elegido, en la zona del cliente (AAAA-MM-DD). */
  fecha: string | null;
  franja: Franja | null;
  modalidad: Modalidad | null;
  descripcion: string;
}

export const BORRADOR_INICIAL: Borrador = {
  servicio: null, pais: PAIS_NEGOCIO, zona: COLOMBIA, fecha: null, franja: null, modalidad: null, descripcion: '',
};

export type CampoPaso2 = 'fecha' | 'hora' | 'modalidad' | 'descripcion';
export type ErroresPaso2 = Partial<Record<CampoPaso2, string>>;

export const TEXTO_MODALIDAD: Record<Modalidad, string> = { PRESENCIAL: 'Presencial', VIRTUAL: 'Virtual' };
export const DETALLE_MODALIDAD: Record<Modalidad, string> = {
  PRESENCIAL: 'Calle 10 # 4-25, oficina 302, Cúcuta. Llega 10 minutos antes.',
  VIRTUAL: 'Te enviaremos el enlace de la videollamada cuando tu cita sea confirmada.',
};

/** Lo que responde la API cuando ocultaron el servicio (HU-01); cualquier otro 404 no significa eso. */
export const SERVICIO_OCULTO = 'Este servicio ya no está disponible';

export const DESCRIPCION_MINIMA = 20;
export const DESCRIPCION_MAXIMA = 500;

/** Resumen lateral de los pasos 1 y 2 (se completa a medida que el cliente elige). */
export function ResumenCita({ borrador, paso }: { borrador: Borrador; paso: 1 | 2 }) {
  const { servicio, franja, fecha, modalidad, descripcion, zona } = borrador;
  const necesidad = descripcion.trim();
  const fueraDeColombia = zona !== COLOMBIA;

  const filaFecha: ReactNode = fecha && franja
    ? (
      <>
        {textoDia(fecha)} · {textoHora(franja.fechaHora)}{fueraDeColombia && ', tu hora'}
        {fueraDeColombia && (
          <small className="resumen-cita__extra">En Colombia: {textoHoraColombia(franja.fechaHora, franja.horaColombia, true)}</small>
        )}
      </>
    )
    : fecha ? `${textoDia(fecha)} · falta la hora` : null;

  return (
    <aside className="tarjeta resumen-cita" style={{ '--retraso': '120ms' } as CSSProperties} aria-labelledby="titulo-resumen">
      <h2 className="tarjeta__titulo" id="titulo-resumen">Resumen de tu cita</h2>
      <dl className="resumen-cita__lista">
        <Fila dato="Servicio" valor={servicio?.nombre ?? null} vacio="Sin elegir" />
        {paso === 1 ? (
          <>
            <Fila dato="Fecha y hora" valor={null} vacio="En el paso 2" />
            <Fila dato="Modalidad" valor={null} vacio="En el paso 2" />
          </>
        ) : (
          <>
            <Fila dato="Fecha y hora" valor={filaFecha} vacio="Sin elegir" completa={Boolean(fecha && franja)} />
            <Fila dato="Modalidad" valor={modalidad ? TEXTO_MODALIDAD[modalidad] : null} vacio="Sin elegir" />
            <Fila dato="Tu necesidad" valor={necesidad || null} vacio="Sin escribir" recortar titulo={necesidad} />
          </>
        )}
        <div className="resumen-cita__fila"><dt className="resumen-cita__dato">Duración</dt><dd className="resumen-cita__valor">1 hora</dd></div>
      </dl>
      <ul className="resumen-cita__nota">
        {paso === 1 ? (
          <>
            <li><Icono nombre="reloj" />Lunes a viernes, de 8:00 a. m. a 12:00 m. y de 2:00 a 6:00 p. m. (hora de Colombia).</li>
            <li><Icono nombre="agendar" />Con mínimo 24 horas de anticipación.</li>
            <li><Icono nombre="correo" />Recibirás la confirmación en tu correo.</li>
          </>
        ) : (
          <li><Icono nombre="persona" />Te asignaremos un equipo; su Product Owner confirmará tu cita.</li>
        )}
      </ul>
    </aside>
  );
}

function Fila({ dato, valor, vacio, completa, recortar, titulo }:
  { dato: string; valor: ReactNode; vacio: string; completa?: boolean; recortar?: boolean; titulo?: string }) {
  const pendiente = valor === null || completa === false;
  return (
    <div className="resumen-cita__fila">
      <dt className="resumen-cita__dato">{dato}</dt>
      <dd
        className={['resumen-cita__valor', pendiente && 'resumen-cita__valor--pendiente'].filter(Boolean).join(' ')}
        data-resumen={recortar ? 'descripcion' : undefined}
        title={titulo || undefined}
        aria-live="polite"
      >
        {valor ?? vacio}
      </dd>
    </div>
  );
}
