import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

import {
  CONFIGURACION_INICIAL, MATRICES_DALTONISMO, aplicarConfiguracion, guardarConfiguracion, leerConfiguracion, leerEnVozAlta,
  type Configuracion, type Daltonismo,
} from '../accesibilidad/configuracion';
import { useNotificar } from './Notificaciones';

/** Trazos de los íconos del panel (los mismos de prototipo/js/accesibilidad.js). */
const TRAZOS = {
  persona: <><circle cx="12" cy="4.5" r="2" /><path d="M5 8.5c2.3.7 4.6 1 7 1s4.7-.3 7-1M12 9.5v4.5M12 14l-3.5 7M12 14l3.5 7" /></>,
  voz: <><path d="M11 5 6 9H2v6h4l5 4V5Z" /><path d="M15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14" /></>,
  texto: <path d="M4 7V5h11v2M9.5 5v14M7 19h5M14 12v-1.5h7V12M17.5 10.5V19M16 19h3" />,
  contraste: <><circle cx="12" cy="12" r="9" /><path d="M12 3a9 9 0 0 1 0 18Z" fill="currentColor" /></>,
  saturacion: <><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="4.5" /><circle cx="12" cy="12" r="1" fill="currentColor" /></>,
  pausa: <><circle cx="12" cy="12" r="9" /><path d="M10 9v6M14 9v6" /></>,
  dislexia: <><path d="M4 18 9.5 5h1L16 18M6 13.5h8.5" /><path d="M3 21c2-1 3 1 5 0s3 1 5 0 3 1 5 0 2 1 3 0" /></>,
  interlineado: <path d="M11 6h10M11 12h10M11 18h10M5 4v16M3 6l2-2 2 2M3 18l2 2 2-2" />,
  interletrado: <><path d="M3 6h18M3 12h12M3 18h18" /><path d="M18 9l3 3-3 3" /></>,
  titulo: <path d="M4 6v12M12 6v12M4 12h8M16 10l3-2v10" />,
  enlace: <><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" /></>,
  negrita: <path d="M7 5h6a3.5 3.5 0 0 1 0 7H7zM7 12h7a3.5 3.5 0 0 1 0 7H7z" />,
  guia: <><rect x="3" y="9" width="18" height="6" rx="1" /><path d="M3 5h18M3 19h18" /></>,
  cerrar: <path d="M18 6 6 18M6 6l12 12" />,
  restablecer: <><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5" /></>,
} satisfies Record<string, ReactNode>;

function Trazo({ nombre }: { nombre: keyof typeof TRAZOS }) {
  return <svg className="icono" viewBox="0 0 24 24" aria-hidden="true" focusable="false">{TRAZOS[nombre]}</svg>;
}

type ClaveNivel = 'tamanoTexto' | 'saturacion' | 'interlineado' | 'interletrado';
type ClaveInterruptor = 'leerEnVoz' | 'altoContraste' | 'pausarAnimaciones' | 'fuenteDislexia';
type ClaveAvanzada = 'resaltarTitulos' | 'resaltarEnlaces' | 'textoNegrita' | 'guiaLectura';

const NIVELES_TEXTO = ['Normal', 'Grande', 'Más grande', 'Máximo'];
const NIVELES_ESPACIO = ['Normal', 'Amplio', 'Más amplio', 'Máximo'];

/** Los ocho mosaicos, en el orden del prototipo. Los que tienen `niveles` pasan de uno al siguiente y vuelven a «Normal». */
const MOSAICOS: { clave: ClaveNivel | ClaveInterruptor; titulo: string; icono: keyof typeof TRAZOS; niveles?: string[] }[] = [
  { clave: 'leerEnVoz', titulo: 'Leer en voz alta', icono: 'voz' },
  { clave: 'tamanoTexto', titulo: 'Tamaño de texto', icono: 'texto', niveles: NIVELES_TEXTO },
  { clave: 'altoContraste', titulo: 'Alto contraste', icono: 'contraste' },
  { clave: 'saturacion', titulo: 'Saturación', icono: 'saturacion', niveles: ['Normal', 'Baja', 'Alta', 'Escala de grises'] },
  { clave: 'pausarAnimaciones', titulo: 'Pausar animaciones', icono: 'pausa' },
  { clave: 'fuenteDislexia', titulo: 'Fuente para dislexia', icono: 'dislexia' },
  { clave: 'interlineado', titulo: 'Interlineado', icono: 'interlineado', niveles: NIVELES_ESPACIO },
  { clave: 'interletrado', titulo: 'Interletrado', icono: 'interletrado', niveles: NIVELES_ESPACIO },
];

const DALTONISMOS: { valor: Daltonismo; nombre: string; detalle: string }[] = [
  { valor: 'ninguno', nombre: 'Ninguno', detalle: 'Colores originales' },
  { valor: 'protanopia', nombre: 'Protanopia', detalle: 'Dificultad con el rojo' },
  { valor: 'deuteranopia', nombre: 'Deuteranopia', detalle: 'Dificultad con el verde' },
  { valor: 'tritanopia', nombre: 'Tritanopia', detalle: 'Dificultad con el azul' },
];

const AVANZADAS: { clave: ClaveAvanzada; titulo: string; icono: keyof typeof TRAZOS; detalle?: string }[] = [
  { clave: 'resaltarTitulos', titulo: 'Resaltar títulos', icono: 'titulo' },
  { clave: 'resaltarEnlaces', titulo: 'Resaltar enlaces', icono: 'enlace' },
  { clave: 'textoNegrita', titulo: 'Texto en negrita', icono: 'negrita' },
  { clave: 'guiaLectura', titulo: 'Guía de lectura', icono: 'guia', detalle: 'Una franja sigue al cursor' },
];

const COLORES_MUESTRA = ['#e53935', '#43a047', '#1e88e5', '#fdd835'];

/**
 * Panel de accesibilidad (RNF-07): una pestaña en el borde derecho que abre un panel con tamaño de texto, contraste, saturación,
 * daltonismo, fuente para dislexia, espaciado, resaltados, guía de lectura y lectura en voz alta. Es el del prototipo
 * (prototipo/js/accesibilidad.js) hecho componente. Va en un portal sobre <body>, fuera de #raiz, porque el filtro de color
 * se aplica al contenido de la página y no debe alcanzar al propio panel. La configuración se recuerda en el navegador.
 */
export function PanelAccesibilidad() {
  const notificar = useNotificar();
  const [configuracion, setConfiguracion] = useState<Configuracion>(leerConfiguracion);
  const [abierto, setAbierto] = useState(false);
  const panel = useRef<HTMLDivElement>(null);
  const botonAbrir = useRef<HTMLButtonElement>(null);
  const botonCerrar = useRef<HTMLButtonElement>(null);
  const guia = useRef<HTMLDivElement>(null);
  const focoPrevio = useRef<HTMLElement | null>(null);
  const hayVoz = typeof window !== 'undefined' && 'speechSynthesis' in window;

  // Cada cambio se aplica a la página y se guarda
  useEffect(() => {
    aplicarConfiguracion(configuracion);
    guardarConfiguracion(configuracion);
    if (!configuracion.leerEnVoz && hayVoz) window.speechSynthesis.cancel();
  }, [configuracion, hayVoz]);

  // La clase sobre <html> es la que muestra el panel y el fondo (CSS del prototipo)
  useEffect(() => {
    document.documentElement.classList.toggle('panel-accesibilidad-abierto', abierto);
    return () => document.documentElement.classList.remove('panel-accesibilidad-abierto');
  }, [abierto]);

  const abrir = useCallback(() => {
    focoPrevio.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setAbierto(true);
    // El panel es invisible hasta que empieza su transición y un elemento invisible no recibe el foco: se reintenta hasta
    // que llegue (unos 600 ms como máximo) y se deja de intentar si la persona ya lo cerró.
    let intentos = 0;
    const llevarFoco = () => {
      if (!document.documentElement.classList.contains('panel-accesibilidad-abierto')) { if (++intentos < 12) setTimeout(llevarFoco, 50); return; }
      botonCerrar.current?.focus();
      if (document.activeElement !== botonCerrar.current && ++intentos < 12) setTimeout(llevarFoco, 50);
    };
    setTimeout(llevarFoco, 30);
  }, []);

  const cerrar = useCallback(() => {
    setAbierto(false);
    (focoPrevio.current ?? botonAbrir.current)?.focus();
  }, []);

  // Escape cierra y el foco no sale del panel mientras está abierto
  useEffect(() => {
    if (!abierto) return;
    const alTeclear = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { cerrar(); return; }
      if (e.key !== 'Tab' || !panel.current) return;
      const enfocables = Array.from(panel.current.querySelectorAll<HTMLElement>('button:not([disabled])'));
      const primero = enfocables[0];
      const ultimo = enfocables[enfocables.length - 1];
      if (!primero || !ultimo) return;
      if (e.shiftKey && document.activeElement === primero) { e.preventDefault(); ultimo.focus(); }
      else if (!e.shiftKey && document.activeElement === ultimo) { e.preventDefault(); primero.focus(); }
    };
    document.addEventListener('keydown', alTeclear);
    return () => document.removeEventListener('keydown', alTeclear);
  }, [abierto, cerrar]);

  // Guía de lectura: una franja que sigue al cursor
  useEffect(() => {
    if (!configuracion.guiaLectura) return;
    const alMover = (e: MouseEvent) => { if (guia.current) guia.current.style.top = `${e.clientY - 22}px`; };
    document.addEventListener('mousemove', alMover, { passive: true });
    return () => document.removeEventListener('mousemove', alMover);
  }, [configuracion.guiaLectura]);

  // Leer en voz alta: el texto seleccionado o el bloque al que se hace clic
  useEffect(() => {
    if (!configuracion.leerEnVoz) return;
    const alSoltar = (e: MouseEvent) => {
      const objetivo = e.target instanceof Element ? e.target : null;
      if (!objetivo || panel.current?.contains(objetivo)) return;
      const seleccion = String(window.getSelection() ?? '');
      if (seleccion.trim().length > 1) { leerEnVozAlta(seleccion); return; }
      const bloque = objetivo.closest<HTMLElement>('p, h1, h2, h3, h4, li, label, td, th, .boton, a');
      if (bloque) leerEnVozAlta(bloque.innerText);
    };
    document.addEventListener('mouseup', alSoltar);
    return () => document.removeEventListener('mouseup', alSoltar);
  }, [configuracion.leerEnVoz]);

  const cambiar = (clave: ClaveNivel | ClaveInterruptor) => {
    const mosaico = MOSAICOS.find((m) => m.clave === clave)!;
    setConfiguracion((actual) => {
      if (mosaico.niveles) return { ...actual, [clave]: ((actual[clave as ClaveNivel] + 1) % mosaico.niveles.length) };
      return { ...actual, [clave]: !actual[clave as ClaveInterruptor] };
    });
    if (clave === 'leerEnVoz' && !configuracion.leerEnVoz) {
      leerEnVozAlta('Lectura en voz alta activada. Selecciona un texto o haz clic sobre un párrafo para escucharlo.');
    }
  };

  const restablecer = () => {
    setConfiguracion({ ...CONFIGURACION_INICIAL });
    notificar('Configuración restablecida.');
  };

  return createPortal(
    <>
      {/* Filtros de color del daltonismo: las muestras del panel y la página los usan por su id */}
      <svg id="filtros-daltonismo" aria-hidden="true" focusable="false" style={{ position: 'absolute', width: 0, height: 0, overflow: 'hidden' }}>
        {(Object.keys(MATRICES_DALTONISMO) as (keyof typeof MATRICES_DALTONISMO)[]).map((tipo) => (
          <filter key={tipo} id={`filtro-${tipo}`}><feColorMatrix type="matrix" values={MATRICES_DALTONISMO[tipo]} /></filter>
        ))}
      </svg>

      <button
        ref={botonAbrir} type="button" className="accesibilidad-boton" aria-label="Abrir opciones de accesibilidad"
        aria-haspopup="dialog" aria-controls="panel-accesibilidad" aria-expanded={abierto} onClick={abrir}
      >
        <Trazo nombre="persona" />
        <span className="accesibilidad-boton__texto">Accesibilidad</span>
      </button>
      <div className="accesibilidad-fondo" onClick={cerrar} />

      {/* Cerrado, el panel no existe para el teclado ni para el lector de pantalla (el CSS ya lo oculta; esto lo dice también en el HTML) */}
      <div
        ref={panel} className="accesibilidad-panel" id="panel-accesibilidad" role="dialog" aria-modal="true" aria-labelledby="titulo-accesibilidad"
        aria-hidden={abierto ? undefined : true} {...(abierto ? {} : { inert: '' })}
      >
        <div className="accesibilidad-panel__encabezado">
          <span className="accesibilidad-panel__icono"><Trazo nombre="persona" /></span>
          <div>
            <h2 className="accesibilidad-panel__titulo" id="titulo-accesibilidad">Accesibilidad</h2>
            <p className="accesibilidad-panel__descripcion">Ajusta la página a tu manera de leer.</p>
          </div>
          <button ref={botonCerrar} type="button" className="boton-icono boton-icono--pequeno" aria-label="Cerrar opciones de accesibilidad" onClick={cerrar}>
            <Trazo nombre="cerrar" />
          </button>
        </div>

        <div className="accesibilidad-panel__cuerpo">
          <div className="accesibilidad-panel__grilla">
            {MOSAICOS.map((m) => {
              const valor = configuracion[m.clave];
              const nivel = typeof valor === 'number' ? valor : 0;
              const sinVoz = m.clave === 'leerEnVoz' && !hayVoz;
              return (
                <button
                  key={m.clave} type="button" className="accesibilidad-opcion" aria-pressed={m.niveles ? nivel > 0 : valor === true}
                  disabled={sinVoz} title={sinVoz ? 'Tu navegador no permite leer en voz alta' : undefined} onClick={() => cambiar(m.clave)}
                >
                  <Trazo nombre={m.icono} />
                  <span>{m.titulo}</span>
                  {m.niveles && (
                    <span className="accesibilidad-niveles" aria-hidden="true">
                      {[0, 1, 2].map((i) => <i key={i} className={`accesibilidad-niveles__nivel${i < nivel ? ' accesibilidad-niveles__nivel--activo' : ''}`} />)}
                    </span>
                  )}
                  {m.niveles && <span className="solo-lector">: {m.niveles[nivel]}</span>}
                </button>
              );
            })}
          </div>

          <p className="accesibilidad-panel__subtitulo" id="titulo-daltonismo">Daltonismo</p>
          <div className="accesibilidad-daltonismo" role="radiogroup" aria-labelledby="titulo-daltonismo">
            {DALTONISMOS.map((d) => (
              <button
                key={d.valor} type="button" className="accesibilidad-daltonismo__opcion" role="radio"
                aria-checked={configuracion.daltonismo === d.valor} onClick={() => setConfiguracion((a) => ({ ...a, daltonismo: d.valor }))}
              >
                <span className="accesibilidad-daltonismo__muestra" aria-hidden="true" style={d.valor !== 'ninguno' ? { filter: `url(#filtro-${d.valor})` } : undefined}>
                  {COLORES_MUESTRA.map((color) => <i key={color} style={{ background: color }} />)}
                </span>
                <span className="accesibilidad-daltonismo__nombre">{d.nombre}</span>
                <span className="accesibilidad-daltonismo__detalle">{d.detalle}</span>
              </button>
            ))}
          </div>

          <p className="accesibilidad-panel__subtitulo">Opciones avanzadas</p>
          {AVANZADAS.map((a) => (
            <button
              key={a.clave} type="button" className="accesibilidad-fila" role="switch" aria-checked={configuracion[a.clave]}
              onClick={() => setConfiguracion((actual) => ({ ...actual, [a.clave]: !actual[a.clave] }))}
            >
              <Trazo nombre={a.icono} />
              <span>
                {a.titulo}
                {a.detalle && <span className="accesibilidad-fila__detalle">{a.detalle}</span>}
              </span>
              <span className="interruptor" aria-hidden="true" />
            </button>
          ))}
        </div>

        <div className="accesibilidad-panel__pie">
          <button type="button" className="boton boton--oscuro boton--bloque" onClick={restablecer}>
            <Trazo nombre="restablecer" />Restablecer configuración
          </button>
        </div>
      </div>

      <div ref={guia} className="guia-lectura" aria-hidden="true" />
    </>,
    document.body,
  );
}
