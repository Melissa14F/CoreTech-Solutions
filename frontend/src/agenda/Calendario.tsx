import { useEffect, useRef, useState, type KeyboardEvent } from 'react';

import { Icono } from '../componentes/Icono';
import type { Franja } from './api';
import { armarFecha, diaSemana, diasDelMes, mesDe, partes, sumarDias, textoDia, tituloMes } from './fechas';

const SEMANA = [
  ['L', 'Lunes'], ['M', 'Martes'], ['X', 'Miércoles'], ['J', 'Jueves'], ['V', 'Viernes'], ['S', 'Sábado'], ['D', 'Domingo'],
];

interface InfoDia {
  libres: number;
  disponible: boolean;
  /** Por qué no se puede elegir (también se anuncia con el lector de pantalla). */
  motivo: string;
  festivo: boolean;
}

/** Qué se puede decir de un día a partir de sus franjas (la API no da el motivo de un día sin cupos). */
export function infoDia(fecha: string, franjas: Franja[] | null, hoy: string): InfoDia {
  const delDia = (franjas ?? []).filter((f) => f.fecha === fecha);
  const libres = delDia.filter((f) => f.estado === 'LIBRE').length;
  if (fecha < hoy) return { libres: 0, disponible: false, motivo: 'Ya pasó', festivo: false };
  if (franjas === null) return { libres: 0, disponible: false, motivo: 'Cargando horarios', festivo: false };
  if (libres > 0) return { libres, disponible: true, motivo: '', festivo: false };
  if (fecha === hoy) return { libres: 0, disponible: false, motivo: 'Hoy', festivo: false };
  if (delDia.some((f) => f.estado === 'OCUPADO')) return { libres: 0, disponible: false, motivo: 'Sin cupos', festivo: false };
  if (delDia.some((f) => f.estado === 'NO_HABIL')) {
    return { libres: 0, disponible: false, motivo: 'No disponible: festivo en Colombia o fuera del plazo', festivo: true };
  }
  const dia = diaSemana(fecha);
  if (dia === 0 || dia === 6) return { libres: 0, disponible: false, motivo: 'Fin de semana', festivo: false };
  return { libres: 0, disponible: false, motivo: 'Sin horarios de atención', festivo: false };
}

interface PropiedadesCalendario {
  /** Mes que se ve ("2026-11"). */
  mes: string;
  /** Hoy en la zona del cliente. */
  hoy: string;
  /** Franjas del mes; null mientras se cargan. */
  franjas: Franja[] | null;
  seleccionado: string | null;
  alElegir: (fecha: string) => void;
  alCambiarMes: (mes: string) => void;
  /** Primer y último mes que se pueden ver. */
  mesMinimo: string;
  mesMaximo: string;
}

/**
 * Calendario del mes en la hora del cliente (HU-01). Se usa con el teclado: flechas para moverse por los días,
 * Inicio y Fin para el principio y el final de la semana, Re Pág y Av Pág para cambiar de mes, y Enter o espacio
 * para elegir. Los días que no se pueden elegir también reciben el foco (aria-disabled) para que el lector de
 * pantalla diga que no están disponibles y por qué.
 */
export function Calendario({ mes, hoy, franjas, seleccionado, alElegir, alCambiarMes, mesMinimo, mesMaximo }: PropiedadesCalendario) {
  const total = diasDelMes(mes);
  const primerDia = armarFecha(partes(`${mes}-01`).anio, partes(`${mes}-01`).mes, 1);
  const vacios = (diaSemana(primerDia) + 6) % 7; // la semana empieza el lunes

  const [enfocado, setEnfocado] = useState<string>(seleccionado && mesDe(seleccionado) === mes ? seleccionado : primerDia);
  const moverFoco = useRef(false);
  const cuadricula = useRef<HTMLDivElement>(null);

  // Si cambia el mes (flechas, teclado), el día con el foco queda dentro del mes que se ve.
  useEffect(() => {
    if (mesDe(enfocado) !== mes) {
      const dia = Math.min(partes(enfocado).dia, diasDelMes(mes));
      setEnfocado(armarFecha(partes(`${mes}-01`).anio, partes(`${mes}-01`).mes, dia));
    }
  }, [mes, enfocado]);

  useEffect(() => {
    if (!moverFoco.current) return;
    moverFoco.current = false;
    cuadricula.current?.querySelector<HTMLElement>(`[data-fecha="${enfocado}"]`)?.focus();
  }, [enfocado, mes]);

  const puedeIr = (nuevoMes: string) => nuevoMes >= mesMinimo && nuevoMes <= mesMaximo;

  function irA(fecha: string) {
    const nuevoMes = mesDe(fecha);
    if (nuevoMes !== mes && !puedeIr(nuevoMes)) return;
    moverFoco.current = true;
    setEnfocado(fecha);
    if (nuevoMes !== mes) alCambiarMes(nuevoMes);
  }

  function alPulsar(evento: KeyboardEvent<HTMLButtonElement>, fecha: string) {
    const dia = (diaSemana(fecha) + 6) % 7; // 0 = lunes
    const movimientos: Record<string, string> = {
      ArrowLeft: sumarDias(fecha, -1),
      ArrowRight: sumarDias(fecha, 1),
      ArrowUp: sumarDias(fecha, -7),
      ArrowDown: sumarDias(fecha, 7),
      Home: sumarDias(fecha, -dia),
      End: sumarDias(fecha, 6 - dia),
    };
    let destino = movimientos[evento.key];
    if (evento.key === 'PageUp' || evento.key === 'PageDown') {
      const paso = evento.key === 'PageUp' ? -1 : 1;
      const p = partes(fecha);
      const m = new Date(Date.UTC(p.anio, p.mes - 1 + paso, 1));
      const ultimo = new Date(Date.UTC(m.getUTCFullYear(), m.getUTCMonth() + 1, 0)).getUTCDate();
      destino = armarFecha(m.getUTCFullYear(), m.getUTCMonth() + 1, Math.min(p.dia, ultimo));
    }
    if (!destino) return;
    evento.preventDefault();
    irA(destino);
  }

  const dias = Array.from({ length: total }, (_, i) => armarFecha(partes(`${mes}-01`).anio, partes(`${mes}-01`).mes, i + 1));
  const anterior = mesDe(sumarDias(primerDia, -1));
  const siguiente = mesDe(sumarDias(primerDia, total));

  return (
    <fieldset className="grupo-opciones campo calendario" data-grupo="fecha">
      <legend className="solo-lector">Día de la cita</legend>
      <div className="calendario__cabecera">
        <p className="calendario__titulo" aria-live="polite">{tituloMes(mes)}</p>
        <div className="calendario__flechas">
          <button type="button" className="boton-icono boton-icono--pequeno" aria-label="Mes anterior"
            disabled={!puedeIr(anterior)} onClick={() => alCambiarMes(anterior)}>
            <Icono nombre="chevronIzquierda" />
          </button>
          <button type="button" className="boton-icono boton-icono--pequeno" aria-label="Mes siguiente"
            disabled={!puedeIr(siguiente)} onClick={() => alCambiarMes(siguiente)}>
            <Icono nombre="chevronDerecha" />
          </button>
        </div>
      </div>
      <div className="calendario__semana" aria-hidden="true">
        {SEMANA.map(([letra, nombre]) => <abbr key={letra} title={nombre}>{letra}</abbr>)}
      </div>
      <div className="calendario__mes" role="group" aria-label={tituloMes(mes)} aria-busy={franjas === null} ref={cuadricula}>
        {Array.from({ length: vacios }, (_, i) => <span key={`v${i}`} className="calendario__vacio" aria-hidden="true" />)}
        {dias.map((fecha) => {
          const info = infoDia(fecha, franjas, hoy);
          const elegido = fecha === seleccionado;
          const cupos = info.libres === 1 ? '1 horario libre' : `${info.libres} horarios libres`;
          const clases = [
            'calendario__dia',
            fecha === hoy && 'calendario__dia--hoy',
            info.disponible && info.libres <= 2 && 'calendario__dia--pocos',
            !info.disponible && 'calendario__dia--inactivo',
            info.festivo && 'calendario__dia--festivo',
            elegido && 'calendario__dia--elegido',
          ].filter(Boolean).join(' ');
          return (
            <button
              key={fecha}
              type="button"
              className={clases}
              data-fecha={fecha}
              tabIndex={fecha === enfocado ? 0 : -1}
              aria-disabled={!info.disponible || undefined}
              aria-pressed={info.disponible ? elegido : undefined}
              aria-current={fecha === hoy ? 'date' : undefined}
              aria-label={`${textoDia(fecha)}, ${info.disponible ? cupos : `no disponible. ${info.motivo}`}`}
              title={info.disponible ? cupos : info.motivo}
              onFocus={() => setEnfocado(fecha)}
              onClick={() => info.disponible && alElegir(fecha)}
              onKeyDown={(e) => alPulsar(e, fecha)}
            >
              <span className="calendario__numero" aria-hidden="true">{partes(fecha).dia}</span>
              {info.disponible && <span className="calendario__cupos" aria-hidden="true" />}
            </button>
          );
        })}
      </div>
      <ul className="leyenda" aria-hidden="true">
        <li className="leyenda__item"><span className="leyenda__muestra"><span className="leyenda__punto" /></span>Con horarios libres</li>
        <li className="leyenda__item"><span className="leyenda__muestra"><span className="leyenda__punto leyenda__punto--pocos" /></span>Quedan pocos</li>
        <li className="leyenda__item"><span className="leyenda__muestra leyenda__muestra--inactiva" />No disponible</li>
      </ul>
    </fieldset>
  );
}
