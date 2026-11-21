import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';

import { separarErrores } from '../paginas/acceso/erroresFormulario';
import {
  MAX_MOTIVO_RESPUESTA, MAX_PIDIO, MAX_RESUMEN, MIN_MOTIVO_RESPUESTA, MIN_PIDIO, MIN_RESUMEN, SIGUIENTES_PASOS,
  TEXTO_SIGUIENTE_PASO, fechaCortaReunion, fechaHoraReunion, reunionesApi, type EstadoSolicitud, type SiguientePaso, type Solicitud,
} from '../comunicacion/reuniones';
import { esConflictoDeEstado } from '../seguimiento/api';
import { Alerta } from './Alerta';
import { Boton } from './Boton';
import { Campo } from './Campo';
import { Cargando } from './Cargando';
import { Dialogo } from './Dialogo';
import { EstadoVacio } from './EstadoVacio';
import { Icono } from './Icono';
import { MenuAcciones, type OpcionMenu } from './MenuAcciones';
import { useNotificar } from './Notificaciones';
import { Tabla, type ColumnaTabla } from './Tabla';
import '../styles/vistas/admin-solicitudes.css';

type Filtro = 'todas' | 'por-responder' | 'programada' | 'atendida' | 'rechazada';
type Accion = { tipo: 'programar' | 'rechazar' | 'cancelar' | 'resumen' | 'ver'; solicitud: Solicitud } | null;

const FILTROS: [Filtro, string][] = [
  ['todas', 'Todas'], ['por-responder', 'Por responder'], ['programada', 'Programadas'], ['atendida', 'Atendidas'], ['rechazada', 'Rechazadas'],
];

const ESTADOS: Record<EstadoSolicitud, { texto: string; clase: string }> = {
  PENDIENTE: { texto: 'Por responder', clase: 'insignia--pendiente' },
  PROGRAMADA: { texto: 'Programada', clase: 'insignia--programada' },
  ATENDIDA: { texto: 'Atendida', clase: 'insignia--atendida' },
  RECHAZADA: { texto: 'Rechazada', clase: 'insignia--cancelada' },
  CANCELADA: { texto: 'Cancelada', clase: 'insignia--cancelada' },
};

function filtroDe(s: Solicitud): Filtro {
  if (s.estado === 'PENDIENTE') return 'por-responder';
  if (s.estado === 'PROGRAMADA') return 'programada';
  if (s.estado === 'ATENDIDA') return 'atendida';
  return 'rechazada';
}

const iniciales = (nombre: string) => nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();

interface Propiedades {
  /** El administrador ve todas y también cancela; el Product Owner ve las de su equipo y llena el resumen. */
  modo: 'admin' | 'equipo';
}

/**
 * Las solicitudes de reunión de seguimiento (HU-16; prototipo: admin/solicitudes.html y desarrollador/citas-reuniones.html):
 * programar eligiendo uno de los horarios del cliente, rechazar con motivo, cancelar (administrador) y, después de la
 * reunión, el resumen del Product Owner (D-42).
 */
export function GestionSolicitudes({ modo }: Propiedades) {
  const notificar = useNotificar();
  const [lista, setLista] = useState<Solicitud[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [intento, setIntento] = useState(0);
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [busqueda, setBusqueda] = useState('');
  const [responsable, setResponsable] = useState('');
  const [accion, setAccion] = useState<Accion>(null);

  const cargar = useCallback(async () => {
    try {
      setLista(await reunionesApi.listar());
      setError(null);
    } catch (e) {
      setError(separarErrores(e).general ?? 'No pudimos cargar las solicitudes.');
    }
  }, []);

  useEffect(() => {
    void cargar();
  }, [cargar, intento]);

  const responsables = useMemo(() => [...new Set((lista ?? []).map((s) => s.responsable).filter(Boolean))].sort(), [lista]);
  const cantidad = (f: Filtro) => (lista ?? []).filter((s) => f === 'todas' || filtroDe(s) === f).length;
  const visibles = (lista ?? []).filter((s) => {
    const q = busqueda.trim().toLowerCase();
    return (filtro === 'todas' || filtroDe(s) === filtro)
      && (!responsable || s.responsable === responsable)
      && (!q || `${s.cliente} ${s.proyecto} ${s.proyectoCodigo} ${s.codigo}`.toLowerCase().includes(q));
  });

  async function terminar(mensaje: string) {
    setAccion(null);
    if (mensaje) notificar(mensaje);
    await cargar();
  }

  const columnas: ColumnaTabla<Solicitud>[] = [
    {
      clave: 'solicitud', titulo: 'Solicitud', ancha: true, clase: 'celda-solicitud',
      celda: (s) => (
        <span className="celda-principal">
          <strong>{s.motivo}</strong>
          <small><span className="codigo">{s.codigo}</span> · «{s.razon}»</small>
        </span>
      ),
    },
    {
      clave: 'cliente', titulo: 'Cliente y proyecto',
      celda: (s) => (
        <span className="celda-principal">
          <strong>{s.cliente}</strong>
          <small>{s.proyecto} · {s.proyectoCodigo}</small>
          {s.responsable && <small className="celda-responsable"><span className="avatar" aria-hidden="true">{iniciales(s.responsable)}</span>{s.responsable}</small>}
        </span>
      ),
    },
    {
      clave: 'fechas', titulo: 'Fechas',
      celda: (s) => (
        <span className="celda-principal celda-fecha">
          <strong>{detalleFecha(s)}</strong>
          <small>Enviada: {fechaCortaReunion(s.enviadaAt)} · {s.modalidad === 'VIRTUAL' ? 'Virtual' : 'Presencial'}</small>
        </span>
      ),
    },
    { clave: 'estado', titulo: 'Estado', celda: (s) => <span className={`insignia ${ESTADOS[s.estado].clase}`}>{ESTADOS[s.estado].texto}</span> },
    { clave: 'acciones', titulo: 'Acciones', tituloOculto: true, celda: (s) => <Acciones solicitud={s} modo={modo} abrir={(tipo) => setAccion({ tipo, solicitud: s })} /> },
  ];

  if (error && !lista) {
    return (
      <>
        <Alerta tipo="error" titulo="No pudimos cargar las solicitudes">{error}</Alerta>
        <p><Boton variante="contorno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton></p>
      </>
    );
  }
  if (!lista) return <Cargando texto="Cargando las solicitudes…" />;

  return (
    <>
      <div className="herramientas">
        <div className="filtros" role="group" aria-label="Filtrar por estado">
          {FILTROS.map(([id, texto]) => (
            <button key={id} type="button" className="filtro" aria-pressed={filtro === id} onClick={() => setFiltro(id)}>
              {texto} <span className="filtro__cantidad">{cantidad(id)}</span>
            </button>
          ))}
        </div>
        <div className="herramientas__derecha">
          <label className="buscador">
            <span className="solo-lector">Buscar solicitud</span>
            <input className="campo__control" type="search" placeholder="Buscar cliente o proyecto" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
          </label>
          {modo === 'admin' && (
            <label>
              <span className="solo-lector">Filtrar por responsable</span>
              <select className="campo__control select" value={responsable} onChange={(e) => setResponsable(e.target.value)}>
                <option value="">Todos los responsables</option>
                {responsables.map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
            </label>
          )}
        </div>
      </div>
      <Tabla
        titulo="Solicitudes de reunión" columnas={columnas} filas={visibles} claveFila={(s) => s.id}
        claseFila={(s) => (s.estado === 'PENDIENTE' || s.estado === 'PROGRAMADA' ? undefined : 'fila--apagada')}
        vacio={(
          <EstadoVacio
            icono="calendario"
            titulo={lista.length === 0 ? 'Aún no hay solicitudes de reunión' : 'No hay solicitudes con estos filtros'}
            texto={lista.length === 0 ? 'Cuando un cliente pida una reunión de seguimiento, aparecerá aquí.' : 'Prueba con otro estado, otro responsable u otra búsqueda.'}
            accion={lista.length === 0 ? undefined : (
              <Boton variante="contorno" tamano="pequeno" onClick={() => { setFiltro('todas'); setResponsable(''); setBusqueda(''); }}>Limpiar filtros</Boton>
            )}
          />
        )}
      />
      <p className="pie-tabla" aria-live="polite">{visibles.length === 1 ? '1 resultado' : `${visibles.length} resultados`}</p>

      {accion?.tipo === 'programar' && <DialogoProgramar solicitud={accion.solicitud} alCerrar={() => setAccion(null)} alTerminar={terminar} />}
      {accion?.tipo === 'rechazar' && (
        <DialogoMotivo
          solicitud={accion.solicitud} tipo="rechazar" alCerrar={() => setAccion(null)} alTerminar={terminar}
          titulo="Rechazar solicitud" texto="El cliente verá el motivo y podrá enviar una solicitud nueva (RN-15)."
          boton="Rechazar solicitud" ayuda="Ejemplo: la duda se resolvió por correo; te enviamos el detalle."
          enviar={(id, motivo) => reunionesApi.rechazar(id, motivo)} exito="Solicitud rechazada. El cliente ya puede enviar otra."
        />
      )}
      {accion?.tipo === 'cancelar' && (
        <DialogoMotivo
          solicitud={accion.solicitud} tipo="cancelar" alCerrar={() => setAccion(null)} alTerminar={terminar}
          titulo="Cancelar reunión" texto="El cliente y el Product Owner reciben un aviso con el motivo."
          boton="Cancelar reunión" ayuda="Ejemplo: el equipo no puede asistir ese día; reprogramamos."
          enviar={(id, motivo) => reunionesApi.cancelar(id, motivo)} exito="Reunión cancelada. El cliente ya puede pedir otra."
        />
      )}
      {accion?.tipo === 'resumen' && <DialogoResumen solicitud={accion.solicitud} alCerrar={() => setAccion(null)} alTerminar={terminar} />}
      {accion?.tipo === 'ver' && <DialogoVerResumen solicitud={accion.solicitud} alCerrar={() => setAccion(null)} />}
    </>
  );
}

function detalleFecha(s: Solicitud): string {
  if (s.estado === 'PENDIENTE') return 'Sin respuesta todavía';
  if (s.estado === 'PROGRAMADA' && s.fechaProgramada) return fechaCortaReunion(s.fechaProgramada);
  if (s.estado === 'ATENDIDA' && s.fechaProgramada) return `Atendida el ${fechaCortaReunion(s.fechaProgramada)}`;
  if (s.estado === 'CANCELADA') return 'Reunión cancelada';
  return 'Respondida con un motivo';
}

function Acciones({ solicitud: s, modo, abrir }: { solicitud: Solicitud; modo: 'admin' | 'equipo'; abrir: (tipo: 'programar' | 'rechazar' | 'cancelar' | 'resumen' | 'ver') => void }) {
  if (s.estado === 'PENDIENTE') {
    const opciones: OpcionMenu[] = [{ texto: 'Rechazar solicitud', icono: 'cerrar', alElegir: () => abrir('rechazar'), peligro: true }];
    return (
      <div className="celda-acciones">
        <Boton variante="acento" tamano="pequeno" icono="calendario" onClick={() => abrir('programar')}>Programar</Boton>
        <MenuAcciones etiqueta={`Más acciones para ${s.codigo}`} opciones={opciones} />
      </div>
    );
  }
  if (s.estado === 'PROGRAMADA') {
    return (
      <div className="celda-acciones">
        {s.puedeCerrar && <Boton variante="acento" tamano="pequeno" icono="exito" onClick={() => abrir('resumen')}>Registrar resumen</Boton>}
        {modo === 'admin' && (
          <MenuAcciones etiqueta={`Más acciones para ${s.codigo}`} opciones={[{ texto: 'Cancelar reunión', icono: 'cerrar', alElegir: () => abrir('cancelar'), peligro: true }]} />
        )}
        {!s.puedeCerrar && modo !== 'admin' && <span className="celda-principal"><small>Esperando la reunión</small></span>}
      </div>
    );
  }
  if (s.estado === 'ATENDIDA' && s.resumen) {
    return <div className="celda-acciones"><Boton variante="contorno" tamano="pequeno" onClick={() => abrir('ver')}>Ver resumen</Boton></div>;
  }
  return <div className="celda-acciones"><span className="celda-principal"><small>Cerrada</small></span></div>;
}

interface PropiedadesDialogoBase {
  solicitud: Solicitud;
  alCerrar: () => void;
  alTerminar: (mensaje: string) => Promise<void>;
}

function ResumenDelDialogo({ s }: { s: Solicitud }) {
  return (
    <dl className="resumen-dialogo">
      <div><dt>Cliente</dt><dd>{s.cliente} · {s.proyecto} ({s.proyectoCodigo})</dd></div>
      <div><dt>Motivo</dt><dd>{s.motivo} · {s.modalidad === 'VIRTUAL' ? 'Virtual' : 'Presencial'}</dd></div>
      <div><dt>Lo que cuenta el cliente</dt><dd>«{s.razon}»</dd></div>
    </dl>
  );
}

function DialogoProgramar({ solicitud: s, alCerrar, alTerminar }: PropiedadesDialogoBase) {
  const [opcion, setOpcion] = useState(0);
  const [enlace, setEnlace] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const virtual = s.modalidad === 'VIRTUAL';

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    if (!opcion) nuevos.horario = 'Elige uno de los horarios propuestos.';
    if (virtual) {
      if (!enlace.trim()) nuevos.enlace = 'Pega el enlace: la reunión es virtual.';
      else if (!/^https?:\/\/\S+\.\S+$/.test(enlace.trim())) nuevos.enlace = 'El enlace debe empezar por https://';
    }
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return;

    setEnviando(true);
    try {
      const r = await reunionesApi.programar(s.id, opcion, enlace.trim());
      setEnviando(false);
      await alTerminar(`Reunión programada para el ${r.fechaProgramada ? fechaHoraReunion(r.fechaProgramada) : 'horario elegido'}. El cliente recibe un aviso.`);
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        // «Ese horario ya no está disponible…» o la solicitud ya cambió: se avisa y se vuelve a pedir la lista
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
      abierto alCerrar={() => !enviando && alCerrar()} ancho icono="calendario" titulo="Programar reunión"
      texto="Elige uno de los horarios que propuso el cliente. Él recibe un aviso con la fecha."
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form="formulario-programar" variante="acento" cargando={enviando}>Programar reunión</Boton>
        </>
      )}
    >
      <form id="formulario-programar" onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <ResumenDelDialogo s={s} />
        <fieldset className={['grupo-opciones', 'campo', errores.horario && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">Horarios que sugirió el cliente</legend>
          {s.opciones.map((o, i) => (
            <label className="tipo-cambio__opcion" key={o}>
              <input type="radio" name="horario" value={i + 1} checked={opcion === i + 1} onChange={() => setOpcion(i + 1)} />
              <span><strong>{fechaHoraReunion(o)}</strong><small>Hora de Colombia</small></span>
            </label>
          ))}
          {errores.horario && (
            <p className="campo__mensaje grupo-opciones__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.horario}</span></p>
          )}
        </fieldset>
        {virtual ? (
          <Campo etiqueta="Enlace de la videollamada" error={errores.enlace || undefined}>
            <input type="url" value={enlace} onChange={(e) => setEnlace(e.target.value)} placeholder="https://meet.google.com/abc-defg-hij" />
          </Campo>
        ) : (
          <p className="nota-presencial"><Icono nombre="carpeta" />Presencial: en la oficina de CoreTech, Calle 10 # 4-25, oficina 302.</p>
        )}
      </form>
    </Dialogo>
  );
}

interface PropiedadesMotivo extends PropiedadesDialogoBase {
  tipo: 'rechazar' | 'cancelar';
  titulo: string;
  texto: string;
  boton: string;
  ayuda: string;
  exito: string;
  enviar: (id: string, motivo: string) => Promise<Solicitud>;
}

function DialogoMotivo({ solicitud: s, tipo, titulo, texto, boton, ayuda, exito, enviar, alCerrar, alTerminar }: PropiedadesMotivo) {
  const [motivo, setMotivo] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function confirmar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = motivo.trim();
    const nuevos: Record<string, string> = {};
    if (!limpio) nuevos.motivo = 'Escribe el motivo: el cliente lo verá.';
    else if (limpio.length < MIN_MOTIVO_RESPUESTA) nuevos.motivo = `Escribe al menos ${MIN_MOTIVO_RESPUESTA} caracteres.`;
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return;

    setEnviando(true);
    try {
      await enviar(s.id, limpio);
      setEnviando(false);
      await alTerminar(exito);
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

  const id = `formulario-${tipo}`;
  return (
    <Dialogo
      abierto alCerrar={() => !enviando && alCerrar()} ancho icono="cerrar" tono="peligro" titulo={titulo} texto={texto}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form={id} variante="peligro" cargando={enviando}>{boton}</Boton>
        </>
      )}
    >
      <form id={id} onSubmit={(e) => void confirmar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <ResumenDelDialogo s={s} />
        <Campo etiqueta="Motivo" error={errores.motivo || undefined} ayuda={ayuda} contador={{ actual: motivo.length, maximo: MAX_MOTIVO_RESPUESTA }}>
          <textarea className="campo__control--area" rows={3} value={motivo} maxLength={MAX_MOTIVO_RESPUESTA} onChange={(e) => setMotivo(e.target.value)} />
        </Campo>
      </form>
    </Dialogo>
  );
}

function DialogoResumen({ solicitud: s, alCerrar, alTerminar }: PropiedadesDialogoBase) {
  const [resumen, setResumen] = useState('');
  const [quePidio, setQuePidio] = useState('');
  const [siguiente, setSiguiente] = useState<SiguientePaso | ''>('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    if (!resumen.trim()) nuevos.resumen = 'Cuenta qué se habló en la reunión.';
    else if (resumen.trim().length < MIN_RESUMEN) nuevos.resumen = `Escribe al menos ${MIN_RESUMEN} caracteres.`;
    if (!quePidio.trim()) nuevos.quePidio = 'Escribe qué pidió el cliente.';
    else if (quePidio.trim().length < MIN_PIDIO) nuevos.quePidio = `Escribe al menos ${MIN_PIDIO} caracteres.`;
    if (!siguiente) nuevos.siguientePaso = 'Elige cuál es el siguiente paso.';
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return;

    setEnviando(true);
    try {
      await reunionesApi.resumen(s.id, { resumen: resumen.trim(), quePidio: quePidio.trim(), siguientePaso: siguiente });
      setEnviando(false);
      await alTerminar('Resumen guardado. La reunión quedó atendida y el cliente puede leerlo.');
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
      abierto alCerrar={() => !enviando && alCerrar()} ancho icono="exito" tono="exito" titulo="Resumen de la reunión"
      texto="Cuenta qué se habló. Al enviarlo la reunión queda atendida y el resumen ya no se puede editar."
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form="formulario-resumen" variante="exito" cargando={enviando}>Guardar resumen</Boton>
        </>
      )}
    >
      <form id="formulario-resumen" onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <ResumenDelDialogo s={s} />
        <Campo etiqueta="¿Qué se habló?" error={errores.resumen || undefined} contador={{ actual: resumen.length, maximo: MAX_RESUMEN }}>
          <textarea className="campo__control--area" rows={4} value={resumen} maxLength={MAX_RESUMEN} onChange={(e) => setResumen(e.target.value)} />
        </Campo>
        <Campo etiqueta="¿Qué pidió el cliente?" error={errores.quePidio || undefined} contador={{ actual: quePidio.length, maximo: MAX_PIDIO }}>
          <textarea className="campo__control--area" rows={3} value={quePidio} maxLength={MAX_PIDIO} onChange={(e) => setQuePidio(e.target.value)} />
        </Campo>
        <Campo etiqueta="¿Qué sigue?" error={errores.siguientePaso || undefined}>
          <select className="select" value={siguiente} onChange={(e) => setSiguiente(e.target.value as SiguientePaso | '')}>
            <option value="">Elige el siguiente paso</option>
            {SIGUIENTES_PASOS.map((p) => <option key={p.valor} value={p.valor}>{p.texto}</option>)}
          </select>
        </Campo>
      </form>
    </Dialogo>
  );
}

function DialogoVerResumen({ solicitud: s, alCerrar }: { solicitud: Solicitud; alCerrar: () => void }) {
  return (
    <Dialogo
      abierto alCerrar={alCerrar} ancho icono="exito" tono="exito" titulo={`Resumen de ${s.codigo}`}
      texto={s.fechaProgramada ? `Reunión del ${fechaHoraReunion(s.fechaProgramada)}` : undefined}
      acciones={<Boton variante="contorno" onClick={alCerrar}>Cerrar</Boton>}
    >
      <ResumenDelDialogo s={s} />
      {s.resumen && (
        <dl className="resumen-dialogo">
          <div><dt>Qué se habló</dt><dd>{s.resumen.resumen}</dd></div>
          <div><dt>Qué pidió el cliente</dt><dd>{s.resumen.quePidio}</dd></div>
          <div><dt>Qué sigue</dt><dd>{TEXTO_SIGUIENTE_PASO[s.resumen.siguientePaso]}</dd></div>
        </dl>
      )}
    </Dialogo>
  );
}
