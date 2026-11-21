import { useEffect, useId, useMemo, useState, type FormEvent } from 'react';

import { bloqueFecha, soloFecha } from '../../agenda/fechas';
import {
  IDIOMAS, PRESUPUESTOS, URGENCIAS, gestionCitas, type CitaGestion, type DatosCuestionario,
} from '../../agenda/gestion';
import { fechaColombiaLarga, horaDelCliente, yaOcurrio } from '../../agenda/horasDeCita';
import { VerCuestionario } from '../../agenda/VerCuestionario';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { GestionSolicitudes } from '../../componentes/GestionSolicitudes';
import { Icono } from '../../componentes/Icono';
import { Insignia, type VarianteInsignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/mis-citas.css';
import '../../styles/vistas/agenda.css';
import '../../styles/vistas/citas-reuniones.css';

const INSIGNIA: Record<CitaGestion['estado'], [VarianteInsignia, string]> = {
  PENDIENTE: ['pendiente', 'Pendiente'], CONFIRMADA: ['confirmada', 'Confirmada'],
  ATENDIDA: ['atendida', 'Atendida'], CANCELADA: ['cancelada', 'Cancelada'],
};
const primerNombre = (nombre: string) => nombre.split(' ')[0] ?? nombre;
const hora = (c: CitaGestion) => new Date(c.fechaHoraColombia).getTime();

type Grupo = 'confirmar' | 'confirmadas' | 'atender' | 'historial';

function grupoDe(c: CitaGestion, ahora: Date): Grupo {
  if (c.estado === 'PENDIENTE') return 'confirmar';
  if (c.estado === 'CONFIRMADA') return yaOcurrio(c, ahora) ? 'atender' : 'confirmadas';
  return 'historial';
}

const TITULOS: Record<Grupo, string> = {
  confirmar: 'Por confirmar', confirmadas: 'Confirmadas', atender: 'Cuestionario pendiente', historial: 'Historial',
};
const VACIOS: Partial<Record<Grupo, string>> = {
  confirmar: 'No tienes citas por confirmar.', confirmadas: 'No tienes citas confirmadas próximas.', historial: 'Aún no hay citas atendidas.',
};

/** Citas que la administración le asignó al equipo: confirmar y llenar el cuestionario (HU-10; prototipo/desarrollador/citas-reuniones.html). */
export function PaginaCitasEquipo() {
  useTituloDocumento('Citas y reuniones');
  const notificar = useNotificar();
  const [lista, setLista] = useState<CitaGestion[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [intento, setIntento] = useState(0);
  const [confirmando, setConfirmando] = useState<CitaGestion | null>(null);
  const [atendiendo, setAtendiendo] = useState<CitaGestion | null>(null);
  const [viendo, setViendo] = useState<CitaGestion | null>(null);
  const [resaltada, setResaltada] = useState<string | null>(null);

  useEffect(() => {
    let vigente = true;
    setError(null);
    gestionCitas.deMiEquipo()
      .then((datos) => vigente && setLista(datos))
      .catch((e: unknown) => vigente && setError(e instanceof ErrorApi ? e.message : 'No pudimos cargar las citas.'));
    return () => {
      vigente = false;
    };
  }, [intento]);

  const grupos = useMemo(() => {
    const ahora = new Date();
    const resultado: Record<Grupo, CitaGestion[]> = { confirmar: [], confirmadas: [], atender: [], historial: [] };
    for (const c of lista ?? []) resultado[grupoDe(c, ahora)].push(c);
    // Lo que viene, de la más cercana a la más lejana; lo que ya pasó, de la más reciente a la más antigua
    resultado.confirmar.sort((a, b) => hora(a) - hora(b));
    resultado.confirmadas.sort((a, b) => hora(a) - hora(b));
    resultado.atender.sort((a, b) => hora(b) - hora(a));
    resultado.historial.sort((a, b) => hora(b) - hora(a));
    return resultado;
  }, [lista]);

  const terminar = (cita: CitaGestion, mensaje: string) => {
    setResaltada(cita.citaId);
    notificar(mensaje);
    setIntento((n) => n + 1);
  };

  const encabezado = (
    <div className="encabezado-vista">
      <div>
        <h1 className="encabezado-vista__titulo">Citas y reuniones</h1>
        <p className="encabezado-vista__texto">Las citas que la administración le asignó a tu equipo.</p>
      </div>
    </div>
  );

  if (error && !lista) {
    return (
      <>
        {encabezado}
        <Alerta tipo="error" titulo="No pudimos cargar las citas">{error}</Alerta>
        <p><Boton variante="contorno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton></p>
      </>
    );
  }
  if (!lista) return <>{encabezado}<Cargando texto="Cargando las citas…" /></>;

  const orden: Grupo[] = ['confirmar', 'confirmadas', 'atender', 'historial'];
  const visibles = orden.filter((g) => grupos[g].length > 0 || VACIOS[g]);

  return (
    <>
      {encabezado}
      {lista.length === 0 ? (
        <EstadoVacio icono="calendario" titulo="Aún no tienes citas asignadas" texto="Cuando la administración te asigne una cita, aparecerá aquí para que la confirmes." />
      ) : (
        visibles.map((g) => (
          <section className="grupo-agenda" key={g} aria-labelledby={`titulo-${g}`}>
            <h2 className="grupo-agenda__titulo" id={`titulo-${g}`}>
              {TITULOS[g]}
              {(g === 'confirmar' || g === 'atender') && grupos[g].length > 0 && (
                <span className={`insignia insignia--${g === 'confirmar' ? 'pendiente' : 'confirmada'}`}>{grupos[g].length}</span>
              )}
            </h2>
            {grupos[g].length === 0 ? (
              <p className="grupo-agenda__vacio">{VACIOS[g]}</p>
            ) : (
              <ul className="lista-citas">
                {grupos[g].map((c) => (
                  <TarjetaCita key={c.citaId} cita={c} grupo={g} resaltada={resaltada === c.citaId}
                               alConfirmar={() => setConfirmando(c)} alAtender={() => setAtendiendo(c)} alVer={() => setViendo(c)} />
                ))}
              </ul>
            )}
          </section>
        ))
      )}

      <section className="grupo-agenda" aria-labelledby="titulo-reuniones-seguimiento">
        <h2 className="grupo-agenda__titulo" id="titulo-reuniones-seguimiento">Reuniones de seguimiento</h2>
        <p className="grupo-agenda__vacio">Las que piden los clientes de tus proyectos. Programa una de sus horas, recházala con un motivo o, después de la reunión, registra su resumen.</p>
        <GestionSolicitudes modo="equipo" />
      </section>

      {confirmando && (
        <DialogoConfirmar
          cita={confirmando}
          alCerrar={() => setConfirmando(null)}
          alTerminar={(c, mensaje) => { setConfirmando(null); terminar(c, mensaje); }}
        />
      )}
      {atendiendo && (
        <DialogoCuestionario
          cita={atendiendo}
          alCerrar={() => setAtendiendo(null)}
          alTerminar={(c, mensaje) => { setAtendiendo(null); terminar(c, mensaje); }}
        />
      )}
      <VerCuestionario cita={viendo} alCerrar={() => setViendo(null)} texto="Lo que registraste después de la reunión." />
    </>
  );
}

function TarjetaCita({ cita, grupo, resaltada, alConfirmar, alAtender, alVer }: {
  cita: CitaGestion;
  grupo: Grupo;
  resaltada: boolean;
  alConfirmar: () => void;
  alAtender: () => void;
  alVer: () => void;
}) {
  const dia = soloFecha(cita.fechaHoraColombia);
  const fecha = bloqueFecha(dia);
  const [variante, etiqueta] = INSIGNIA[cita.estado];
  const delCliente = horaDelCliente(cita);
  const virtual = cita.modalidad === 'VIRTUAL';
  const nombre = primerNombre(cita.cliente.nombre);

  let nota: string | null = null;
  if (grupo === 'atender') {
    nota = 'La reunión ya pasó. Llena el cuestionario de la cita para cerrarla: la administración lo usa para crear el proyecto y asignar el equipo.';
  } else if (cita.estado === 'CANCELADA') {
    nota = cita.motivoCancelacion ? `Motivo: ${cita.motivoCancelacion}` : 'La cita se canceló.';
  } else if (cita.notaAsignacion && cita.estado === 'PENDIENTE') {
    nota = `Nota de la administración: ${cita.notaAsignacion}`;
  } else if (cita.estado === 'PENDIENTE') {
    nota = cita.descripcionNecesidad;
  } else if (cita.estado === 'CONFIRMADA') {
    nota = cita.descripcionNecesidad;
  }

  return (
    <li
      className={[
        'tarjeta-cita', grupo === 'confirmar' && 'tarjeta-cita--aviso', (grupo === 'historial') && 'tarjeta-cita--pasada',
        resaltada && 'esta-resaltada',
      ].filter(Boolean).join(' ')}
      id={`cita-${cita.codigo}`}
    >
      <div className="tarjeta-cita__fecha" aria-hidden="true">
        <span className="tarjeta-cita__dia-semana">{fecha.diaSemana}</span>
        <span className="tarjeta-cita__dia">{fecha.dia}</span>
        <span className="tarjeta-cita__mes">{fecha.mes}</span>
      </div>
      <div>
        <div className="tarjeta-cita__cabecera">
          <h3 className="tarjeta-cita__titulo">{cita.servicio}</h3>
          <Insignia variante={variante}>{etiqueta}</Insignia>
          <span className="tarjeta-cita__codigo">{cita.codigo}</span>
        </div>
        <div className="tarjeta-cita__datos">
          <span className="tarjeta-cita__dato"><Icono nombre="persona" />{cita.cliente.nombre}</span>
          <span className="tarjeta-cita__dato"><Icono nombre="reloj" />{`${fechaColombiaLarga(cita)} (hora de Colombia)`}</span>
          <span className="tarjeta-cita__dato"><Icono nombre="ubicacion" />{virtual ? 'Virtual' : 'Presencial'}</span>
          {delCliente && <span className="tarjeta-cita__dato"><Icono nombre="informacion" />{delCliente}</span>}
        </div>
        {nota && <p className="tarjeta-cita__nota">{nota}</p>}
        {cita.notaCliente && cita.estado === 'CONFIRMADA' && <p className="tarjeta-cita__comentario">{`Nota para el cliente: ${cita.notaCliente}`}</p>}
        {cita.estado === 'CONFIRMADA' && virtual && cita.enlaceReunion && (
          <span className="tarjeta-cita__enlace"><Icono nombre="ubicacion" />{cita.enlaceReunion.replace(/^https?:\/\//, '')}</span>
        )}
      </div>
      <div className="tarjeta-cita__acciones">
        {grupo === 'confirmar' && <Boton variante="acento" tamano="pequeno" icono="exito" onClick={alConfirmar}>Confirmar</Boton>}
        {grupo === 'confirmadas' && virtual && cita.enlaceReunion && (
          <a className="boton boton--contorno boton--pequeno" href={cita.enlaceReunion} target="_blank" rel="noopener noreferrer">Abrir enlace</a>
        )}
        {grupo === 'atender' && <Boton variante="oscuro" tamano="pequeno" icono="portapapeles" onClick={alAtender}>Llenar cuestionario</Boton>}
        {cita.estado === 'ATENDIDA' && cita.tieneCuestionario && (
          <Boton variante="contorno" tamano="pequeno" icono="ojo" onClick={alVer}>Ver cuestionario</Boton>
        )}
        {cita.estado !== 'CANCELADA' && cita.estado !== 'ATENDIDA' && (
          <a className="boton boton--fantasma boton--pequeno" href={`mailto:${cita.cliente.email}`}>{`Escribir a ${nombre}`}</a>
        )}
      </div>
    </li>
  );
}

// ---------------------------------------------------------------- Confirmar (RN-12)

const URL_VALIDA = /^https?:\/\/.+\..+/;

function DialogoConfirmar({ cita, alCerrar, alTerminar }: {
  cita: CitaGestion;
  alCerrar: () => void;
  alTerminar: (c: CitaGestion, mensaje: string) => void;
}) {
  const idFormulario = useId();
  const virtual = cita.modalidad === 'VIRTUAL';
  const [enlace, setEnlace] = useState('');
  const [nota, setNota] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const { formulario, enfocar } = useEnfocarError();

  async function confirmar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    const limpio = enlace.trim();
    if (virtual) {
      if (!limpio) nuevos.enlaceReunion = 'Pega el enlace: la cita es virtual y el cliente lo necesita para unirse.';
      else if (!URL_VALIDA.test(limpio)) nuevos.enlaceReunion = 'El enlace debe empezar por https://';
      else if (limpio.length > 255) nuevos.enlaceReunion = 'Usa máximo 255 caracteres';
    }
    if (nota.length > 300) nuevos.nota = 'Usa máximo 300 caracteres';
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return enfocar();
    setGuardando(true);
    try {
      await gestionCitas.confirmar(cita.citaId, virtual ? limpio : '', nota);
      alTerminar(cita, `Cita ${cita.codigo} confirmada. ${primerNombre(cita.cliente.nombre)} recibirá un aviso${virtual ? ' con el enlace' : ''}.`);
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setErrores(porCampo);
      setGeneral(mensaje);
      setGuardando(false);
      enfocar();
    }
  }

  return (
    <Dialogo
      abierto
      alCerrar={() => !guardando && alCerrar()}
      ancho
      icono="exito"
      titulo="Confirmar cita"
      texto="El cliente recibirá un aviso con la confirmación."
      acciones={(
        <>
          <Boton variante="contorno" onClick={alCerrar} disabled={guardando}>Volver</Boton>
          <Boton type="submit" form={idFormulario} cargando={guardando}>Confirmar cita</Boton>
        </>
      )}
    >
      <form id={idFormulario} ref={formulario} onSubmit={(e) => void confirmar(e)} noValidate>
        <div className="resumen-dialogo">
          <strong>{`${cita.codigo} · ${cita.cliente.nombre}`}</strong>
          <span>{`${cita.servicio} · ${fechaColombiaLarga(cita)} · ${virtual ? 'Virtual' : 'Presencial'}`}</span>
        </div>
        {general && <Alerta tipo="error" titulo={general} />}
        {virtual && (
          <Campo etiqueta="Enlace de la videollamada" error={errores.enlaceReunion} ayuda="Se enviará al cliente junto con la confirmación.">
            <input type="url" inputMode="url" name="enlaceReunion" placeholder="https://meet.google.com/abc-defg-hij"
                   value={enlace} onChange={(e) => setEnlace(e.target.value)} />
          </Campo>
        )}
        <Campo etiqueta="Nota para el cliente" opcional error={errores.nota} contador={{ actual: nota.length, maximo: 300 }}>
          <textarea className="campo__control--area" name="nota" maxLength={300} value={nota} onChange={(e) => setNota(e.target.value)}
                    placeholder="Ejemplo: trae, si puedes, ejemplos de páginas que te gusten." />
        </Campo>
      </form>
    </Dialogo>
  );
}

// ---------------------------------------------------------------- Cuestionario (RN-24)

type ErroresCuestionario = Partial<Record<keyof DatosCuestionario, string>>;

/** Mismas reglas y mensajes que el backend (ServicioGestionCitas) y el prototipo. */
export function validarCuestionario(d: DatosCuestionario): ErroresCuestionario {
  const e: ErroresCuestionario = {};
  const resumen = d.resumen.trim();
  if (!resumen) e.resumen = 'Escribe el resumen de la reunión.';
  else if (resumen.length < 30) e.resumen = 'Escribe al menos 30 caracteres.';
  const cliente = d.descripcionCliente.trim();
  if (!cliente) e.descripcionCliente = 'Describe al cliente.';
  else if (cliente.length < 30) e.descripcionCliente = 'Escribe al menos 30 caracteres.';
  if (!d.presupuesto) e.presupuesto = 'Elige el presupuesto aproximado.';
  if (!d.idioma) e.idioma = 'Elige el idioma.';
  if (!d.urgencia) e.urgencia = 'Elige el nivel de urgencia.';
  return e;
}

const VACIO: DatosCuestionario = { resumen: '', descripcionCliente: '', presupuesto: '', fechaDeseada: '', idioma: '', urgencia: '' };

function DialogoCuestionario({ cita, alCerrar, alTerminar }: {
  cita: CitaGestion;
  alCerrar: () => void;
  alTerminar: (c: CitaGestion, mensaje: string) => void;
}) {
  const idFormulario = useId();
  const [datos, setDatos] = useState<DatosCuestionario>(VACIO);
  const [errores, setErrores] = useState<ErroresCuestionario>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const { formulario, enfocar } = useEnfocarError();
  const cambiar = <K extends keyof DatosCuestionario>(campo: K, valor: DatosCuestionario[K]) => setDatos((d) => ({ ...d, [campo]: valor }));

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const nuevos = validarCuestionario(datos);
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) {
      setGeneral('Completa el cuestionario: faltan datos.');
      return enfocar();
    }
    setGuardando(true);
    try {
      await gestionCitas.llenarCuestionario(cita.citaId, datos);
      alTerminar(cita, `Cuestionario enviado. La cita ${cita.codigo} quedó atendida.`);
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setErrores(porCampo);
      setGeneral(mensaje ?? 'Completa el cuestionario: faltan datos.');
      setGuardando(false);
      enfocar();
    }
  }

  return (
    <Dialogo
      abierto
      alCerrar={() => !guardando && alCerrar()}
      ancho
      clase="dialogo-cuestionario"
      icono="portapapeles"
      titulo="Cuestionario de la cita"
      texto="Cuéntale a la administración y a tu equipo qué pasó en la reunión. Al enviarlo, la cita queda atendida y no se puede editar."
      acciones={(
        <>
          <Boton variante="contorno" onClick={alCerrar} disabled={guardando}>Volver</Boton>
          <Boton type="submit" form={idFormulario} cargando={guardando}>Enviar y cerrar la cita</Boton>
        </>
      )}
    >
      <form id={idFormulario} ref={formulario} onSubmit={(e) => void enviar(e)} noValidate>
        <div className="resumen-dialogo">
          <strong>{`${cita.codigo} · ${cita.cliente.nombre}`}</strong>
          <span>{`${cita.servicio} · ${fechaColombiaLarga(cita)}`}</span>
        </div>
        {general && <Alerta tipo="error" titulo={general} />}
        <Campo etiqueta="Resumen de la reunión" error={errores.resumen} contador={{ actual: datos.resumen.length, maximo: 1000 }}>
          <textarea className="campo__control--area" name="resumen" rows={4} maxLength={1000} value={datos.resumen}
                    onChange={(e) => cambiar('resumen', e.target.value)}
                    placeholder="Ejemplo: Hablamos del alcance de la tienda, los medios de pago y los tiempos de entrega." />
        </Campo>
        <Campo etiqueta="Descripción del cliente" error={errores.descripcionCliente} contador={{ actual: datos.descripcionCliente.length, maximo: 600 }}>
          <textarea className="campo__control--area" name="descripcionCliente" rows={3} maxLength={600} value={datos.descripcionCliente}
                    onChange={(e) => cambiar('descripcionCliente', e.target.value)}
                    placeholder="Ejemplo: Dueña de un negocio de accesorios que hoy vende solo por redes sociales." />
        </Campo>
        <div className="fila-campos">
          <Campo etiqueta="Presupuesto aproximado" error={errores.presupuesto}>
            <select className="select" name="presupuesto" value={datos.presupuesto} onChange={(e) => cambiar('presupuesto', e.target.value as DatosCuestionario['presupuesto'])}>
              <option value="">Elige un rango (COP)</option>
              {PRESUPUESTOS.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
            </select>
          </Campo>
          <Campo etiqueta="Idioma preferido" error={errores.idioma}>
            <select className="select" name="idioma" value={datos.idioma} onChange={(e) => cambiar('idioma', e.target.value as DatosCuestionario['idioma'])}>
              <option value="">Elige un idioma</option>
              {IDIOMAS.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
            </select>
          </Campo>
        </div>
        <Campo etiqueta="Fecha deseada de entrega" opcional error={errores.fechaDeseada} ayuda="Como la dijo el cliente. Déjalo vacío si no tiene una fecha.">
          <input type="text" name="fechaDeseada" maxLength={60} value={datos.fechaDeseada}
                 onChange={(e) => cambiar('fechaDeseada', e.target.value)} placeholder="Ejemplo: antes de marzo de 2027" />
        </Campo>
        <fieldset className={['grupo-opciones campo', errores.urgencia && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">Nivel de urgencia</legend>
          <div className="urgencias">
            {URGENCIAS.map(([valor, texto, detalle]) => (
              <label className="urgencia" key={valor}>
                <input type="radio" name="urgencia" value={valor} checked={datos.urgencia === valor}
                       onChange={() => cambiar('urgencia', valor)} aria-invalid={errores.urgencia ? true : undefined} />
                <span className="urgencia__texto"><strong>{texto}</strong><small>{detalle}</small></span>
              </label>
            ))}
          </div>
          {errores.urgencia && (
            <p className="campo__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.urgencia}</span></p>
          )}
        </fieldset>
      </form>
    </Dialogo>
  );
}
