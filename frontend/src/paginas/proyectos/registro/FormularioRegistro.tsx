import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';

import { ErrorApi } from '../../../api/cliente';
import { Alerta } from '../../../componentes/Alerta';
import { Boton } from '../../../componentes/Boton';
import { Campo } from '../../../componentes/Campo';
import { Dialogo } from '../../../componentes/Dialogo';
import { Icono } from '../../../componentes/Icono';
import { useNotificar } from '../../../componentes/Notificaciones';
import { nombrePila } from '../../../proyectos/plan';
import {
  MAX_ADJUNTOS, MAX_EXPLICACION, MAX_NOTA, MAX_NOTA_CORRECCION, MIN_NOTA, ORIGENES_CORRECCION, pasoEnCurso, seguimiento,
  validarAdjuntos, type DetalleProyecto, type FaseDetalle, type PasoDetalle,
} from '../../../seguimiento/api';
import { separarErrores } from '../../acceso/erroresFormulario';

const kb = (bytes: number) => (bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);
const redondo = (n: number) => Math.round(n * 10) / 10;
const pct = (n: number) => `${String(redondo(n)).replace('.', ',')} %`;

interface PropiedadesFormulario {
  detalle: DetalleProyecto;
  /** La fase en curso: de ella salen los pasos por hacer. Nula si no hay (en garantía, por ejemplo). */
  fase: FaseDetalle | undefined;
  /** Pasos por hacer de la fase en curso: del plan, ajustes y correcciones. */
  pendientes: PasoDetalle[];
  /** Fases donde se puede agregar una corrección: en curso, aprobadas o en corrección (HU-12). */
  corregibles: FaseDetalle[];
  puedePaso: boolean;
  puedeCorreccion: boolean;
  /** Después de publicar el padre vuelve a pedir el proyecto. */
  alPublicar: () => Promise<void>;
}

const ROTULO: Record<PasoDetalle['tipo'], string> = { PASO: '', CORRECCION: ' (corrección)', AJUSTE: ' (ajuste)', ALCANCE: '' };

/**
 * «Registrar trabajo» (HU-03, HU-12; prototipo: form.formulario-avance): marca un paso de la fase en curso como hecho con una
 * nota y sus bocetos, o agrega una corrección en la fase que se corrige. Lo publicado no se edita ni se borra.
 */
export function FormularioRegistro({ detalle, fase, pendientes, corregibles, puedePaso, puedeCorreccion, alPublicar }: PropiedadesFormulario) {
  const notificar = useNotificar();
  const pila = nombrePila(detalle.cliente);
  const enCurso = fase ? pasoEnCurso(fase) : null;
  const enGarantia = detalle.estado === 'EN_GARANTIA';

  const [tipo, setTipo] = useState<'paso' | 'correccion'>(puedePaso ? 'paso' : 'correccion');
  const [pasoId, setPasoId] = useState(enCurso ?? pendientes[0]?.id ?? '');
  const [faseCorregida, setFaseCorregida] = useState(fase?.id ?? corregibles[0]?.id ?? '');
  const [origen, setOrigen] = useState('');
  const [titulo, setTitulo] = useState('');
  const [nota, setNota] = useState('');
  const [archivos, setArchivos] = useState<File[]>([]);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [confirmar, setConfirmar] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const entrada = useRef<HTMLInputElement>(null);

  // Después de publicar, el paso elegido ya no está pendiente: se pasa al siguiente.
  useEffect(() => {
    if (!pendientes.some((p) => p.id === pasoId)) setPasoId(enCurso ?? pendientes[0]?.id ?? '');
  }, [pendientes, pasoId, enCurso]);
  // Sin pasos por hacer (o en garantía) solo queda la corrección; y viceversa.
  useEffect(() => {
    if (tipo === 'paso' && !puedePaso) setTipo('correccion');
    if (tipo === 'correccion' && !puedeCorreccion && puedePaso) setTipo('paso');
  }, [tipo, puedePaso, puedeCorreccion]);
  useEffect(() => {
    if (!corregibles.some((f) => f.id === faseCorregida)) setFaseCorregida(fase?.id ?? corregibles[0]?.id ?? '');
  }, [corregibles, faseCorregida, fase?.id]);

  const paso = pendientes.find((p) => p.id === pasoId);
  const faseElegida = corregibles.find((f) => f.id === faseCorregida);
  const esCorreccion = tipo === 'correccion';
  const maximo = esCorreccion ? MAX_EXPLICACION : paso && paso.tipo !== 'PASO' ? MAX_NOTA_CORRECCION : MAX_NOTA;
  const faltan = pendientes.length - 1;
  const avance = redondo(detalle.avancePorcentaje);

  function cambiarTipo(nuevo: 'paso' | 'correccion') {
    setTipo(nuevo);
    setErrores({});
    setGeneral(null);
  }

  function revisar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    const texto = nota.trim();
    if (esCorreccion) {
      if (!faseElegida) nuevos.fase = 'Elige la fase que se corrige.';
      if (!origen) nuevos.origen = 'Elige de dónde sale la corrección.';
      if (!titulo.trim()) nuevos.titulo = 'Escribe un nombre corto.';
      else if (titulo.trim().length < 3) nuevos.titulo = 'Escribe al menos 3 caracteres.';
      if (!texto) nuevos.nota = `Explícale a ${pila} qué se va a corregir y por qué.`;
      else if (texto.length < MIN_NOTA) nuevos.nota = `Escribe al menos ${MIN_NOTA} caracteres.`;
    } else {
      if (!paso) nuevos.paso = 'Elige el paso que terminaste.';
      if (!texto) nuevos.nota = `Escribe la nota para que ${pila} sepa qué cambió.`;
      else if (texto.length < MIN_NOTA) nuevos.nota = `Escribe al menos ${MIN_NOTA} caracteres.`;
      else if (texto.length > maximo) nuevos.nota = `Escribe máximo ${maximo} caracteres.`;
      const archivosMal = validarAdjuntos(archivos);
      if (archivosMal) nuevos.adjuntos = archivosMal;
    }
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length === 0) setConfirmar(true);
  }

  function elegirArchivos(evento: ChangeEvent<HTMLInputElement>) {
    const elegidos = Array.from(evento.target.files ?? []);
    setArchivos((actuales) => [...actuales, ...elegidos]);
    evento.target.value = '';
    setErrores((e) => { const { adjuntos: _quitado, ...resto } = e; return resto; });
  }

  /** Errores del servidor: cada campo en su lugar; «explicacion» es el mismo cuadro que la nota. */
  function mostrarError(e: unknown) {
    const { porCampo, general: otro } = separarErrores(e);
    const { explicacion, ...resto } = porCampo;
    setConfirmar(false);
    setEnviando(false);
    setErrores(explicacion ? { ...resto, nota: explicacion } : resto);
    setGeneral(otro ?? null);
    // 409: otra pestaña ya lo registró o la fase cambió; se muestra lo último
    if (e instanceof ErrorApi && e.estado === 409) void alPublicar();
  }

  async function publicar() {
    setEnviando(true);
    let mensaje: string;
    if (esCorreccion) {
      if (!faseElegida) return;
      try {
        const r = await seguimiento.registrarCorreccion(faseElegida.id, origen, titulo.trim(), nota.trim());
        mensaje = r.estadoFase === 'EN_CORRECCION'
          ? `Corrección agregada. El proyecto queda en ${pct(r.avancePorcentaje)} hasta que la termines.`
          : `Corrección agregada en ${faseElegida.nombre}.`;
      } catch (e) {
        mostrarError(e);
        return;
      }
    } else {
      if (!paso) return;
      mensaje = `✓ Paso hecho. ${pila} verá tu nota.`;
      try {
        await seguimiento.marcarHecho(paso.id, nota.trim());
      } catch (e) {
        mostrarError(e);
        return;
      }
      if (archivos.length > 0) {
        try {
          await seguimiento.adjuntar(paso.id, archivos);
        } catch (e) {
          const problema = separarErrores(e);
          mensaje = `El paso quedó publicado, pero no se subieron los archivos: ${problema.porCampo.adjuntos ?? problema.general ?? 'intenta de nuevo más tarde'}`;
        }
      }
    }
    setConfirmar(false);
    setNota('');
    setTitulo('');
    setOrigen('');
    setArchivos([]);
    setErrores({});
    setGeneral(null);
    setEnviando(false);
    notificar(mensaje);
    await alPublicar();
  }

  const impacto = () => {
    if (!esCorreccion) {
      return faltan > 0
        ? <>Después de este paso {faltan === 1 ? <>quedará <strong>1</strong> paso</> : <>quedarán <strong>{faltan}</strong> pasos</>} por hacer en {fase?.nombre}.</>
        : <>Es el último paso por hacer en <strong>{fase?.nombre}</strong>.</>;
    }
    if (!faseElegida) return null;
    if (enGarantia) return <>El proyecto está en garantía: {faseElegida.nombre} sigue aprobada y el avance sigue en <strong>{avance} %</strong>.</>;
    if (faseElegida.estado === 'APROBADA') {
      return <>{faseElegida.nombre} ya estaba aprobada: dejará de sumar su {pct(faseElegida.porcentaje)} mientras la corriges. Proyecto: <strong>{avance} %</strong> → <strong>{Math.max(0, redondo(avance - faseElegida.porcentaje))} %</strong>.</>;
    }
    if (faseElegida.estado === 'EN_CORRECCION') return <>{faseElegida.nombre} ya está en corrección: se agrega otra corrección en ella.</>;
    return <>Se agrega como un paso más de {faseElegida.nombre}. El porcentaje del proyecto no cambia.</>;
  };

  return (
    <>
      <form onSubmit={revisar} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <fieldset className="tipo-registro grupo-opciones">
          <legend className="campo__etiqueta">¿Qué quieres registrar?</legend>
          <div className="tipo-registro__opciones">
            <label className="tipo-registro__opcion">
              <input type="radio" name="tipo" value="paso" checked={tipo === 'paso'} disabled={!puedePaso} onChange={() => cambiarTipo('paso')} />
              <span><strong>Paso hecho</strong><small>{fase ? `Marca con ✓ un paso de ${fase.nombre}.` : 'No quedan pasos por hacer.'}</small></span>
            </label>
            <label className="tipo-registro__opcion">
              <input type="radio" name="tipo" value="correccion" checked={tipo === 'correccion'} disabled={!puedeCorreccion} onChange={() => cambiarTipo('correccion')} />
              <span><strong>Corrección</strong><small>Algo de esta fase o de una aprobada hay que arreglar.</small></span>
            </label>
          </div>
        </fieldset>

        {!esCorreccion ? (
          <Campo etiqueta="Paso terminado" error={errores.paso ?? errores.estado}>
            <select className="select" value={pasoId} onChange={(e) => { setPasoId(e.target.value); setErrores({}); }}>
              {pendientes.map((p) => (
                <option key={p.id} value={p.id}>{p.titulo}{ROTULO[p.tipo]}{p.id === enCurso ? ' (en curso)' : ''}</option>
              ))}
            </select>
          </Campo>
        ) : (
          <>
            <div className="fila-campos">
              <Campo etiqueta="Fase que se corrige" error={errores.fase}>
                <select className="select" value={faseCorregida} onChange={(e) => setFaseCorregida(e.target.value)}>
                  {corregibles.map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.nombre} ({f.estado === 'EN_CURSO' ? 'actual' : f.estado === 'EN_CORRECCION' ? 'en corrección' : 'aprobada'})
                    </option>
                  ))}
                </select>
              </Campo>
              <Campo etiqueta="¿De dónde sale?" error={errores.origen}>
                <select className="select" value={origen} onChange={(e) => { setOrigen(e.target.value); setErrores((x) => ({ ...x, origen: '' })); }}>
                  <option value="">Elige una opción</option>
                  {ORIGENES_CORRECCION.map((o) => <option key={o} value={o}>{o === 'Lo pidió el cliente' ? `Lo pidió ${pila}` : o}</option>)}
                </select>
              </Campo>
            </div>
            <Campo etiqueta="Nombre corto de la corrección" error={errores.titulo}>
              <input value={titulo} maxLength={80} onChange={(e) => setTitulo(e.target.value)} placeholder="Ejemplo: Mostrar el costo del envío antes de pagar" />
            </Campo>
          </>
        )}

        <p className="impacto-proyecto" aria-live="polite">{impacto()}</p>

        <Campo etiqueta={esCorreccion ? `Explícale a ${pila} qué se va a corregir y por qué` : `Nota para ${pila}: ¿qué se hizo?`}
               error={errores.nota} contador={{ actual: nota.length, maximo }}
               ayuda="Escríbelo como se lo dirías en persona, sin términos técnicos.">
          <textarea className="campo__control--area" value={nota} maxLength={maximo} onChange={(e) => setNota(e.target.value)}
                    placeholder={esCorreccion
                      ? 'Ejemplo: el carrito no mostraba el costo del envío; ahora se verá antes de confirmar el pago.'
                      : 'Ejemplo: ya recibes cada pedido en tu panel y tu cliente recibe un correo con el resumen de su compra.'} />
        </Campo>

        {!esCorreccion && (
          <>
            <Campo etiqueta="Bocetos o documentos" opcional error={errores.adjuntos}
                   ayuda={`PNG, JPG, SVG o PDF · hasta ${MAX_ADJUNTOS} archivos de 5 MB.`}>
              <input ref={entrada} type="file" multiple accept=".png,.jpg,.jpeg,.svg,.pdf,image/png,image/jpeg,image/svg+xml,application/pdf"
                     onChange={elegirArchivos} />
            </Campo>
            {archivos.length > 0 && (
              <ul className="archivos-elegidos" aria-label="Archivos elegidos">
                {archivos.map((a, i) => (
                  <li key={`${a.name}-${i}`}>
                    <span>{a.name} <small>{kb(a.size)}</small></span>
                    <button type="button" className="boton-icono boton-icono--pequeno" aria-label={`Quitar ${a.name}`}
                            onClick={() => setArchivos((actuales) => actuales.filter((_, j) => j !== i))}>
                      <Icono nombre="cerrar" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}

        <div className="formulario-avance__pie">
          <p className="formulario-avance__nota"><Icono nombre="candado" />Lo que publiques no se puede editar ni borrar.</p>
          <Boton type="submit" variante="acento" icono="enviar">{esCorreccion ? 'Agregar corrección' : 'Marcar como hecho'}</Boton>
        </div>
      </form>

      <Dialogo
        abierto={confirmar} alCerrar={() => !enviando && setConfirmar(false)} icono="enviar"
        titulo={esCorreccion ? '¿Agregar la corrección?' : '¿Marcar el paso como hecho?'}
        texto={esCorreccion
          ? `Se agregará «${titulo.trim()}» en ${faseElegida?.nombre ?? ''}.${faseElegida?.estado === 'APROBADA' && !enGarantia ? ` La fase dejará de sumar su ${pct(faseElegida.porcentaje)} hasta que la termines.` : ''} No se podrá editar ni borrar.`
          : `«${paso?.titulo ?? ''}» quedará con ✓ y ${pila} verá tu nota. No se podrá editar ni borrar.`}
        acciones={(
          <>
            <Boton variante="contorno" disabled={enviando} onClick={() => setConfirmar(false)}>Revisar de nuevo</Boton>
            <Boton variante="acento" cargando={enviando} onClick={() => void publicar()}>Sí, publicar</Boton>
          </>
        )}
      />
    </>
  );
}
