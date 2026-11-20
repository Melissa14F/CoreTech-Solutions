import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { fechaEvento } from '../administracion/formato';
import { Icono } from '../componentes/Icono';
import { type Bandeja, type Notificacion, notificacionesApi } from '../comunicacion/notificaciones';

/** Cada cuánto se pide de nuevo la bandeja (HU-24: el contador se mantiene al día sin recargar). */
export const REFRESCO_MS = 60_000;

const VACIA: Bandeja = { noLeidas: 0, notificaciones: [] };

function etiquetaBoton(nuevas: number): string {
  if (nuevas === 0) return 'Notificaciones: no tienes avisos nuevos';
  return `Notificaciones: ${nuevas} ${nuevas === 1 ? 'nueva' : 'nuevas'}`;
}

/**
 * Campana de notificaciones de los tres paneles (HU-24). El punto rojo y la etiqueta muestran cuántas hay
 * sin leer, como el prototipo; al abrirla se ve la lista (no leídas primero). Abrir un aviso lo marca como leído
 * y lleva a su pantalla. Se refresca cada minuto y cada vez que se abre.
 */
export function Campana() {
  const [abierta, setAbierta] = useState(false);
  const [bandeja, setBandeja] = useState<Bandeja>(VACIA);
  const [error, setError] = useState(false);
  const idLista = useId();
  const contenedor = useRef<HTMLDivElement>(null);
  const boton = useRef<HTMLButtonElement>(null);

  const cargar = useCallback(async () => {
    try {
      setBandeja(await notificacionesApi.bandeja());
      setError(false);
    } catch {
      // Sin conexión o la API caída: la campana conserva lo último que tenía y lo intenta en el próximo minuto.
      setError(true);
    }
  }, []);

  useEffect(() => {
    void cargar();
    const intervalo = window.setInterval(() => void cargar(), REFRESCO_MS);
    return () => window.clearInterval(intervalo);
  }, [cargar]);

  useEffect(() => {
    if (!abierta) return;
    const cerrarAfuera = (evento: MouseEvent) => {
      if (!contenedor.current?.contains(evento.target as Node)) setAbierta(false);
    };
    const cerrarConEscape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') {
        setAbierta(false);
        boton.current?.focus();
      }
    };
    document.addEventListener('mousedown', cerrarAfuera);
    document.addEventListener('keydown', cerrarConEscape);
    return () => {
      document.removeEventListener('mousedown', cerrarAfuera);
      document.removeEventListener('keydown', cerrarConEscape);
    };
  }, [abierta]);

  const alternar = () => {
    if (!abierta) void cargar();
    setAbierta((valor) => !valor);
  };

  /** La marca como leída en pantalla de inmediato; si la API falla, la siguiente carga la corrige. */
  const marcarLeida = (aviso: Notificacion) => {
    if (aviso.leida) return;
    setBandeja((actual) => ({
      noLeidas: Math.max(0, actual.noLeidas - 1),
      notificaciones: actual.notificaciones.map((n) => (n.notificacionId === aviso.notificacionId ? { ...n, leida: true } : n)),
    }));
    notificacionesApi.marcarLeida(aviso.notificacionId).catch(() => void cargar());
  };

  const abrirAviso = (aviso: Notificacion) => {
    marcarLeida(aviso);
    if (aviso.enlace) setAbierta(false);
  };

  const marcarTodas = () => {
    setBandeja((actual) => ({ noLeidas: 0, notificaciones: actual.notificaciones.map((n) => ({ ...n, leida: true })) }));
    notificacionesApi.marcarTodas().catch(() => void cargar());
  };

  const { noLeidas, notificaciones } = bandeja;

  return (
    <div className="campana-menu" ref={contenedor}>
      <button
        ref={boton}
        type="button"
        className="boton-icono boton-icono--pequeno campana"
        aria-label={etiquetaBoton(noLeidas)}
        aria-expanded={abierta}
        aria-controls={idLista}
        onClick={alternar}
      >
        <Icono nombre="campana" />
        {noLeidas > 0 && <span className="campana__punto" aria-hidden="true" />}
      </button>
      {abierta && (
        <div className="bandeja" id={idLista} role="region" aria-label="Notificaciones">
          <div className="bandeja__cabecera">
            <p className="bandeja__titulo">
              Notificaciones
              {noLeidas > 0 && <span className="bandeja__contador">{noLeidas} {noLeidas === 1 ? 'nueva' : 'nuevas'}</span>}
            </p>
            {noLeidas > 0 && (
              <button type="button" className="enlace bandeja__marcar" onClick={marcarTodas}>
                Marcar todas como leídas
              </button>
            )}
          </div>
          {error && notificaciones.length === 0 ? (
            <p className="bandeja__vacia">No pudimos cargar tus notificaciones. Intenta de nuevo en un momento.</p>
          ) : notificaciones.length === 0 ? (
            <p className="bandeja__vacia">No tienes notificaciones.</p>
          ) : (
            <ul className="bandeja__lista">
              {notificaciones.map((aviso) => (
                <li key={aviso.notificacionId}>
                  <ElementoAviso aviso={aviso} alAbrir={() => abrirAviso(aviso)} />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function ElementoAviso({ aviso, alAbrir }: { aviso: Notificacion; alAbrir: () => void }) {
  const clase = `bandeja__aviso${aviso.leida ? '' : ' bandeja__aviso--nuevo'}`;
  const contenido = (
    <>
      {!aviso.leida && <span className="solo-lector">Nueva: </span>}
      <span className="bandeja__aviso-titulo">{aviso.titulo}</span>
      <span className="bandeja__aviso-texto">{aviso.mensaje}</span>
      <time className="bandeja__aviso-fecha" dateTime={aviso.fecha}>{fechaEvento(aviso.fecha)}</time>
    </>
  );
  if (aviso.enlace) return <Link className={clase} to={aviso.enlace} onClick={alAbrir}>{contenido}</Link>;
  // Sin pantalla a la que ir: si está nueva, tocarla la marca como leída; si ya se leyó, es solo texto.
  if (aviso.leida) return <div className={clase}>{contenido}</div>;
  return (
    <button type="button" className={clase} onClick={alAbrir} title="Marcar como leída">
      {contenido}
    </button>
  );
}
