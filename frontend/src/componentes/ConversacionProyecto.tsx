import { useCallback, useEffect, useState, type FormEvent } from 'react';

import { separarErrores } from '../paginas/acceso/erroresFormulario';
import {
  MAX_MENSAJE, MIN_MENSAJE, mensajesApi, type Hilo, type Mensaje,
} from '../comunicacion/mensajes';
import { fechaCortaReunion } from '../comunicacion/reuniones';
import { esConflictoDeEstado } from '../seguimiento/api';
import { Alerta } from './Alerta';
import { Boton } from './Boton';
import { Campo } from './Campo';
import { Dialogo } from './Dialogo';
import { Icono } from './Icono';
import { useNotificar } from './Notificaciones';

interface Propiedades {
  codigo: string;
  /** El nombre de pila de la otra persona del hilo, para los textos («Escríbele a Andrés…»). */
  interlocutor: string;
}

/**
 * El hilo de mensajes de un proyecto entre el cliente y el desarrollador (Product Owner) (HU-14; prototipo: .conversacion de
 * cliente/proyecto-tienda-en-linea.html). El cliente elige un tema de la lista; el desarrollador responde sin tema (D-41). El
 * autor puede editar su mensaje durante 5 minutos (queda «Editado»); con el proyecto pausado, finalizado o cancelado el hilo
 * es solo de lectura (RN-14).
 */
export function ConversacionProyecto({ codigo, interlocutor }: Propiedades) {
  const notificar = useNotificar();
  const [hilo, setHilo] = useState<Hilo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dialogo, setDialogo] = useState<{ editar: Mensaje | null } | null>(null);

  const cargar = useCallback(async () => {
    try {
      setHilo(await mensajesApi.hilo(codigo));
      setError(null);
    } catch (e) {
      setError(separarErrores(e).general ?? 'No pudimos cargar los mensajes.');
    }
  }, [codigo]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  return (
    <div className="conversacion">
      <div className="conversacion__cabecera">
        <h3 className="conversacion__titulo" id="titulo-mensajes">Mensajes</h3>
        {hilo?.puedeEscribir && (
          <button type="button" className="enlace conversacion__nuevo" onClick={() => setDialogo({ editar: null })}>
            <Icono nombre="enviar" />Escribir
          </button>
        )}
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {hilo && hilo.mensajes.length === 0 && (
        <p className="conversacion__vacio">
          {hilo.puedeEscribir ? `Aún no hay mensajes. Escríbele a ${interlocutor} si tienes una duda o una idea.` : 'Este proyecto todavía no tiene mensajes.'}
        </p>
      )}
      {hilo && hilo.mensajes.length > 0 && (
        <ol className="conversacion__lista" aria-labelledby="titulo-mensajes" aria-live="polite">
          {hilo.mensajes.map((m) => (
            <li className={m.esMio ? 'mensaje mensaje--tuyo' : 'mensaje'} key={m.id}>
              <p className="mensaje__meta">
                <span className="mensaje__autor">{m.esMio ? 'Tú' : m.autor}</span>
                <span>{fechaCortaReunion(m.creadoAt)}</span>
                {m.editadoAt && <span className="mensaje__editado">· Editado</span>}
              </p>
              {m.tema && <p className="mensaje__tema">{m.tema}</p>}
              <p className="mensaje__texto">{m.contenido}</p>
              {m.puedeEditar && (
                <button type="button" className="enlace mensaje__editar" onClick={() => setDialogo({ editar: m })}>Editar</button>
              )}
            </li>
          ))}
        </ol>
      )}
      {hilo && !hilo.puedeEscribir && (
        <p className="conversacion__vacio">Este proyecto no recibe mensajes en su estado actual: puedes leer lo escrito, pero no agregar nada.</p>
      )}
      {hilo && (
        <DialogoMensaje
          abierto={dialogo !== null} codigo={codigo} hilo={hilo} editar={dialogo?.editar ?? null} interlocutor={interlocutor}
          alCerrar={() => setDialogo(null)}
          alTerminar={async (mensaje) => { setDialogo(null); if (mensaje) notificar(mensaje); await cargar(); }}
        />
      )}
    </div>
  );
}

interface PropiedadesDialogo {
  abierto: boolean;
  codigo: string;
  hilo: Hilo;
  editar: Mensaje | null;
  interlocutor: string;
  alCerrar: () => void;
  /** Cierra el diálogo, avisa al usuario (si hay texto) y vuelve a pedir el hilo. */
  alTerminar: (mensaje: string) => Promise<void>;
}

function DialogoMensaje({ abierto, codigo, hilo, editar, interlocutor, alCerrar, alTerminar }: PropiedadesDialogo) {
  const [tema, setTema] = useState('');
  const [contenido, setContenido] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (abierto) {
      setTema(editar?.tema ?? '');
      setContenido(editar?.contenido ?? '');
      setErrores({});
      setGeneral(null);
    }
  }, [abierto, editar]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = contenido.trim();
    const nuevos: Record<string, string> = {};
    if (hilo.escribeConTema && !tema) nuevos.tema = 'Elige de qué trata tu mensaje.';
    if (!limpio) nuevos.contenido = 'Escribe tu mensaje.';
    else if (limpio.length < MIN_MENSAJE) nuevos.contenido = `Escribe al menos ${MIN_MENSAJE} caracteres.`;
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return;

    setEnviando(true);
    const cuerpo = { tema: hilo.escribeConTema ? tema : null, contenido: limpio };
    try {
      if (editar) await mensajesApi.editar(editar.id, cuerpo);
      else await mensajesApi.enviar(codigo, cuerpo);
      setEnviando(false);
      await alTerminar(editar ? 'Mensaje editado.' : `Mensaje enviado. ${interlocutor} te responderá aquí mismo.`);
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        // «Ya no puedes editar este mensaje» o «Este proyecto no recibe mensajes»: se cierra, se avisa y se recarga el hilo
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
      abierto={abierto} alCerrar={() => !enviando && alCerrar()} ancho icono="mensaje"
      titulo={editar ? 'Editar mensaje' : `Escribirle a ${interlocutor}`}
      texto={editar ? 'Puedes cambiarlo durante los 5 minutos siguientes a enviarlo. Quedará marcado como editado.'
        : `${interlocutor} recibe un aviso y te responde en este mismo hilo.`}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Cancelar</Boton>
          <Boton type="submit" form="formulario-mensaje" variante="acento" icono="enviar" cargando={enviando}>
            {editar ? 'Guardar cambios' : 'Enviar mensaje'}
          </Boton>
        </>
      )}
    >
      <form id="formulario-mensaje" onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        {hilo.escribeConTema && (
          <Campo etiqueta="¿De qué trata?" error={errores.tema || undefined}>
            <select className="select" value={tema} onChange={(e) => setTema(e.target.value)}>
              <option value="">Elige un tema</option>
              {hilo.temas.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </Campo>
        )}
        <Campo etiqueta="Tu mensaje" error={errores.contenido || undefined} contador={{ actual: contenido.length, maximo: MAX_MENSAJE }}>
          <textarea className="campo__control--area" rows={5} value={contenido} maxLength={MAX_MENSAJE}
                    onChange={(e) => setContenido(e.target.value)}
                    placeholder="Escríbelo como se lo dirías en persona." />
        </Campo>
      </form>
    </Dialogo>
  );
}
