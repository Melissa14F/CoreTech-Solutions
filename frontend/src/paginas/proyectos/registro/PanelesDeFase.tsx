import { useState, type FormEvent } from 'react';

import { Alerta } from '../../../componentes/Alerta';
import { Boton } from '../../../componentes/Boton';
import { Campo } from '../../../componentes/Campo';
import { Dialogo } from '../../../componentes/Dialogo';
import { Icono } from '../../../componentes/Icono';
import { useNotificar } from '../../../componentes/Notificaciones';
import { nombrePila } from '../../../proyectos/plan';
import {
  MAX_NOTA_CORRECCION, MIN_NOTA, correccionesAbiertas, esConflictoDeEstado, faseSiguiente, pasosPorHacer, seguimiento,
  type DetalleProyecto, type FaseDetalle, type PasoDetalle,
} from '../../../seguimiento/api';
import { separarErrores } from '../../acceso/erroresFormulario';

const pct = (n: number) => `${String(Math.round(n * 10) / 10).replace('.', ',')} %`;

/** Las correcciones que siguen abiertas en fases ya aprobadas (o en garantía): restan al proyecto hasta terminarlas (RN-16). */
export function CorreccionesAbiertas({ detalle, alTerminar }: { detalle: DetalleProyecto; alTerminar: () => Promise<void> }) {
  const notificar = useNotificar();
  const pila = nombrePila(detalle.cliente);
  const enGarantia = detalle.estado === 'EN_GARANTIA';
  const abiertas = detalle.fases
    .filter((f) => f.estado === 'APROBADA' || f.estado === 'EN_CORRECCION')
    .flatMap((f) => correccionesAbiertas(f).map((paso) => ({ fase: f, paso })));
  const [elegida, setElegida] = useState<{ fase: FaseDetalle; paso: PasoDetalle } | null>(null);
  const [nota, setNota] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (abiertas.length === 0) return null;

  const cerrar = () => { setElegida(null); setNota(''); setError(undefined); setGeneral(null); };

  async function terminar(evento: FormEvent) {
    evento.preventDefault();
    const texto = nota.trim();
    if (!texto) return setError('Describe qué se corrigió.');
    if (texto.length < MIN_NOTA) return setError(`Escribe al menos ${MIN_NOTA} caracteres.`);
    if (!elegida) return;
    setEnviando(true);
    setError(undefined);
    setGeneral(null);
    try {
      await seguimiento.marcarHecho(elegida.paso.id, texto);
      const vuelve = elegida.fase.estado === 'EN_CORRECCION' && correccionesAbiertas(elegida.fase).length === 1;
      notificar(vuelve ? `Corrección terminada. ${elegida.fase.nombre} vuelve a sumar su ${pct(elegida.fase.porcentaje)}.` : 'Corrección terminada.');
      cerrar();
      setEnviando(false);
      await alTerminar();
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        notificar(e.message);
        cerrar();
        await alTerminar();
        return;
      }
      const { porCampo, general: otro } = separarErrores(e);
      setError(porCampo.nota);
      setGeneral(otro);
    }
  }

  return (
    <section className="correcciones-abiertas" aria-labelledby="titulo-abiertas">
      <h3 className="correcciones-abiertas__titulo" id="titulo-abiertas">
        <Icono nombre="llave" />Correcciones en fases aprobadas <span>{abiertas.length}</span>
      </h3>
      <p className="correcciones-abiertas__nota">
        {enGarantia
          ? 'El proyecto está en garantía: la fase corregida sigue aprobada y el avance sigue en 100 %.'
          : 'Cada fase en corrección deja de sumar su porcentaje hasta que la termines. Las de otras fases no impiden entregar la fase actual.'}
      </p>
      <ul className="correcciones-abiertas__lista">
        {abiertas.map(({ fase, paso }) => (
          <li className="correccion-abierta" key={paso.id}>
            <div className="correccion-abierta__texto">
              <strong>{paso.titulo}</strong>
              <small>
                {fase.nombre} · abierta
                {fase.estado === 'EN_CORRECCION' && <span className="correccion-abierta__resta">−{pct(fase.porcentaje)}</span>}
              </small>
            </div>
            <Boton variante="contorno" tamano="pequeno" icono="exito" onClick={() => setElegida({ fase, paso })}>Marcar corregida</Boton>
          </li>
        ))}
      </ul>

      <Dialogo
        abierto={elegida !== null} alCerrar={() => !enviando && cerrar()} ancho icono="exito" tono="exito" titulo="Terminar corrección"
        texto={elegida ? (enGarantia || elegida.fase.estado !== 'EN_CORRECCION'
          ? `«${elegida.paso.titulo}» quedará terminada.`
          : `Al terminarla, ${elegida.fase.nombre} vuelve a sumar su ${pct(elegida.fase.porcentaje)} al proyecto.`) : undefined}
        acciones={(
          <>
            <Boton variante="contorno" disabled={enviando} onClick={cerrar}>Cancelar</Boton>
            <Boton type="submit" form="formulario-resolver" variante="exito" icono="exito" cargando={enviando}>Marcar corregida</Boton>
          </>
        )}
      >
        <form id="formulario-resolver" onSubmit={(e) => void terminar(e)} noValidate>
          {general && <Alerta tipo="error">{general}</Alerta>}
          <Campo etiqueta={`Nota para ${pila}: ¿qué se corrigió?`} error={error} contador={{ actual: nota.length, maximo: MAX_NOTA_CORRECCION }}>
            <textarea className="campo__control--area" rows={4} value={nota} maxLength={MAX_NOTA_CORRECCION} onChange={(e) => setNota(e.target.value)}
                      placeholder="Ejemplo: el modelo de datos ya guarda las existencias por talla y color." />
          </Campo>
        </form>
      </Dialogo>
    </section>
  );
}

/**
 * Entregar la fase al cliente o cerrarla (HU-13; prototipo: .entrega-fase): se activa cuando todos los pasos están hechos y la
 * fase no tiene correcciones abiertas. Las de otras fases no la bloquean (RN-16).
 */
export function EntregaFase({ detalle, fase, alEntregar }: { detalle: DetalleProyecto; fase: FaseDetalle; alEntregar: () => Promise<void> }) {
  const notificar = useNotificar();
  const pila = nombrePila(detalle.cliente);
  const siguiente = faseSiguiente(detalle, fase);
  const faltan = pasosPorHacer(fase).length;
  const abiertas = correccionesAbiertas(fase).length;
  const [confirmar, setConfirmar] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const aprueba = fase.apruebaCliente;
  const final = aprueba ? '' : siguiente ? ` y empieza ${siguiente.nombre}.` : ' y el proyecto queda terminado.';

  const texto = abiertas > 0 ? 'Termina las correcciones de esta fase antes de entregarla.'
    : faltan > 0 ? (faltan === 1 ? 'Falta 1 paso por terminar.' : `Faltan ${faltan} pasos por terminar.`)
    : aprueba ? `Todo listo: ${pila} tendrá 3 días hábiles para revisarla y aprobarla.`
    : `Todo listo: la cierras tú y el proyecto suma ${pct(fase.porcentaje)}. ${pila} verá lo hecho.`;

  async function entregar() {
    setEnviando(true);
    setError(null);
    try {
      const r = await seguimiento.entregarFase(fase.id);
      setConfirmar(false);
      setEnviando(false);
      if (r.estado === 'EN_REVISION') notificar(`Fase entregada. ${pila} recibirá un aviso para aprobarla.`);
      else if (r.estadoProyecto === 'EN_GARANTIA') notificar(`Proyecto terminado. ${pila} recibirá un aviso. Queda en garantía 30 días.`);
      else notificar(`${fase.nombre} cerrada. El proyecto va en ${pct(r.avancePorcentaje)}${siguiente ? ` y empieza ${siguiente.nombre}` : ''}.`);
      await alEntregar();
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        // Los pasos o la fase cambiaron mientras tanto (otra pestaña, o un ajuste nuevo): se muestra el mensaje y lo último
        setConfirmar(false);
        notificar(e.message);
        await alEntregar();
        return;
      }
      setError(separarErrores(e).general);
    }
  }

  return (
    <div className="entrega-fase">
      <div>
        <strong>{aprueba ? `Entregar ${fase.nombre} a ${pila}` : `Cerrar ${fase.nombre}`}</strong>
        <p>{texto}</p>
      </div>
      <Boton variante="exito" icono="exito" disabled={faltan > 0 || abiertas > 0} onClick={() => setConfirmar(true)}>
        {aprueba ? 'Entregar fase' : 'Cerrar fase'}
      </Boton>
      <Dialogo
        abierto={confirmar} alCerrar={() => !enviando && setConfirmar(false)} icono="enviar" titulo={aprueba ? `¿Entregar ${fase.nombre}?` : `¿Cerrar ${fase.nombre}?`}
        texto={aprueba
          ? `${pila} tendrá 3 días hábiles para revisarla. Cuando la apruebe, el proyecto suma ${pct(fase.porcentaje)}${siguiente ? ` y empieza ${siguiente.nombre}.` : ' y el proyecto queda terminado.'}`
          : `Esta fase la cierra el equipo: el proyecto suma ${pct(fase.porcentaje)}${final} ${pila} verá todo lo hecho.`}
        acciones={(
          <>
            <Boton variante="contorno" disabled={enviando} onClick={() => setConfirmar(false)}>Revisar de nuevo</Boton>
            <Boton variante="acento" cargando={enviando} onClick={() => void entregar()}>{aprueba ? 'Sí, entregar' : 'Sí, cerrar'}</Boton>
          </>
        )}
      >
        {error && <Alerta tipo="error">{error}</Alerta>}
      </Dialogo>
    </div>
  );
}
