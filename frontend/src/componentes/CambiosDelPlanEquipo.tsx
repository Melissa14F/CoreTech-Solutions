import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';

import { separarErrores } from '../paginas/acceso/erroresFormulario';
import { pesos } from '../proyectos/admin';
import {
  MAX_MOTIVO, MIN_MOTIVO, VALOR_ADICIONAL_MAXIMO, cambiosPlanApi, type CambiosDelProyecto, type Propuesta, type TipoCambio,
} from '../proyectos/cambiosPlan';
import { esConflictoDeEstado, type DetalleProyecto } from '../seguimiento/api';
import { Alerta } from './Alerta';
import { Boton } from './Boton';
import { Campo } from './Campo';
import { Dialogo } from './Dialogo';
import { DetalleCambioPlan } from './DetalleCambioPlan';
import { useNotificar } from './Notificaciones';

const TIPOS: { tipo: TipoCambio; texto: string; ayuda: string }[] = [
  { tipo: 'AGREGAR_FASE', texto: 'Agregar una fase', ayuda: 'Una fase nueva con sus pasos, después de la fase actual.' },
  { tipo: 'QUITAR_FASE', texto: 'Quitar una fase pendiente', ayuda: 'Solo una fase que todavía no empieza; las aprobadas no cambian.' },
  { tipo: 'CAMBIAR_FECHA', texto: 'Cambiar solo la fecha de entrega', ayuda: 'Las fases y los pasos siguen igual.' },
  { tipo: 'QUITAR_PASO', texto: 'Quitar un paso pendiente', ayuda: 'Un paso que todavía no empieza; queda como quitado, no se borra.' },
  { tipo: 'AGREGAR_ALCANCE', texto: 'Cotizar un trabajo nuevo', ayuda: 'Lo que el cliente pidió como «algo nuevo» al revisar una fase.' },
];

interface Propiedades {
  detalle: DetalleProyecto;
}

/**
 * Los cambios del plan aprobado, del lado del desarrollador (Product Owner) (HU-30; prototipo: la tarjeta de cambio de
 * desarrollador/registrar-avance-app-de-reservas.html): lo propuesto y su estado, y «Proponer cambio de plan». Lo propone él,
 * lo autoriza el administrador y lo acepta el cliente; mientras tanto el plan sigue igual.
 */
export function CambiosDelPlanEquipo({ detalle }: Propiedades) {
  const notificar = useNotificar();
  const [datos, setDatos] = useState<CambiosDelProyecto | null>(null);
  const [abierto, setAbierto] = useState(false);

  const cargar = useCallback(async () => {
    try {
      setDatos(await cambiosPlanApi.deProyecto(detalle.codigo));
    } catch {
      setDatos(null);
    }
  }, [detalle.codigo]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  if (!datos) return null;
  const recientes = datos.cambios.slice(0, 3);

  return (
    <section className="tarjeta aparecer cambios-del-plan" aria-labelledby="titulo-cambios-equipo">
      <h2 className="tarjeta__titulo" id="titulo-cambios-equipo">Cambios del plan</h2>
      <p className="equipo__nota">
        El plan aprobado no se cambia solo: tú propones el cambio, la administración lo autoriza y el cliente lo acepta.
      </p>
      {datos.valorAdicionalAceptado > 0 && (
        <p className="cambio-plan__nota">Valor total: <strong>{pesos(datos.valorTotal)}</strong> ({pesos(datos.valorCotizado)} + {pesos(datos.valorAdicionalAceptado)} aceptados).</p>
      )}
      {recientes.length > 0 && (
        <ul className="lista-cambios">
          {recientes.map((c) => <li key={c.id}><DetalleCambioPlan cambio={c} conComparacion={false} /></li>)}
        </ul>
      )}
      {datos.puedeProponer ? (
        <Boton variante="contorno" icono="mas" onClick={() => setAbierto(true)}>Proponer cambio de plan</Boton>
      ) : datos.hayCambioAbierto ? (
        <p className="cambio-plan__nota">Ya hay un cambio esperando respuesta. Cuando se resuelva podrás proponer otro.</p>
      ) : (
        <p className="cambio-plan__nota">Solo se cambia el plan de un proyecto activo con el plan aprobado.</p>
      )}
      {abierto && (
        <DialogoProponer
          detalle={detalle} datos={datos} alCerrar={() => setAbierto(false)}
          alTerminar={async (mensaje) => { setAbierto(false); if (mensaje) notificar(mensaje); await cargar(); }}
        />
      )}
    </section>
  );
}

interface PropiedadesDialogo {
  detalle: DetalleProyecto;
  datos: CambiosDelProyecto;
  alCerrar: () => void;
  alTerminar: (mensaje: string) => Promise<void>;
}

function DialogoProponer({ detalle, datos, alCerrar, alTerminar }: PropiedadesDialogo) {
  const fases = detalle.fases;
  const pendientes = fases.filter((f) => f.estado === 'PENDIENTE');
  /** Un paso que todavía no empieza, de la fase actual o de una pendiente, y que no es el único de su fase. */
  const pasosQuitables = useMemo(() => fases.flatMap((f) => (f.estado === 'PENDIENTE' || f.estado === 'EN_CURSO')
    ? f.pasos.filter((p) => p.tipo === 'PASO' && p.estado === 'PENDIENTE' && f.pasos.length > 1)
        .map((p) => ({ id: p.id, texto: `${f.nombre} · ${p.titulo}` }))
    : []), [fases]);
  const minimo = Math.max(0, ...fases.filter((f) => f.estado !== 'PENDIENTE').map((f) => f.orden)) + 1;
  const posiciones = Array.from({ length: Math.max(0, fases.length + 1 - minimo + 1) }, (_, i) => minimo + i);

  const [tipo, setTipo] = useState<TipoCambio | ''>('');
  const [motivo, setMotivo] = useState('');
  const [entrega, setEntrega] = useState(detalle.fechaEstimadaEntrega);
  const [valor, setValor] = useState('0');
  const [nombreFase, setNombreFase] = useState('');
  const [posicion, setPosicion] = useState('');
  const [apruebaCliente, setApruebaCliente] = useState(false);
  const [pasos, setPasos] = useState('');
  const [faseId, setFaseId] = useState('');
  const [pasoId, setPasoId] = useState('');
  const [solicitudId, setSolicitudId] = useState('');
  const [tituloPaso, setTituloPaso] = useState('');
  const [explicacionPaso, setExplicacionPaso] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function validar(): Record<string, string> {
    const e: Record<string, string> = {};
    if (!tipo) { e.tipo = 'Elige el tipo de cambio.'; return e; }
    const m = motivo.trim();
    if (!m) e.motivo = 'Cuéntale al administrador por qué hace falta este cambio.';
    else if (m.length < MIN_MOTIVO) e.motivo = `Escribe al menos ${MIN_MOTIVO} caracteres.`;
    if (tipo !== 'QUITAR_PASO') {
      if (!entrega) e.nuevaEntrega = 'Elige la nueva fecha de entrega.';
      else if (tipo === 'CAMBIAR_FECHA' && entrega === detalle.fechaEstimadaEntrega) e.nuevaEntrega = 'Elige una fecha distinta a la entrega actual';
      const v = Number(valor);
      if (valor.trim() === '' || !Number.isInteger(v) || v < 0 || v > VALOR_ADICIONAL_MAXIMO) e.valorAdicional = 'Escribe un valor entero entre $0 y $200.000.000.';
    }
    if (tipo === 'AGREGAR_FASE') {
      if (nombreFase.trim().length < 3) e.nombreFase = 'Escribe el nombre de la fase (al menos 3 caracteres).';
      if (!posicion) e.posicion = 'Elige dónde va la fase.';
      const lista = pasos.split('\n').map((p) => p.trim()).filter(Boolean);
      if (lista.length === 0) e.pasos = 'Agrega al menos un paso.';
      else if (lista.length > 12) e.pasos = 'Cada fase puede tener máximo 12 pasos.';
      else if (lista.some((p) => p.length < 3 || p.length > 80)) e.pasos = 'El título de cada paso debe tener entre 3 y 80 caracteres.';
    }
    if (tipo === 'QUITAR_FASE' && !faseId) e.faseId = 'Elige la fase que quieres quitar.';
    if (tipo === 'QUITAR_PASO' && !pasoId) e.pasoId = 'Elige el paso que quieres quitar.';
    if (tipo === 'AGREGAR_ALCANCE') {
      if (!solicitudId) e.solicitudCambioId = 'Elige la solicitud de algo nuevo que vas a cotizar.';
      if (tituloPaso.trim().length < 3) e.tituloPaso = 'Escribe el título del paso (al menos 3 caracteres).';
      if (explicacionPaso.trim().length < 20) e.explicacionPaso = 'Explica qué se hará (al menos 20 caracteres).';
    }
    return e;
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos = validar();
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0 || !tipo) return;

    const cuerpo: Propuesta = { tipo, motivo: motivo.trim() };
    if (tipo !== 'QUITAR_PASO') {
      cuerpo.nuevaEntrega = entrega;
      cuerpo.valorAdicional = Number(valor);
    }
    if (tipo === 'AGREGAR_FASE') {
      cuerpo.nombreFase = nombreFase.trim();
      cuerpo.posicion = Number(posicion);
      cuerpo.apruebaCliente = apruebaCliente;
      cuerpo.pasos = pasos.split('\n').map((p) => p.trim()).filter(Boolean).map((titulo) => ({ titulo, explicacion: null }));
    }
    if (tipo === 'QUITAR_FASE') cuerpo.faseId = faseId;
    if (tipo === 'QUITAR_PASO') cuerpo.pasoId = pasoId;
    if (tipo === 'AGREGAR_ALCANCE') {
      cuerpo.solicitudCambioId = solicitudId;
      cuerpo.tituloPaso = tituloPaso.trim();
      cuerpo.explicacionPaso = explicacionPaso.trim();
    }

    setEnviando(true);
    try {
      await cambiosPlanApi.proponer(detalle.codigo, cuerpo);
      setEnviando(false);
      await alTerminar('Cambio enviado al administrador. El plan sigue igual hasta que se autorice y el cliente lo acepte.');
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        // «Ya hay un cambio esperando respuesta», «Solo se quita una fase pendiente…»: se avisa y se vuelve a pedir lo último
        await alTerminar(e.message);
        return;
      }
      const { porCampo, general: otro } = separarErrores(e);
      setErrores(porCampo);
      setGeneral(otro ?? (Object.keys(porCampo).length > 0 ? 'Revisa los campos marcados.' : null));
    }
  }

  return (
    <Dialogo
      abierto alCerrar={() => !enviando && alCerrar()} ancho icono="carpeta" titulo="Proponer cambio de plan"
      texto="El plan aprobado sigue igual hasta que la administración lo autorice y el cliente lo acepte."
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Cancelar</Boton>
          <Boton type="submit" form="formulario-cambio-plan" variante="acento" icono="enviar" cargando={enviando}>Enviar al administrador</Boton>
        </>
      )}
    >
      <form id="formulario-cambio-plan" onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <fieldset className={['grupo-opciones', 'campo', errores.tipo && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">¿Qué quieres cambiar?</legend>
          {TIPOS.map((t) => (
            <label className="tipo-cambio__opcion" key={t.tipo}>
              <input type="radio" name="tipo_cambio_plan" value={t.tipo} checked={tipo === t.tipo}
                     onChange={() => { setTipo(t.tipo); setErrores({}); }} />
              <span><strong>{t.texto}</strong><small>{t.ayuda}</small></span>
            </label>
          ))}
          {errores.tipo && <p className="campo__mensaje grupo-opciones__mensaje" role="alert"><span className="campo__mensaje-texto">{errores.tipo}</span></p>}
        </fieldset>

        {tipo === 'AGREGAR_FASE' && (
          <>
            <Campo etiqueta="Nombre de la fase nueva" error={errores.nombreFase}>
              <input type="text" value={nombreFase} maxLength={60} onChange={(e) => setNombreFase(e.target.value)} />
            </Campo>
            <Campo etiqueta="¿Dónde va?" error={errores.posicion}>
              <select className="select" value={posicion} onChange={(e) => setPosicion(e.target.value)}>
                <option value="">Elige la posición</option>
                {posiciones.map((p) => {
                  const siguiente = fases.find((f) => f.orden === p);
                  return <option key={p} value={p}>{siguiente ? `Posición ${p}, antes de «${siguiente.nombre}»` : `Posición ${p}, al final`}</option>;
                })}
              </select>
            </Campo>
            <label className="tipo-cambio__opcion">
              <input type="checkbox" checked={apruebaCliente} onChange={(e) => setApruebaCliente(e.target.checked)} />
              <span><strong>La aprueba el cliente</strong><small>Si no, la cierra el equipo.</small></span>
            </label>
            <Campo etiqueta="Pasos (uno por línea)" error={errores.pasos} ayuda="De 1 a 12 pasos.">
              <textarea className="campo__control--area" rows={4} value={pasos} onChange={(e) => setPasos(e.target.value)} />
            </Campo>
          </>
        )}
        {tipo === 'QUITAR_FASE' && (
          <Campo etiqueta="Fase que se quita" error={errores.faseId}>
            <select className="select" value={faseId} onChange={(e) => setFaseId(e.target.value)}>
              <option value="">{pendientes.length ? 'Elige una fase pendiente' : 'No hay fases pendientes'}</option>
              {pendientes.map((f) => <option key={f.id} value={f.id}>{f.nombre}</option>)}
            </select>
          </Campo>
        )}
        {tipo === 'QUITAR_PASO' && (
          <Campo etiqueta="Paso que se quita" error={errores.pasoId}>
            <select className="select" value={pasoId} onChange={(e) => setPasoId(e.target.value)}>
              <option value="">{pasosQuitables.length ? 'Elige un paso pendiente' : 'No hay pasos que se puedan quitar'}</option>
              {pasosQuitables.map((p) => <option key={p.id} value={p.id}>{p.texto}</option>)}
            </select>
          </Campo>
        )}
        {tipo === 'AGREGAR_ALCANCE' && (
          <>
            <Campo etiqueta="Lo que pidió el cliente" error={errores.solicitudCambioId}>
              <select className="select" value={solicitudId} onChange={(e) => setSolicitudId(e.target.value)}>
                <option value="">{datos.alcancesPorCotizar.length ? 'Elige una solicitud por cotizar' : 'No hay solicitudes por cotizar'}</option>
                {datos.alcancesPorCotizar.map((a) => <option key={a.id} value={a.id}>{a.fase} · {a.descripcion.slice(0, 70)}</option>)}
              </select>
            </Campo>
            <Campo etiqueta="Título del paso nuevo" error={errores.tituloPaso}>
              <input type="text" value={tituloPaso} maxLength={80} onChange={(e) => setTituloPaso(e.target.value)} />
            </Campo>
            <Campo etiqueta="Qué se hará" error={errores.explicacionPaso} contador={{ actual: explicacionPaso.length, maximo: 400 }}>
              <textarea className="campo__control--area" rows={3} value={explicacionPaso} maxLength={400} onChange={(e) => setExplicacionPaso(e.target.value)} />
            </Campo>
          </>
        )}

        {tipo && (
          <>
            <Campo etiqueta="Motivo" error={errores.motivo} contador={{ actual: motivo.length, maximo: MAX_MOTIVO }}>
              <textarea className="campo__control--area" rows={3} value={motivo} maxLength={MAX_MOTIVO} onChange={(e) => setMotivo(e.target.value)}
                        placeholder="Explica por qué hace falta este cambio." />
            </Campo>
            {tipo !== 'QUITAR_PASO' && (
              <>
                <Campo etiqueta="Nueva fecha de entrega" error={errores.nuevaEntrega} ayuda={`Hoy: ${detalle.fechaEstimadaEntrega}`}>
                  <input type="date" value={entrega} onChange={(e) => setEntrega(e.target.value)} />
                </Campo>
                <Campo etiqueta="Valor adicional (COP)" error={errores.valorAdicional} ayuda="Escribe 0 si no cuesta nada más. Máximo $200.000.000.">
                  <input type="number" min={0} max={VALOR_ADICIONAL_MAXIMO} step={1} value={valor} onChange={(e) => setValor(e.target.value)} />
                </Campo>
              </>
            )}
          </>
        )}
      </form>
    </Dialogo>
  );
}
