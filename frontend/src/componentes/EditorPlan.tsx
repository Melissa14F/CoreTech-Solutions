import { useEffect, useRef, useState } from 'react';

import {
  MAX_FASES, MAX_PASOS, MIN_FASES, nuevaFase, nuevoPaso, porcentajeFase,
  type ErroresPlan, type FaseEditable, type PasoEditable,
} from '../proyectos/plan';
import { Boton } from './Boton';
import { Campo } from './Campo';
import { Dialogo } from './Dialogo';
import { Icono } from './Icono';
import { Insignia } from './Insignia';
import { useNotificar } from './Notificaciones';

interface PropiedadesEditorPlan {
  fases: FaseEditable[];
  alCambiar: (fases: FaseEditable[]) => void;
  /** Errores por campo y la lista de arriba (los calcula quien guarda: `validarPlan` o la respuesta 422). */
  errores: ErroresPlan;
  /** Quién aprueba las fases marcadas; en el plan de un proyecto, el nombre del cliente. */
  cliente?: string;
  /** Distingue los ids si hay dos editores en la misma página. */
  prefijo?: string;
}

function mover<T>(lista: T[], i: number, d: number): T[] {
  const j = i + d;
  if (j < 0 || j >= lista.length) return lista;
  const a = lista[i];
  const b = lista[j];
  if (a === undefined || b === undefined) return lista;
  const copia = [...lista];
  copia[i] = b;
  copia[j] = a;
  return copia;
}

/**
 * Editor de fases y pasos de la plantilla de un servicio (HU-27) y del plan de un proyecto (HU-28), igual al
 * prototipo (js/plan-editor.js): agrega, quita y reordena con botones (no solo arrastrando), marca las fases que
 * aprueba el cliente y muestra en vivo cuántas fases hay y cuánto vale cada una. Es controlado: quien lo usa guarda.
 */
export function EditorPlan({ fases, alCambiar, errores, cliente = 'el cliente', prefijo = 'plan' }: PropiedadesEditorPlan) {
  const notificar = useNotificar();
  const [abiertas, setAbiertas] = useState<Set<string>>(() => new Set(fases[0] ? [fases[0].clave] : []));
  /** Lo que se va a quitar, mientras se espera la confirmación. */
  const [porQuitar, setPorQuitar] = useState<{ fase: FaseEditable; paso?: PasoEditable } | null>(null);
  const raiz = useRef<HTMLDivElement>(null);
  /** Valores de data-foco, por orden de preferencia, a los que se lleva el foco después del próximo pintado. */
  const enfocar = useRef<string[]>([]);

  useEffect(() => {
    const candidatos = enfocar.current;
    enfocar.current = [];
    for (const c of candidatos) {
      const objetivo = raiz.current?.querySelector<HTMLElement>(`[data-foco="${c}"]:not(:disabled)`);
      if (objetivo) {
        objetivo.focus();
        return;
      }
    }
  });

  const n = fases.length;
  const aprueban = fases.filter((f) => f.apruebaCliente).length;
  const pasosTotal = fases.reduce((s, f) => s + f.pasos.length, 0);

  const cambiarFase = (clave: string, cambios: Partial<FaseEditable>) =>
    alCambiar(fases.map((f) => (f.clave === clave ? { ...f, ...cambios } : f)));
  const cambiarPaso = (fase: FaseEditable, clave: string, cambios: Partial<PasoEditable>) =>
    cambiarFase(fase.clave, { pasos: fase.pasos.map((p) => (p.clave === clave ? { ...p, ...cambios } : p)) });

  function agregarFase() {
    if (n >= MAX_FASES) return;
    const fase = nuevaFase();
    enfocar.current = [`nombre-${fase.clave}`];
    setAbiertas((a) => new Set(a).add(fase.clave));
    alCambiar([...fases, fase]);
    notificar(`Fase ${n + 1} agregada. Ahora cada fase vale ${porcentajeFase(n + 1)}.`, 'info');
  }

  function quitarFase(fase: FaseEditable) {
    if (n <= MIN_FASES) return;
    enfocar.current = ['agregar-fase'];
    alCambiar(fases.filter((f) => f.clave !== fase.clave));
    notificar(`"${fase.nombre.trim() || 'La fase'}" se quitó. Ahora son ${n - 1} fases y cada una vale ${porcentajeFase(n - 1)}.`, 'info');
  }

  function moverFase(i: number, d: number) {
    const fase = fases[i];
    if (!fase) return;
    enfocar.current = [`${d < 0 ? 'subir' : 'bajar'}-fase-${fase.clave}`, `${d < 0 ? 'bajar' : 'subir'}-fase-${fase.clave}`];
    alCambiar(mover(fases, i, d));
    notificar(`"${fase.nombre.trim() || 'Fase'}" ahora es la fase ${i + d + 1}.`, 'info');
  }

  function agregarPaso(fase: FaseEditable) {
    if (fase.pasos.length >= MAX_PASOS) return;
    const paso = nuevoPaso();
    enfocar.current = [`titulo-${paso.clave}`];
    cambiarFase(fase.clave, { pasos: [...fase.pasos, paso] });
  }

  function quitarPaso(fase: FaseEditable, paso: PasoEditable) {
    enfocar.current = [`agregar-paso-${fase.clave}`];
    cambiarFase(fase.clave, { pasos: fase.pasos.filter((p) => p.clave !== paso.clave) });
  }

  function moverPaso(fase: FaseEditable, i: number, d: number) {
    const clave = fase.pasos[i]?.clave;
    if (!clave) return;
    enfocar.current = [`${d < 0 ? 'subir' : 'bajar'}-paso-${clave}`, `${d < 0 ? 'bajar' : 'subir'}-paso-${clave}`];
    cambiarFase(fase.clave, { pasos: mover(fase.pasos, i, d) });
  }

  return (
    <div className="editor-plan" ref={raiz}>
      <div className="editor-plan__resumen" aria-live="polite">
        <span className="editor-plan__dato"><strong>{n}</strong> {n === 1 ? 'fase' : 'fases'}</span>
        <span className="editor-plan__dato"><strong>{n ? porcentajeFase(n) : '—'}</strong> vale cada una</span>
        <span className="editor-plan__dato"><strong>{pasosTotal}</strong> {pasosTotal === 1 ? 'paso' : 'pasos'}</span>
        <span className={`editor-plan__dato${aprueban ? '' : ' editor-plan__dato--error'}`}>
          <strong>{aprueban}</strong> las aprueba {cliente}
        </span>
        <span className="editor-plan__barra" aria-hidden="true">
          {fases.map((f) => (
            <span key={f.clave} className={`editor-plan__segmento${f.apruebaCliente ? ' editor-plan__segmento--cliente' : ''}`} />
          ))}
        </span>
      </div>

      {errores.generales.length > 0 && (
        <div className="alerta alerta--error editor-plan__errores" role="alert">
          <Icono nombre="alerta" />
          <div>
            <p className="editor-plan__errores-titulo">Revisa el plan antes de guardar:</p>
            <ul>{errores.generales.map((e) => <li key={e}>{e}</li>)}</ul>
          </div>
        </div>
      )}

      <ol className="editor-plan__fases">
        {fases.map((fase, f) => {
          const id = `${prefijo}-${fase.clave}`;
          const campo = `fases[${f}]`;
          const errorPasos = errores.porCampo[`${campo}.pasos`];
          const invalida = Object.keys(errores.porCampo).some((k) => k.startsWith(`${campo}.`));
          return (
            <li
              key={fase.clave}
              className={['fase-editor', fase.apruebaCliente && 'fase-editor--cliente', invalida && 'fase-editor--invalida'].filter(Boolean).join(' ')}
            >
              <details
                className="fase-editor__detalle"
                open={abiertas.has(fase.clave) || invalida}
                onToggle={(e) => {
                  const abierta = e.currentTarget.open;
                  setAbiertas((a) => {
                    if (a.has(fase.clave) === abierta) return a;
                    const nuevo = new Set(a);
                    if (abierta) nuevo.add(fase.clave); else nuevo.delete(fase.clave);
                    return nuevo;
                  });
                }}
              >
                <summary className="fase-editor__resumen">
                  <span className="fase-editor__numero" aria-hidden="true">{f + 1}</span>
                  <span className="fase-editor__titulo">
                    <strong>{fase.nombre.trim() || 'Fase sin nombre'}</strong>
                    <small>
                      Fase {f + 1} de {n} · {fase.pasos.length} {fase.pasos.length === 1 ? 'paso' : 'pasos'} · vale {porcentajeFase(n)}
                    </small>
                  </span>
                  {fase.apruebaCliente
                    ? <Insignia variante="aprueba"><Icono nombre="persona" />La aprueba {cliente}</Insignia>
                    : <Insignia variante="neutra">La cierra el equipo</Insignia>}
                  <Icono nombre="chevronAbajo" className="fase-editor__flecha" />
                </summary>

                <div className="fase-editor__cuerpo">
                  <div className="fase-editor__fila">
                    <Campo etiqueta="Nombre de la fase" id={`${id}-nombre`} error={errores.porCampo[`${campo}.nombre`]}>
                      <input
                        type="text" maxLength={60} value={fase.nombre} data-foco={`nombre-${fase.clave}`}
                        onChange={(e) => cambiarFase(fase.clave, { nombre: e.target.value })}
                      />
                    </Campo>
                    <label className="casilla fase-editor__aprueba">
                      <input
                        type="checkbox" checked={fase.apruebaCliente}
                        onChange={(e) => cambiarFase(fase.clave, { apruebaCliente: e.target.checked })}
                      />
                      <span>
                        La aprueba {cliente}
                        <small>Tiene 3 días hábiles para aprobarla o pedir ajustes.</small>
                      </span>
                    </label>
                  </div>

                  <Campo
                    etiqueta={<>Qué es <span className="campo__opcional">lo verá {cliente}</span></>}
                    id={`${id}-que-es`} error={errores.porCampo[`${campo}.queEs`]}
                  >
                    <input type="text" maxLength={140} value={fase.queEs} onChange={(e) => cambiarFase(fase.clave, { queEs: e.target.value })} />
                  </Campo>

                  <fieldset className="pasos-editor">
                    <legend className="campo__etiqueta">
                      Pasos <span className="campo__opcional">{fase.pasos.length} de {MAX_PASOS} como máximo</span>
                    </legend>
                    <ol className="pasos-editor__lista">
                      {fase.pasos.map((paso, p) => {
                        const ruta = `${campo}.pasos[${p}]`;
                        const errorTitulo = errores.porCampo[`${ruta}.titulo`];
                        const errorExplicacion = errores.porCampo[`${ruta}.explicacion`];
                        const idPaso = `${id}-${paso.clave}`;
                        return (
                          <li key={paso.clave} className="paso-editor">
                            <span className="paso-editor__numero" aria-hidden="true">{p + 1}</span>
                            <div className="paso-editor__campos">
                              <div className={`campo campo--compacto${errorTitulo ? ' campo--invalido' : ''}`}>
                                <label className="solo-lector" htmlFor={`${idPaso}-titulo`}>Paso {p + 1} de la fase {f + 1}: título</label>
                                <input
                                  className="campo__control" id={`${idPaso}-titulo`} type="text" maxLength={80}
                                  placeholder="Qué se hace, en pocas palabras" value={paso.titulo} data-foco={`titulo-${paso.clave}`}
                                  aria-invalid={errorTitulo ? true : undefined} aria-describedby={errorTitulo ? `${idPaso}-titulo-mensaje` : undefined}
                                  onChange={(e) => cambiarPaso(fase, paso.clave, { titulo: e.target.value })}
                                />
                                {errorTitulo && (
                                  <p className="campo__mensaje" id={`${idPaso}-titulo-mensaje`}>
                                    <Icono nombre="alerta" /><span className="campo__mensaje-texto">{errorTitulo}</span>
                                  </p>
                                )}
                              </div>
                              <div className={`campo campo--compacto${errorExplicacion ? ' campo--invalido' : ''}`}>
                                <label className="solo-lector" htmlFor={`${idPaso}-explicacion`}>Paso {p + 1}: explicación para {cliente}</label>
                                <input
                                  className="campo__control paso-editor__explicacion" id={`${idPaso}-explicacion`} type="text" maxLength={160}
                                  placeholder={`Explicación sencilla para ${cliente} (opcional)`} value={paso.explicacion}
                                  aria-invalid={errorExplicacion ? true : undefined}
                                  onChange={(e) => cambiarPaso(fase, paso.clave, { explicacion: e.target.value })}
                                />
                                {errorExplicacion && (
                                  <p className="campo__mensaje"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errorExplicacion}</span></p>
                                )}
                              </div>
                            </div>
                            <div className="paso-editor__acciones" role="group" aria-label={`Acciones del paso ${p + 1}`}>
                              <button
                                type="button" className="boton-icono boton-icono--mini" aria-label={`Subir paso ${p + 1}`}
                                disabled={p === 0} data-foco={`subir-paso-${paso.clave}`}
                                onClick={() => moverPaso(fase, p, -1)}
                              ><Icono nombre="chevronArriba" /></button>
                              <button
                                type="button" className="boton-icono boton-icono--mini" aria-label={`Bajar paso ${p + 1}`}
                                disabled={p === fase.pasos.length - 1} data-foco={`bajar-paso-${paso.clave}`}
                                onClick={() => moverPaso(fase, p, 1)}
                              ><Icono nombre="chevronAbajo" /></button>
                              <button
                                type="button" className="boton-icono boton-icono--mini boton-icono--peligro" aria-label={`Quitar paso ${p + 1}`}
                                disabled={fase.pasos.length === 1} onClick={() => setPorQuitar({ fase, paso })}
                              ><Icono nombre="papelera" /></button>
                            </div>
                          </li>
                        );
                      })}
                    </ol>
                    {errorPasos && (
                      <p className="campo__mensaje pasos-editor__mensaje es-visible" role="alert">
                        <Icono nombre="alerta" /><span className="campo__mensaje-texto">{errorPasos}</span>
                      </p>
                    )}
                    <Boton variante="fantasma" tamano="pequeno" icono="mas" data-foco={`agregar-paso-${fase.clave}`}
                      disabled={fase.pasos.length >= MAX_PASOS} onClick={() => agregarPaso(fase)}>
                      Agregar paso
                    </Boton>
                  </fieldset>

                  <div className="fase-editor__acciones">
                    <Boton variante="contorno" tamano="pequeno" icono="chevronArriba" disabled={f === 0}
                      data-foco={`subir-fase-${fase.clave}`} onClick={() => moverFase(f, -1)}>Subir fase</Boton>
                    <Boton variante="contorno" tamano="pequeno" icono="chevronAbajo" disabled={f === n - 1}
                      data-foco={`bajar-fase-${fase.clave}`} onClick={() => moverFase(f, 1)}>Bajar fase</Boton>
                    <Boton variante="contorno" tamano="pequeno" icono="papelera" className="boton--texto-peligro"
                      disabled={n <= MIN_FASES} title={n <= MIN_FASES ? 'Un plan necesita al menos 2 fases' : undefined}
                      onClick={() => setPorQuitar({ fase })}>Quitar fase</Boton>
                  </div>
                </div>
              </details>
            </li>
          );
        })}
      </ol>

      <div className="editor-plan__pie">
        <Boton variante="contorno" icono="mas" data-foco="agregar-fase" disabled={n >= MAX_FASES} onClick={agregarFase}>Agregar fase</Boton>
        <p className="editor-plan__limite">
          {n >= MAX_FASES ? 'Llegaste al máximo de 8 fases.' : 'Entre 2 y 8 fases. Las fases nuevas empiezan con un paso.'}
        </p>
      </div>

      <Dialogo
        abierto={porQuitar !== null}
        alCerrar={() => setPorQuitar(null)}
        tono="peligro"
        icono="papelera"
        titulo={porQuitar?.paso ? '¿Quitar este paso?' : '¿Quitar esta fase?'}
        texto={porQuitar?.paso
          ? `Se quitará el paso «${porQuitar.paso.titulo.trim() || 'sin título'}». Se aplica al guardar la plantilla.`
          : `Se quitará la fase «${porQuitar?.fase.nombre.trim() || 'sin nombre'}» con sus ${porQuitar?.fase.pasos.length ?? 0} pasos. Se aplica al guardar la plantilla.`}
        acciones={(
          <>
            <Boton variante="contorno" onClick={() => setPorQuitar(null)}>Cancelar</Boton>
            <Boton
              variante="peligro"
              onClick={() => {
                if (porQuitar?.paso) quitarPaso(porQuitar.fase, porQuitar.paso);
                else if (porQuitar) quitarFase(porQuitar.fase);
                setPorQuitar(null);
              }}
            >
              {porQuitar?.paso ? 'Quitar paso' : 'Quitar fase'}
            </Boton>
          </>
        )}
      />
    </div>
  );
}
