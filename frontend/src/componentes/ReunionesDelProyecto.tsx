import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';

import { separarErrores } from '../paginas/acceso/erroresFormulario';
import {
  HORAS_DE_INICIO, MAX_RAZON, MIN_RAZON, TEXTO_SIGUIENTE_PASO, etiquetaHora, fechaHoraReunion, instanteDeColombia,
  proximosDiasHabiles, reunionesApi, type Modalidad, type Solicitud, type SolicitudesDelProyecto,
} from '../comunicacion/reuniones';
import { esConflictoDeEstado } from '../seguimiento/api';
import { Alerta } from './Alerta';
import { Boton } from './Boton';
import { Campo } from './Campo';
import { Dialogo } from './Dialogo';
import { Icono } from './Icono';
import { useNotificar } from './Notificaciones';

const ESTADOS: Record<Solicitud['estado'], { texto: string; clase: string }> = {
  PENDIENTE: { texto: 'Por responder', clase: 'insignia--pendiente' },
  PROGRAMADA: { texto: 'Programada', clase: 'insignia--programada' },
  ATENDIDA: { texto: 'Atendida', clase: 'insignia--atendida' },
  RECHAZADA: { texto: 'Rechazada', clase: 'insignia--cancelada' },
  CANCELADA: { texto: 'Cancelada', clase: 'insignia--cancelada' },
};

interface Propiedades {
  codigo: string;
  /** El nombre de pila del desarrollador (Product Owner), para los textos. */
  interlocutor: string;
  /** Cambia cuando el padre recargó el proyecto: se vuelve a pedir la lista. */
  version?: number;
}

/**
 * Las reuniones de seguimiento del cliente (HU-15; prototipo: .solicitar-reunion de cliente/proyecto-tienda-en-linea.html):
 * pedir una proponiendo uno o dos horarios, ver su respuesta y, cuando ya se atendió, el resumen. Solo con el proyecto
 * activo y el plan aprobado (RN-14) y una a la vez (RN-15): si no se puede, el botón no aparece.
 */
export function ReunionesDelProyecto({ codigo, interlocutor, version = 0 }: Propiedades) {
  const notificar = useNotificar();
  const [datos, setDatos] = useState<SolicitudesDelProyecto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [abierto, setAbierto] = useState(false);

  const cargar = useCallback(async () => {
    try {
      setDatos(await reunionesApi.deProyecto(codigo));
      setError(null);
    } catch (e) {
      setError(separarErrores(e).general ?? 'No pudimos cargar tus reuniones.');
    }
  }, [codigo]);

  useEffect(() => {
    void cargar();
  }, [cargar, version]);

  return (
    <section className="tarjeta aparecer solicitar-reunion" aria-labelledby="titulo-reuniones">
      <h2 className="tarjeta__titulo" id="titulo-reuniones">Reuniones de seguimiento</h2>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {datos && datos.solicitudes.length === 0 && (
        <p className="solicitar-reunion__texto">
          {datos.puedeSolicitar
            ? `¿Quieres revisar algo con ${interlocutor}? Pide una reunión y propón uno o dos horarios.`
            : 'Podrás pedir reuniones de seguimiento cuando apruebes el plan de trabajo.'}
        </p>
      )}
      {datos && datos.solicitudes.length > 0 && (
        <ul className="lista-reuniones">
          {datos.solicitudes.map((s) => <ReunionDelCliente key={s.id} solicitud={s} interlocutor={interlocutor} />)}
        </ul>
      )}
      {datos?.puedeSolicitar && (
        <div className="solicitar-reunion__acciones">
          <Boton variante="contorno" icono="calendario" onClick={() => setAbierto(true)}>Solicitar reunión</Boton>
        </div>
      )}
      {datos && (
        <DialogoSolicitud
          abierto={abierto} codigo={codigo} motivos={datos.motivos} interlocutor={interlocutor} alCerrar={() => setAbierto(false)}
          alTerminar={async (mensaje) => { setAbierto(false); notificar(mensaje); await cargar(); }}
        />
      )}
    </section>
  );
}

function ReunionDelCliente({ solicitud: s, interlocutor }: { solicitud: Solicitud; interlocutor: string }) {
  const estado = ESTADOS[s.estado];
  return (
    <li className="reunion">
      <p className="reunion__cabecera">
        <strong>{s.motivo}</strong>
        <span className={`insignia ${estado.clase}`}>{estado.texto}</span>
      </p>
      <p className="reunion__detalle">
        <Icono nombre={s.modalidad === 'VIRTUAL' ? 'mensaje' : 'calendario'} />
        {s.modalidad === 'VIRTUAL' ? 'Virtual' : 'Presencial'} · {s.fase}
      </p>
      {s.estado === 'PENDIENTE' && (
        <p className="reunion__detalle">
          {interlocutor} aún no responde. Propusiste: {s.opciones.map(fechaHoraReunion).join(' o ')}.
        </p>
      )}
      {(s.estado === 'PROGRAMADA' || s.estado === 'ATENDIDA') && s.fechaProgramada && (
        <p className="reunion__detalle">
          <Icono nombre="reloj" />{fechaHoraReunion(s.fechaProgramada)} (hora de Colombia)
        </p>
      )}
      {s.estado === 'PROGRAMADA' && s.modalidad === 'VIRTUAL' && s.enlace && (
        <p className="reunion__detalle">
          <a className="enlace" href={s.enlace} target="_blank" rel="noopener noreferrer">Entrar a la videollamada</a>
        </p>
      )}
      {s.estado === 'PROGRAMADA' && s.modalidad === 'PRESENCIAL' && (
        <p className="reunion__detalle">En la oficina de CoreTech, Calle 10 # 4-25, oficina 302.</p>
      )}
      {(s.estado === 'RECHAZADA' || s.estado === 'CANCELADA') && s.motivoRechazo && (
        <p className="reunion__detalle">{s.estado === 'RECHAZADA' ? 'Motivo' : 'Se canceló porque'}: {s.motivoRechazo}</p>
      )}
      {s.resumen && (
        <div className="reunion__resumen">
          <p className="reunion__resumen-titulo">Lo que quedó de la reunión</p>
          <p>{s.resumen.resumen}</p>
          <p><strong>Lo que pediste:</strong> {s.resumen.quePidio}</p>
          <p><strong>Lo que sigue:</strong> {TEXTO_SIGUIENTE_PASO[s.resumen.siguientePaso]}</p>
        </div>
      )}
    </li>
  );
}

interface PropiedadesDialogo {
  abierto: boolean;
  codigo: string;
  motivos: string[];
  interlocutor: string;
  alCerrar: () => void;
  alTerminar: (mensaje: string) => Promise<void>;
}

function DialogoSolicitud({ abierto, codigo, motivos, interlocutor, alCerrar, alTerminar }: PropiedadesDialogo) {
  const dias = useMemo(() => proximosDiasHabiles(), []);
  const [motivo, setMotivo] = useState('');
  const [razon, setRazon] = useState('');
  const [modalidad, setModalidad] = useState<Modalidad | ''>('');
  const [dia1, setDia1] = useState('');
  const [hora1, setHora1] = useState('');
  const [dia2, setDia2] = useState('');
  const [hora2, setHora2] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (abierto) {
      setMotivo(''); setRazon(''); setModalidad(''); setDia1(''); setHora1(''); setDia2(''); setHora2('');
      setErrores({});
      setGeneral(null);
    }
  }, [abierto]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = razon.trim();
    const nuevos: Record<string, string> = {};
    if (!motivo) nuevos.motivo = 'Elige el motivo de la reunión.';
    if (!limpio) nuevos.razon = 'Cuéntanos por qué quieres la reunión.';
    else if (limpio.length < MIN_RAZON) nuevos.razon = `Escribe al menos ${MIN_RAZON} caracteres.`;
    if (!modalidad) nuevos.modalidad = 'Elige cómo quieres reunirte.';
    if (!dia1 || !hora1) nuevos.opcion1 = 'Elige el día y la hora de la opción 1.';
    if ((dia2 && !hora2) || (!dia2 && hora2)) nuevos.opcion2 = 'Elige el día y la hora de la opción 2, o deja los dos vacíos.';
    else if (dia2 && hora2 && dia2 === dia1 && hora2 === hora1) nuevos.opcion2 = 'Las dos opciones deben ser distintas';
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0 || !modalidad) return;

    setEnviando(true);
    try {
      await reunionesApi.solicitar(codigo, {
        motivo, razon: limpio, modalidad,
        opcion1: instanteDeColombia(dia1, Number(hora1)),
        opcion2: dia2 && hora2 ? instanteDeColombia(dia2, Number(hora2)) : null,
      });
      setEnviando(false);
      await alTerminar(`Solicitud enviada. ${interlocutor} te responderá con el horario elegido.`);
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

  const horas = (valor: string, cambiar: (v: string) => void, etiqueta: string) => (
    <select className="campo__control select" value={valor} onChange={(e) => cambiar(e.target.value)} aria-label={etiqueta}>
      <option value="">Elige una hora</option>
      {HORAS_DE_INICIO.map((h) => <option key={h} value={h}>{etiquetaHora(h)}</option>)}
    </select>
  );
  const diasSelect = (valor: string, cambiar: (v: string) => void, etiqueta: string) => (
    <select className="campo__control select" value={valor} onChange={(e) => cambiar(e.target.value)} aria-label={etiqueta}>
      <option value="">Elige un día</option>
      {dias.map((d) => <option key={d.valor} value={d.valor}>{d.texto}</option>)}
    </select>
  );

  return (
    <Dialogo
      abierto={abierto} alCerrar={() => !enviando && alCerrar()} ancho icono="calendario" titulo="Solicitar una reunión"
      texto={`Cuéntale a ${interlocutor} para qué la quieres y propón uno o dos horarios. Las horas son de Colombia.`}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Cancelar</Boton>
          <Boton type="submit" form="formulario-reunion" variante="acento" icono="enviar" cargando={enviando}>Enviar solicitud</Boton>
        </>
      )}
    >
      <form id="formulario-reunion" onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <Campo etiqueta="Motivo" error={errores.motivo || undefined}>
          <select className="select" value={motivo} onChange={(e) => setMotivo(e.target.value)}>
            <option value="">Elige un motivo</option>
            {motivos.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
        </Campo>
        <Campo etiqueta="¿Qué quieres hablar?" error={errores.razon || undefined} contador={{ actual: razon.length, maximo: MAX_RAZON }}>
          <textarea className="campo__control--area" rows={4} value={razon} maxLength={MAX_RAZON} onChange={(e) => setRazon(e.target.value)}
                    placeholder="Ejemplo: quiero revisar el flujo de pago antes de que pase a Pruebas." />
        </Campo>
        <fieldset className={['grupo-opciones', 'campo', errores.modalidad && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">¿Cómo prefieres reunirte?</legend>
          <label className="tipo-cambio__opcion">
            <input type="radio" name="modalidad" value="VIRTUAL" checked={modalidad === 'VIRTUAL'} onChange={() => setModalidad('VIRTUAL')} />
            <span><strong>Virtual</strong><small>Por videollamada; te enviamos el enlace.</small></span>
          </label>
          <label className="tipo-cambio__opcion">
            <input type="radio" name="modalidad" value="PRESENCIAL" checked={modalidad === 'PRESENCIAL'} onChange={() => setModalidad('PRESENCIAL')} />
            <span><strong>Presencial</strong><small>En la oficina de CoreTech, Calle 10 # 4-25, oficina 302.</small></span>
          </label>
          {errores.modalidad && (
            <p className="campo__mensaje grupo-opciones__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.modalidad}</span></p>
          )}
        </fieldset>
        <fieldset className={['grupo-opciones', 'campo', errores.opcion1 && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">Opción 1</legend>
          <div className="opcion-horario">{diasSelect(dia1, setDia1, 'Día de la opción 1')}{horas(hora1, setHora1, 'Hora de la opción 1')}</div>
          {errores.opcion1 && (
            <p className="campo__mensaje grupo-opciones__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.opcion1}</span></p>
          )}
        </fieldset>
        <fieldset className={['grupo-opciones', 'campo', errores.opcion2 && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">Opción 2 <span className="campo__opcional">(opcional)</span></legend>
          <div className="opcion-horario">{diasSelect(dia2, setDia2, 'Día de la opción 2')}{horas(hora2, setHora2, 'Hora de la opción 2')}</div>
          {errores.opcion2 && (
            <p className="campo__mensaje grupo-opciones__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.opcion2}</span></p>
          )}
        </fieldset>
      </form>
    </Dialogo>
  );
}
