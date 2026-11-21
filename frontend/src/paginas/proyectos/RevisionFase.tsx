import { useEffect, useState, type FormEvent } from 'react';

import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Dialogo } from '../../componentes/Dialogo';
import { Icono } from '../../componentes/Icono';
import { diaCorto } from '../../componentes/LineaTiempoFases';
import { fechaConDia, nombrePila } from '../../proyectos/plan';
import {
  MAX_CAMBIO, MAX_RONDAS_AJUSTE, MIN_CAMBIO, esConflictoDeEstado, faseSiguiente, seguimiento,
  type DetalleProyecto, type FaseDetalle,
} from '../../seguimiento/api';
import { separarErrores } from '../acceso/erroresFormulario';

const porcentaje = (valor: number) => `${String(Math.round(valor * 10) / 10).replace('.', ',')} %`;

interface PropiedadesRevision {
  detalle: DetalleProyecto;
  fase: FaseDetalle;
  /** Después de aprobar o pedir cambios: el padre avisa al usuario y vuelve a pedir el proyecto. */
  alResponder: (mensaje: string) => Promise<void>;
}

/**
 * La fase que el equipo entregó y espera la respuesta del cliente (HU-11, RN-17; prototipo: .revision-fase de
 * cliente/proyecto-app-de-reservas.html): «Aprobar fase» suma su porcentaje y empieza la siguiente; «Pedir cambios» es un ajuste
 * (2 rondas incluidas) o algo nuevo que se cotiza.
 */
export function RevisionFase({ detalle, fase, alResponder }: PropiedadesRevision) {
  const [abierto, setAbierto] = useState<'aprobar' | 'cambios' | null>(null);
  const pila = nombrePila(detalle.productOwner);
  const siguiente = faseSiguiente(detalle, fase);
  const rondasQueQuedan = MAX_RONDAS_AJUSTE - fase.rondasAjusteUsadas;

  return (
    <>
      <div className="revision-fase" data-revision-fase>
        <span className="revision-fase__icono"><Icono nombre="carpeta" grande /></span>
        <div className="revision-fase__texto">
          <strong>{pila} terminó esta fase{fase.entregadaAt ? ` el ${diaCorto(fase.entregadaAt)}` : ''}. Falta tu aprobación.</strong>
          <p>
            Revisa lo que te envió por correo. Si está bien, apruébala: el proyecto suma {porcentaje(fase.porcentaje)}{' '}
            {siguiente ? <>y empieza <strong>{siguiente.nombre}</strong></> : 'y queda terminado, con 30 días de garantía'}. Si algo no te convence, pide cambios.
          </p>
          <ul className="revision-fase__datos">
            {fase.plazoAprobacion && (
              <li>
                <Icono nombre="reloj" />
                <span>Tienes hasta el <strong>{fechaConDia(fase.plazoAprobacion)}</strong> para responder. Si no lo haces, te recordaremos y la fase se dará por aprobada.</span>
              </li>
            )}
            <li>
              <Icono nombre="mensaje" />
              <span>Te quedan <strong>{rondasQueQuedan} de {MAX_RONDAS_AJUSTE}</strong> rondas de ajustes incluidas en esta fase.</span>
            </li>
          </ul>
        </div>
        <div className="revision-fase__acciones">
          <Boton variante="contorno" icono="mensaje" onClick={() => setAbierto('cambios')}>Pedir cambios</Boton>
          <Boton variante="exito" icono="exito" onClick={() => setAbierto('aprobar')}>Aprobar fase</Boton>
        </div>
      </div>

      <DialogoAprobar
        abierto={abierto === 'aprobar'} detalle={detalle} fase={fase} siguiente={siguiente?.nombre}
        alCerrar={() => setAbierto(null)}
        alTerminar={async (mensaje) => { setAbierto(null); await alResponder(mensaje); }}
      />
      <DialogoCambios
        abierto={abierto === 'cambios'} detalle={detalle} fase={fase} rondasQueQuedan={rondasQueQuedan}
        alCerrar={() => setAbierto(null)}
        alTerminar={async (mensaje) => { setAbierto(null); await alResponder(mensaje); }}
      />
    </>
  );
}

interface PropiedadesDialogo {
  abierto: boolean;
  detalle: DetalleProyecto;
  fase: FaseDetalle;
  alCerrar: () => void;
  alTerminar: (mensaje: string) => Promise<void>;
}

function DialogoAprobar({ abierto, detalle, fase, siguiente, alCerrar, alTerminar }: PropiedadesDialogo & { siguiente?: string }) {
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (abierto) setError(null);
  }, [abierto]);

  async function aprobar() {
    setEnviando(true);
    setError(null);
    try {
      const r = await seguimiento.aprobarFase(fase.id);
      const mensaje = r.estadoProyecto === 'EN_GARANTIA' && r.garantiaHasta
        ? `¡Tu proyecto está terminado! Tiene 30 días de garantía, hasta el ${fechaConDia(r.garantiaHasta)}.`
        : `${fase.nombre} aprobada. Tu proyecto va en ${porcentaje(r.avancePorcentaje)}.`;
      setEnviando(false);
      await alTerminar(mensaje);
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        // La fase ya cambió: se cierra el diálogo, se muestra el mensaje y se vuelve a pedir el proyecto
        await alTerminar(e.message);
        return;
      }
      setError(separarErrores(e).general);
    }
  }

  return (
    <Dialogo
      abierto={abierto} alCerrar={() => !enviando && alCerrar()} icono="exito" tono="exito" titulo={`¿Aprobar ${fase.nombre}?`}
      texto={<>Confirmas que lo entregado cumple con lo que necesitas. El proyecto suma {porcentaje(fase.porcentaje)}{' '}
        {siguiente ? <>y empieza <strong>{siguiente}</strong></> : 'y queda terminado'}. Esta aprobación queda registrada.</>}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton variante="exito" cargando={enviando} onClick={() => void aprobar()}>Sí, aprobar</Boton>
        </>
      )}
    >
      {error && <Alerta tipo="error">{error}</Alerta>}
      <p className="dialogo__texto">{detalle.nombre}</p>
    </Dialogo>
  );
}

function DialogoCambios({ abierto, detalle, fase, rondasQueQuedan, alCerrar, alTerminar }: PropiedadesDialogo & { rondasQueQuedan: number }) {
  const pila = nombrePila(detalle.productOwner);
  const [tipo, setTipo] = useState<'AJUSTE' | 'ALCANCE' | ''>('');
  const [texto, setTexto] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const sinRondas = rondasQueQuedan <= 0;

  useEffect(() => {
    if (abierto) {
      setTipo(sinRondas ? 'ALCANCE' : '');
      setTexto('');
      setErrores({});
      setGeneral(null);
    }
  }, [abierto, sinRondas]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    const limpio = texto.trim();
    if (!tipo) nuevos.tipo = 'Elige el tipo de cambio.';
    if (!limpio) nuevos.descripcion = 'Describe qué quieres cambiar.';
    else if (limpio.length < MIN_CAMBIO) nuevos.descripcion = `Escribe al menos ${MIN_CAMBIO} caracteres para que ${pila} entienda el cambio.`;
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0 || !tipo) return;

    setEnviando(true);
    try {
      await seguimiento.pedirCambio(fase.id, tipo, limpio);
      setEnviando(false);
      await alTerminar(tipo === 'AJUSTE'
        ? `Ajuste enviado a ${detalle.productOwner}. Te avisaremos cuando vuelva a entregar la fase.`
        : `Solicitud enviada. ${pila} te enviará el costo y el tiempo antes de hacerla.`);
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        await alTerminar(e.message);
        return;
      }
      const { porCampo, general: otro } = separarErrores(e);
      setErrores(porCampo);
      setGeneral(otro);
    }
  }

  return (
    <Dialogo
      abierto={abierto} alCerrar={() => !enviando && alCerrar()} ancho icono="mensaje" tono="advertencia"
      titulo={`Pedir cambios en ${fase.nombre}`} texto={`Cuéntanos qué quieres cambiar. ${pila} lo revisará y te entregará la fase de nuevo.`}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Cancelar</Boton>
          <Boton type="submit" form="formulario-cambios" variante="acento" icono="mensaje" cargando={enviando}>Enviar</Boton>
        </>
      )}
    >
      <form id="formulario-cambios" onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <fieldset className={['grupo-opciones', 'campo', 'tipo-cambio', errores.tipo && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">¿Qué tipo de cambio es?</legend>
          <label className="tipo-cambio__opcion">
            <input type="radio" name="tipo_cambio" value="AJUSTE" checked={tipo === 'AJUSTE'} disabled={sinRondas}
                   onChange={() => { setTipo('AJUSTE'); setErrores((e) => ({ ...e, tipo: '' })); }} />
            <span>
              <strong>Ajuste a lo entregado</strong>
              <small>
                {sinRondas
                  ? 'Ya usaste las 2 rondas; un nuevo cambio se cotiza'
                  : `Algo no te convence o no quedó como lo acordamos. Incluido: te quedan ${rondasQueQuedan} de ${MAX_RONDAS_AJUSTE} rondas.`}
              </small>
            </span>
          </label>
          <label className="tipo-cambio__opcion">
            <input type="radio" name="tipo_cambio" value="ALCANCE" checked={tipo === 'ALCANCE'}
                   onChange={() => { setTipo('ALCANCE'); setErrores((e) => ({ ...e, tipo: '' })); }} />
            <span>
              <strong>Algo nuevo que no estaba acordado</strong>
              <small>Es un cambio de alcance: antes de hacerlo, {pila} te envía el costo y cuántos días agrega a la entrega. Puedes aprobar la fase mientras tanto.</small>
            </span>
          </label>
          {errores.tipo && (
            <p className="campo__mensaje grupo-opciones__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.tipo}</span></p>
          )}
        </fieldset>
        <Campo etiqueta="Describe el cambio" error={errores.descripcion || undefined} contador={{ actual: texto.length, maximo: MAX_CAMBIO }}
               ayuda="Sé concreto: qué falta, qué sobra o qué no funciona como esperabas.">
          <textarea className="campo__control--area" rows={4} value={texto} maxLength={MAX_CAMBIO} onChange={(e) => setTexto(e.target.value)}
                    placeholder="Ejemplo: falta que los clientes puedan elegir con qué estilista reservar." />
        </Campo>
      </form>
    </Dialogo>
  );
}
