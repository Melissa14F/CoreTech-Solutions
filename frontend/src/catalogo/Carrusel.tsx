import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode, type TouchEvent } from 'react';

import { Icono } from '../componentes/Icono';

const INTERVALO_MS = 5000;
const DISTANCIA_MINIMA_DESLIZAR = 40;

const movimientoReducido = () =>
  typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** 1, 2 o 3 tarjetas según el ancho, como js/carrusel.js del prototipo. */
function tarjetasPorVista(ancho: number) {
  if (ancho < 600) return 1;
  if (ancho < 960) return 2;
  return 3;
}

interface PropiedadesCarrusel {
  etiqueta: string;
  diapositivas: ReactNode[];
}

/**
 * Carrusel accesible del prototipo (vistas/inicio.css): avanza solo cada 5 s y se detiene al pasar el
 * cursor, al enfocarlo, con el botón de pausa (WCAG 2.2.2) o si el usuario pidió reducir el movimiento.
 * Se maneja con flechas, puntos, teclado (← →) y deslizando el dedo. Solo las tarjetas visibles se pueden
 * enfocar y las lee el lector de pantalla.
 */
export function Carrusel({ etiqueta, diapositivas }: PropiedadesCarrusel) {
  const ventana = useRef<HTMLDivElement>(null);
  const pista = useRef<HTMLDivElement>(null);
  const [porVista, setPorVista] = useState(1);
  const [posicion, setPosicion] = useState(0);
  const [pausado, setPausado] = useState(false);
  const [detenidoTemporal, setDetenidoTemporal] = useState(false);
  const inicioToque = useRef<number | null>(null);

  const ultima = Math.max(0, diapositivas.length - porVista);

  const irA = useCallback((nueva: number) => {
    setPosicion(nueva > ultima ? 0 : nueva < 0 ? ultima : nueva);
  }, [ultima]);

  // Tarjetas por vista según el ancho de la ventana del carrusel
  useLayoutEffect(() => {
    const medir = () => setPorVista(tarjetasPorVista(ventana.current?.clientWidth ?? 0));
    medir();
    window.addEventListener('resize', medir);
    return () => window.removeEventListener('resize', medir);
  }, []);

  useEffect(() => setPosicion((p) => Math.min(p, ultima)), [ultima]);

  // Desplaza la pista: ancho de una tarjeta + separación, por la posición
  useLayoutEffect(() => {
    const elemento = pista.current;
    const primera = elemento?.firstElementChild;
    if (!elemento || !primera) return;
    const separacion = parseFloat(getComputedStyle(elemento).columnGap) || 0;
    const paso = primera.getBoundingClientRect().width + separacion;
    elemento.style.transform = `translateX(${-posicion * paso}px)`;
    // Las tarjetas que no se ven no reciben el foco del teclado (inert; React 18 no tiene la propiedad)
    Array.from(elemento.children).forEach((diapositiva, indice) => {
      const visible = indice >= posicion && indice < posicion + porVista;
      diapositiva.toggleAttribute('inert', !visible);
    });
  }, [posicion, porVista, diapositivas.length]);

  // Avance automático
  const autoplay = !pausado && !detenidoTemporal && !movimientoReducido() && ultima > 0;
  useEffect(() => {
    if (!autoplay) return;
    const temporizador = window.setInterval(() => setPosicion((p) => (p >= ultima ? 0 : p + 1)), INTERVALO_MS);
    return () => window.clearInterval(temporizador);
  }, [autoplay, ultima]);

  function alPulsarTecla(evento: KeyboardEvent) {
    if (evento.key === 'ArrowRight') {
      evento.preventDefault();
      irA(posicion + 1);
    } else if (evento.key === 'ArrowLeft') {
      evento.preventDefault();
      irA(posicion - 1);
    }
  }

  function alTerminarToque(evento: TouchEvent) {
    if (inicioToque.current === null) return;
    const recorrido = (evento.changedTouches[0]?.clientX ?? inicioToque.current) - inicioToque.current;
    if (Math.abs(recorrido) > DISTANCIA_MINIMA_DESLIZAR) irA(posicion + (recorrido < 0 ? 1 : -1));
    inicioToque.current = null;
    setDetenidoTemporal(false);
  }

  return (
    <div
      className="carrusel"
      role="region"
      aria-roledescription="carrusel"
      aria-label={etiqueta}
      onKeyDown={alPulsarTecla}
      onMouseEnter={() => setDetenidoTemporal(true)}
      onMouseLeave={() => setDetenidoTemporal(false)}
      onFocus={() => setDetenidoTemporal(true)}
      onBlur={(evento) => {
        if (!evento.currentTarget.contains(evento.relatedTarget as Node | null)) setDetenidoTemporal(false);
      }}
    >
      <div className="carrusel__escenario">
        <button type="button" className="boton-icono carrusel__flecha carrusel__flecha--anterior" aria-label="Servicios anteriores"
                onClick={() => irA(posicion - 1)}>
          <Icono nombre="chevronIzquierda" />
        </button>
        <div
          className="carrusel__ventana"
          ref={ventana}
          onTouchStart={(e) => {
            inicioToque.current = e.touches[0]?.clientX ?? null;
            setDetenidoTemporal(true);
          }}
          onTouchEnd={alTerminarToque}
        >
          <div className="carrusel__pista" ref={pista} aria-live="off">
            {diapositivas.map((contenido, indice) => {
              const visible = indice >= posicion && indice < posicion + porVista;
              return (
                <div
                  key={indice}
                  className="carrusel__diapositiva"
                  role="group"
                  aria-roledescription="diapositiva"
                  aria-label={`${indice + 1} de ${diapositivas.length}`}
                  aria-hidden={!visible}
                >
                  {contenido}
                </div>
              );
            })}
          </div>
        </div>
        <button type="button" className="boton-icono carrusel__flecha carrusel__flecha--siguiente" aria-label="Servicios siguientes"
                onClick={() => irA(posicion + 1)}>
          <Icono nombre="chevronDerecha" />
        </button>
      </div>
      <div className="carrusel__controles">
        <div className="carrusel__puntos" aria-label="Elegir grupo de servicios">
          {Array.from({ length: ultima + 1 }, (_, i) => (
            <button key={i} type="button" className="carrusel__punto" aria-label={`Ver grupo ${i + 1} de ${ultima + 1}`}
                    aria-current={i === posicion} onClick={() => irA(i)} />
          ))}
        </div>
        <span className="carrusel__estado" aria-hidden="true">{posicion + 1} / {ultima + 1}</span>
        <div className="carrusel__pausa">
          <button type="button" className="boton-icono" aria-label={pausado ? 'Reanudar carrusel' : 'Pausar carrusel'}
                  aria-pressed={pausado} onClick={() => setPausado((p) => !p)}>
            <Icono nombre={pausado ? 'reproducir' : 'pausa'} />
          </button>
        </div>
      </div>
    </div>
  );
}
