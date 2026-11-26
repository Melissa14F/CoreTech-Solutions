/**
 * Panel de accesibilidad (RNF-07): la configuración, su memoria en el navegador y cómo se aplica a la página.
 * Es la misma lógica de prototipo/js/accesibilidad.js: los ajustes son clases `modo-*` sobre <html> (sus estilos están en
 * styles/global.css, sección 13) y una variable CSS `--filtro-pagina` para saturación, contraste y daltonismo.
 */

export type Daltonismo = 'ninguno' | 'protanopia' | 'deuteranopia' | 'tritanopia';

export interface Configuracion {
  leerEnVoz: boolean;
  /** 0 a 3 */
  tamanoTexto: number;
  altoContraste: boolean;
  /** 0 normal · 1 baja · 2 alta · 3 escala de grises */
  saturacion: number;
  pausarAnimaciones: boolean;
  fuenteDislexia: boolean;
  /** 0 a 3 */
  interlineado: number;
  /** 0 a 3 */
  interletrado: number;
  daltonismo: Daltonismo;
  resaltarTitulos: boolean;
  resaltarEnlaces: boolean;
  textoNegrita: boolean;
  guiaLectura: boolean;
}

export const CONFIGURACION_INICIAL: Configuracion = {
  leerEnVoz: false, tamanoTexto: 0, altoContraste: false, saturacion: 0, pausarAnimaciones: false,
  fuenteDislexia: false, interlineado: 0, interletrado: 0, daltonismo: 'ninguno',
  resaltarTitulos: false, resaltarEnlaces: false, textoNegrita: false, guiaLectura: false,
};

/** Misma clave que el prototipo: la configuración sobrevive entre páginas y visitas. */
export const CLAVE_ALMACENAMIENTO = 'coretech-accesibilidad';

/** Matrices de color (feColorMatrix) que simulan cada tipo de daltonismo. */
export const MATRICES_DALTONISMO: Record<Exclude<Daltonismo, 'ninguno'>, string> = {
  protanopia: '0.567 0.433 0 0 0  0.558 0.442 0 0 0  0 0.242 0.758 0 0  0 0 0 1 0',
  deuteranopia: '0.625 0.375 0 0 0  0.7 0.3 0 0 0  0 0.3 0.7 0 0  0 0 0 1 0',
  tritanopia: '0.95 0.05 0 0 0  0 0.433 0.567 0 0  0 0.475 0.525 0 0  0 0 0 1 0',
};

const DALTONISMOS: Daltonismo[] = ['ninguno', 'protanopia', 'deuteranopia', 'tritanopia'];

/** Lee lo guardado; cualquier valor raro (o sin almacenamiento) cae al valor inicial de ese ajuste. */
export function leerConfiguracion(): Configuracion {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE_ALMACENAMIENTO) ?? '{}') as Partial<Record<keyof Configuracion, unknown>>;
    const resultado = { ...CONFIGURACION_INICIAL } as Record<string, unknown>;
    for (const clave of Object.keys(CONFIGURACION_INICIAL) as (keyof Configuracion)[]) {
      const valor = guardado[clave];
      const inicial = CONFIGURACION_INICIAL[clave];
      if (typeof inicial === 'boolean' && typeof valor === 'boolean') resultado[clave] = valor;
      else if (typeof inicial === 'number' && Number.isInteger(valor) && (valor as number) >= 0 && (valor as number) <= 3) resultado[clave] = valor;
    }
    if (DALTONISMOS.includes(guardado.daltonismo as Daltonismo)) resultado.daltonismo = guardado.daltonismo;
    return resultado as unknown as Configuracion;
  } catch {
    return { ...CONFIGURACION_INICIAL };
  }
}

export function guardarConfiguracion(configuracion: Configuracion) {
  try {
    localStorage.setItem(CLAVE_ALMACENAMIENTO, JSON.stringify(configuracion));
  } catch {
    /* sin almacenamiento (ventana privada, datos bloqueados): el ajuste vale mientras la página esté abierta */
  }
}

const CLASES_POR_NIVEL = ['modo-texto', 'modo-interlineado', 'modo-interletrado'] as const;

/** Pone la configuración en <html>. Se llama antes de pintar (main.tsx) para que no haya parpadeo. */
export function aplicarConfiguracion(c: Configuracion, raiz: HTMLElement = document.documentElement) {
  const clases = raiz.classList;
  for (const base of CLASES_POR_NIVEL) for (let n = 1; n <= 3; n++) clases.remove(`${base}-${n}`);
  if (c.tamanoTexto) clases.add(`modo-texto-${c.tamanoTexto}`);
  if (c.interlineado) clases.add(`modo-interlineado-${c.interlineado}`);
  if (c.interletrado) clases.add(`modo-interletrado-${c.interletrado}`);
  clases.toggle('modo-contraste', c.altoContraste);
  clases.toggle('modo-pausa', c.pausarAnimaciones);
  clases.toggle('modo-dislexia', c.fuenteDislexia);
  clases.toggle('modo-titulos', c.resaltarTitulos);
  clases.toggle('modo-enlaces', c.resaltarEnlaces);
  clases.toggle('modo-negrita', c.textoNegrita);
  clases.toggle('modo-guia', c.guiaLectura);
  clases.toggle('modo-voz', c.leerEnVoz);

  const filtros: string[] = [];
  if (c.saturacion === 1) filtros.push('saturate(.45)');
  if (c.saturacion === 2) filtros.push('saturate(1.8)');
  if (c.saturacion === 3) filtros.push('grayscale(1)');
  if (c.altoContraste) filtros.push('contrast(1.08)');
  if (c.daltonismo !== 'ninguno') filtros.push(`url(#filtro-${c.daltonismo})`);
  // El filtro se aplica al contenido de la página, no al panel (así las muestras de color se ven tal cual)
  raiz.style.setProperty('--filtro-pagina', filtros.length ? filtros.join(' ') : 'none');
}

/** Lee un texto con la voz del navegador (español, si hay). No hace nada si el navegador no tiene síntesis de voz. */
export function leerEnVozAlta(texto: string) {
  if (!('speechSynthesis' in window) || !texto) return;
  window.speechSynthesis.cancel();
  const locucion = new SpeechSynthesisUtterance(texto.replace(/\s+/g, ' ').trim().slice(0, 600));
  locucion.lang = 'es-CO';
  const voces = window.speechSynthesis.getVoices().filter((v) => /^es/i.test(v.lang));
  if (voces.length) locucion.voice = voces.find((v) => /CO|MX|US/.test(v.lang)) ?? voces[0]!;
  window.speechSynthesis.speak(locucion);
}
