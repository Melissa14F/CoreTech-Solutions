import { useCallback, useEffect, useId, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import type { EstadoCita } from '../../agenda/api';
import { gestionCitas, type CitaGestion, type EquipoParaAsignar, type ListaCitas } from '../../agenda/gestion';
import { fechaColombia, horaDelCliente, yaOcurrio } from '../../agenda/horasDeCita';
import { VerCuestionario } from '../../agenda/VerCuestionario';
import { iniciales } from '../../administracion/formato';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { Icono } from '../../componentes/Icono';
import { Insignia, type VarianteInsignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { Paginacion } from '../../componentes/Paginacion';
import { Tabla, type ColumnaTabla } from '../../componentes/Tabla';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-usuarios.css';

type Filtro = 'todas' | 'sin-asignar' | EstadoCita;

const FILTROS: [Filtro, string][] = [
  ['todas', 'Todas'], ['sin-asignar', 'Sin asignar'], ['PENDIENTE', 'Pendientes'], ['CONFIRMADA', 'Confirmadas'],
  ['ATENDIDA', 'Atendidas'], ['CANCELADA', 'Canceladas'],
];
const INSIGNIA: Record<EstadoCita, [VarianteInsignia, string]> = {
  PENDIENTE: ['pendiente', 'Pendiente'], CONFIRMADA: ['confirmada', 'Confirmada'],
  ATENDIDA: ['atendida', 'Atendida'], CANCELADA: ['cancelada', 'Cancelada'],
};

const paraAsignar = (c: CitaGestion) => c.estado === 'PENDIENTE' || c.estado === 'CONFIRMADA';

/** Citas del administrador: asignar el equipo, cancelar con motivo y ver el cuestionario (HU-09; prototipo/admin/citas.html). */
export function PaginaCitasAdmin() {
  useTituloDocumento('Citas');
  const notificar = useNotificar();
  const [parametros] = useSearchParams();
  const [lista, setLista] = useState<ListaCitas | null>(null);
  const [equipos, setEquipos] = useState<EquipoParaAsignar[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [equipoId, setEquipoId] = useState('');
  const [busqueda, setBusqueda] = useState(parametros.get('buscar') ?? '');
  // La búsqueda se pide al servidor cuando se deja de escribir; así no hay una consulta por tecla
  const [consulta, setConsulta] = useState(busqueda);
  const [pagina, setPagina] = useState(0);
  const [intento, setIntento] = useState(0);
  const [asignando, setAsignando] = useState<CitaGestion | null>(null);
  const [cancelando, setCancelando] = useState<CitaGestion | null>(null);
  const [viendo, setViendo] = useState<CitaGestion | null>(null);
  const [resaltada, setResaltada] = useState<string | null>(null);

  const cargar = useCallback(() => {
    let vigente = true;
    setError(null);
    gestionCitas.todas({
      estado: filtro !== 'todas' && filtro !== 'sin-asignar' ? filtro : undefined,
      sinEquipo: filtro === 'sin-asignar',
      equipoId: equipoId || undefined,
      q: consulta,
      pagina,
    })
      .then((datos) => vigente && setLista(datos))
      .catch((e: unknown) => vigente && setError(e instanceof ErrorApi ? e.message : 'No pudimos cargar las citas.'));
    return () => {
      vigente = false;
    };
  }, [filtro, equipoId, consulta, pagina]);

  useEffect(() => cargar(), [cargar, intento]);
  useEffect(() => {
    const espera = setTimeout(() => { setConsulta(busqueda); setPagina(0); }, 300);
    return () => clearTimeout(espera);
  }, [busqueda]);
  useEffect(() => {
    gestionCitas.equiposDisponibles().then(setEquipos).catch(() => setEquipos([]));
  }, [intento]);


  const recargarTrasCambio = (cita: CitaGestion, mensaje: string) => {
    setResaltada(cita.citaId);
    notificar(mensaje);
    setIntento((n) => n + 1);
  };

  const cantidad = (f: Filtro) => (f === 'todas' ? lista?.total : f === 'sin-asignar' ? lista?.sinEquipo : lista?.porEstado[f]) ?? 0;

  const columnas: ColumnaTabla<CitaGestion>[] = [
    {
      clave: 'cita', titulo: 'Cita',
      celda: (c) => <span className="celda-principal"><strong className="codigo">{c.codigo}</strong><small>{c.servicio}</small></span>,
    },
    {
      clave: 'cliente', titulo: 'Cliente',
      celda: (c) => <span className="celda-principal"><strong>{c.cliente.nombre}</strong><small>{c.cliente.email}</small></span>,
    },
    {
      clave: 'fecha', titulo: 'Fecha (hora de Colombia)',
      celda: (c) => {
        const delCliente = horaDelCliente(c);
        return (
          <>
            <span className="celda-principal celda-fecha">
              <strong>{fechaColombia(c)}</strong>
              <small><span className="celda-modalidad">{c.modalidad === 'VIRTUAL' ? 'Virtual' : 'Presencial'}</span></small>
            </span>
            {delCliente && <span className="celda-nota">{delCliente}</span>}
          </>
        );
      },
    },
    {
      clave: 'equipo', titulo: 'Equipo',
      celda: (c) => {
        if (c.equipo) {
          return (
            <span className="celda-persona">
              <span className="avatar" aria-hidden="true">{iniciales(c.equipo.productOwner)}</span>
              <span className="celda-principal">
                <strong>{`Equipo de ${c.equipo.productOwner}`}</strong>
                <small>{`${c.equipo.integrantes} ${c.equipo.integrantes === 1 ? 'persona' : 'personas'}`}</small>
              </span>
            </span>
          );
        }
        return c.estado === 'CANCELADA'
          ? <span className="celda-principal"><small>No se asignó</small></span>
          : <Insignia variante="sin-asignar">Sin asignar</Insignia>;
      },
    },
    {
      clave: 'estado', titulo: 'Estado',
      celda: (c) => <Insignia variante={INSIGNIA[c.estado][0]}>{INSIGNIA[c.estado][1]}</Insignia>,
    },
    {
      clave: 'acciones', titulo: 'Acciones', tituloOculto: true,
      celda: (c) => (
        <div className="celda-acciones">
          {paraAsignar(c) && (
            <>
              <Boton variante={c.equipo ? 'contorno' : 'acento'} tamano="pequeno" icono={c.equipo ? 'usuarios' : 'usuarioMas'} onClick={() => setAsignando(c)}>
                {c.equipo ? 'Reasignar' : 'Asignar'}
              </Boton>
              <button type="button" className="boton-icono boton-icono--pequeno" onClick={() => setCancelando(c)}
                      aria-label={`Cancelar la cita ${c.codigo}`} title="Cancelar cita">
                <Icono nombre="cerrar" />
              </button>
            </>
          )}
          {c.estado === 'CONFIRMADA' && c.equipo?.emailProductOwner && yaOcurrio(c) && (
            <a className="boton boton--contorno boton--pequeno" href={`mailto:${c.equipo.emailProductOwner}?subject=${encodeURIComponent(`Cita ${c.codigo} sin cerrar`)}`}
               aria-label={`Recordar a ${c.equipo.productOwner} que llene el cuestionario de ${c.codigo}`}>Recordar</a>
          )}
          {c.estado === 'ATENDIDA' && (
            <>
              <Link className="boton boton--acento boton--pequeno" to={`/admin/proyectos/nuevo?cita=${c.codigo}`}>Crear proyecto</Link>
              <button type="button" className="boton-icono boton-icono--pequeno" onClick={() => setViendo(c)}
                      aria-label={`Ver el cuestionario de la cita ${c.codigo}`} title="Ver cuestionario">
                <Icono nombre="ojo" />
              </button>
            </>
          )}
          {c.estado === 'CANCELADA' && (
            <span className="celda-principal">
              <small>{c.canceladaPor === 'CLIENTE' ? 'Canceló el cliente' : c.canceladaPor === 'ADMINISTRACION' ? 'Cancelada por la administración' : 'Cancelada'}</small>
              {c.motivoCancelacion && <small>{`Motivo: ${c.motivoCancelacion}`}</small>}
            </span>
          )}
        </div>
      ),
    },
  ];

  const filtros = (
    <div className="herramientas">
      <div className="filtros" role="group" aria-label="Filtrar por estado">
        {FILTROS.map(([id, texto]) => (
          <button key={id} type="button" className="filtro" aria-pressed={filtro === id} onClick={() => { setFiltro(id); setPagina(0); }}>
            {texto} <span className="filtro__cantidad">{cantidad(id)}</span>
          </button>
        ))}
      </div>
      <div className="herramientas__derecha">
        <label className="buscador">
          <span className="solo-lector">Buscar cita</span>
          <input className="campo__control" type="search" placeholder="Buscar cliente o código" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </label>
        <label>
          <span className="solo-lector">Filtrar por equipo</span>
          <select className="campo__control select" value={equipoId} onChange={(e) => { setEquipoId(e.target.value); setPagina(0); }}>
            <option value="">Todos los equipos</option>
            {equipos.map((e) => <option key={e.equipoId} value={e.equipoId}>{e.productOwner}</option>)}
          </select>
        </label>
      </div>
    </div>
  );

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Citas</h1>
          <p className="encabezado-vista__texto">
            Todas las citas de los clientes. Asigna cada cita nueva a un equipo: su desarrollador (Product Owner) la confirma y se comunica con el cliente.
          </p>
        </div>
      </div>

      {error && !lista ? (
        <>
          <Alerta tipo="error" titulo="No pudimos cargar las citas">{error}</Alerta>
          <p><Boton variante="contorno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton></p>
        </>
      ) : !lista ? (
        <Cargando texto="Cargando las citas…" />
      ) : (
        <>
          {filtros}
          <Tabla
            titulo="Citas de los clientes"
            columnas={columnas}
            filas={lista.citas}
            claveFila={(c) => c.citaId}
            claseFila={(c) => [c.estado === 'CANCELADA' && 'fila--apagada', !c.equipo && paraAsignar(c) && 'fila--atencion', resaltada === c.citaId && 'esta-resaltada'].filter(Boolean).join(' ')}
            vacio={(
              <EstadoVacio
                titulo="No hay citas con estos filtros"
                texto="Prueba con otro estado o busca por el código o el nombre del cliente."
                accion={<Boton variante="contorno" tamano="pequeno" onClick={() => { setFiltro('todas'); setEquipoId(''); setBusqueda(''); setConsulta(''); setPagina(0); }}>Limpiar filtros</Boton>}
              />
            )}
          />
          <Paginacion pagina={lista.pagina} totalPaginas={lista.totalPaginas} alCambiar={setPagina} etiqueta="Páginas de las citas" />
        </>
      )}

      {asignando && (
        <DialogoAsignar
          cita={asignando}
          equipos={equipos}
          alCerrar={() => setAsignando(null)}
          alTerminar={(c, mensaje) => { setAsignando(null); recargarTrasCambio(c, mensaje); }}
        />
      )}
      {cancelando && (
        <DialogoCancelar
          cita={cancelando}
          alCerrar={() => setCancelando(null)}
          alTerminar={(c, mensaje) => { setCancelando(null); recargarTrasCambio(c, mensaje); }}
        />
      )}
      <VerCuestionario cita={viendo} alCerrar={() => setViendo(null)} texto="Lo que registró el Product Owner después de la reunión." />
    </>
  );
}

function Resumen({ cita }: { cita: CitaGestion }) {
  return (
    <div className="resumen-dialogo">
      <strong>{`${cita.codigo} · ${cita.cliente.nombre}`}</strong>
      <span>{`${cita.servicio} · ${fechaColombia(cita)} · ${cita.modalidad === 'VIRTUAL' ? 'Virtual' : 'Presencial'}`}</span>
    </div>
  );
}

function DialogoAsignar({ cita, equipos, alCerrar, alTerminar }: {
  cita: CitaGestion;
  equipos: EquipoParaAsignar[];
  alCerrar: () => void;
  alTerminar: (c: CitaGestion, mensaje: string) => void;
}) {
  const idFormulario = useId();
  const [equipoId, setEquipoId] = useState(cita.equipo?.equipoId ?? '');
  const [nota, setNota] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const { formulario, enfocar } = useEnfocarError();
  const reasignando = cita.equipo !== null;

  async function guardar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    if (!equipoId) nuevos.equipoId = 'Elige quién atenderá la cita.';
    if (nota.length > 300) nuevos.nota = 'Usa máximo 300 caracteres';
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return enfocar();
    setGuardando(true);
    try {
      const elegido = equipos.find((e) => e.equipoId === equipoId);
      const actualizada = await gestionCitas.asignar(cita.citaId, equipoId, nota);
      const nombre = elegido ? `equipo de ${elegido.productOwner}` : 'equipo';
      alTerminar(actualizada, reasignando
        ? `${cita.codigo} pasó al ${nombre}. Les avisamos.`
        : `${cita.codigo} asignada al ${nombre}. Le enviamos un correo.`);
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
      icono="usuarioMas"
      titulo={reasignando ? 'Reasignar equipo' : 'Asignar equipo'}
      texto="Le llegará un aviso para confirmar la cita con el cliente."
      acciones={(
        <>
          <Boton variante="contorno" onClick={alCerrar} disabled={guardando}>Volver</Boton>
          <Boton type="submit" form={idFormulario} cargando={guardando}>Guardar asignación</Boton>
        </>
      )}
    >
      <form id={idFormulario} ref={formulario} onSubmit={(e) => void guardar(e)} noValidate>
        <Resumen cita={cita} />
        {general && <Alerta tipo="error" titulo={general} />}
        <fieldset className={['grupo-opciones campo', errores.equipoId && 'campo--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">Equipo, por su Product Owner <span className="campo__opcional">(de menor a mayor carga)</span></legend>
          <div className="opciones-persona">
            {equipos.map((e) => {
              const detalle = `Equipo de ${e.integrantes} · ${e.proyectosActivos} de ${e.maxProyectos} proyectos · ${e.citasProximas} ${e.citasProximas === 1 ? 'cita próxima' : 'citas próximas'}`;
              return (
                <label className="opcion-persona" key={e.equipoId}>
                  <input type="radio" name="equipoId" value={e.equipoId} checked={equipoId === e.equipoId} onChange={() => setEquipoId(e.equipoId)} />
                  <span className="avatar" aria-hidden="true">{iniciales(e.productOwner)}</span>
                  <span className="opcion-persona__nombre">{e.productOwner}<span className="opcion-persona__detalle">{detalle}</span></span>
                  <span className="carga" role="img" aria-label={`${e.proyectosActivos} de ${e.maxProyectos} proyectos activos`}>
                    {Array.from({ length: e.maxProyectos }, (_, i) => (
                      <span key={i} className={['carga__unidad', i < e.proyectosActivos && 'carga__unidad--ocupada'].filter(Boolean).join(' ')} />
                    ))}
                  </span>
                </label>
              );
            })}
          </div>
          {errores.equipoId && (
            <p className="campo__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.equipoId}</span></p>
          )}
        </fieldset>
        <Campo etiqueta="Nota para el Product Owner" opcional error={errores.nota} contador={{ actual: nota.length, maximo: 300 }}>
          <textarea className="campo__control--area" name="nota" maxLength={300} value={nota} onChange={(e) => setNota(e.target.value)}
                    placeholder="Ejemplo: Laura ya es cliente; revisa su tienda en línea antes de la cita." />
        </Campo>
      </form>
    </Dialogo>
  );
}

function DialogoCancelar({ cita, alCerrar, alTerminar }: {
  cita: CitaGestion;
  alCerrar: () => void;
  alTerminar: (c: CitaGestion, mensaje: string) => void;
}) {
  const idFormulario = useId();
  const [motivo, setMotivo] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const { formulario, enfocar } = useEnfocarError();

  async function cancelar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const limpio = motivo.trim();
    const nuevos: Record<string, string> = {};
    if (!limpio) nuevos.motivo = 'Escribe el motivo: quedará en la auditoría.';
    else if (limpio.length < 15) nuevos.motivo = 'Escribe al menos 15 caracteres.';
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return enfocar();
    setGuardando(true);
    try {
      const actualizada = await gestionCitas.cancelar(cita.citaId, limpio);
      alTerminar(actualizada, `Cita ${cita.codigo} cancelada. Avisamos al cliente.`);
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
      icono="papelera"
      tono="peligro"
      titulo="Cancelar cita"
      texto="El cliente y el Product Owner recibirán un aviso con el motivo. La franja queda libre."
      acciones={(
        <>
          <Boton variante="contorno" onClick={alCerrar} disabled={guardando}>Volver</Boton>
          <Boton type="submit" form={idFormulario} variante="peligro" cargando={guardando}>Cancelar la cita</Boton>
        </>
      )}
    >
      <form id={idFormulario} ref={formulario} onSubmit={(e) => void cancelar(e)} noValidate>
        <Resumen cita={cita} />
        {general && <Alerta tipo="error" titulo={general} />}
        <Campo etiqueta="Motivo" error={errores.motivo} contador={{ actual: motivo.length, maximo: 300 }}>
          <textarea className="campo__control--area" name="motivo" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)}
                    placeholder="Ejemplo: el cliente pidió cambiar la fecha por teléfono; agendará de nuevo." />
        </Campo>
      </form>
    </Dialogo>
  );
}
