import { useCallback, useEffect, useId, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { iniciales } from '../../administracion/formato';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { Icono } from '../../componentes/Icono';
import { Insignia } from '../../componentes/Insignia';
import { MenuAcciones, type OpcionMenu } from '../../componentes/MenuAcciones';
import { useNotificar } from '../../componentes/Notificaciones';
import { Paginacion } from '../../componentes/Paginacion';
import { Tabla, type ColumnaTabla } from '../../componentes/Tabla';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import {
  fechaLegible, hoyColombia, proyectosAdmin, textosDeFila,
  type EquipoParaProyecto, type FiltroProyectos, type ListaProyectos, type ProyectoAdmin,
} from '../../proyectos/admin';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-usuarios.css';
import '../../styles/vistas/admin-proyectos.css';

const FILTROS: [FiltroProyectos, string][] = [
  ['todas', 'Todos'], ['activo', 'Activos'], ['riesgo', 'En riesgo'], ['plan', 'Plan por aprobar'], ['pausado', 'Pausados'],
  ['garantia', 'En garantía'], ['finalizado', 'Finalizados'], ['cancelado', 'Cancelados'],
];

type Accion = 'editar' | 'reasignar' | 'pausar' | 'reanudar' | 'cancelar';
const planVuelve = (p: ProyectoAdmin) => p.estadoPlan === 'VENCIDO' || p.estadoPlan === 'RECHAZADO';

/** Proyectos del administrador: filtrar, editar, reasignar, pausar, reanudar o cancelar (HU-17; prototipo/admin/proyectos.html). */
export function PaginaProyectosAdmin() {
  useTituloDocumento('Proyectos');
  const notificar = useNotificar();
  const [parametros] = useSearchParams();
  const [lista, setLista] = useState<ListaProyectos | null>(null);
  const [equipos, setEquipos] = useState<EquipoParaProyecto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<FiltroProyectos>('todas');
  const [equipoId, setEquipoId] = useState('');
  const [busqueda, setBusqueda] = useState(parametros.get('buscar') ?? '');
  const [consulta, setConsulta] = useState(busqueda);
  const [pagina, setPagina] = useState(0);
  const [intento, setIntento] = useState(0);
  const [accion, setAccion] = useState<{ tipo: Accion; proyecto: ProyectoAdmin } | null>(null);
  const [resaltado, setResaltado] = useState<string | null>(null);

  const cargar = useCallback(() => {
    let vigente = true;
    setError(null);
    proyectosAdmin.lista({ estado: filtro, equipoId: equipoId || undefined, q: consulta, pagina })
      .then((datos) => vigente && setLista(datos))
      .catch((e: unknown) => vigente && setError(e instanceof ErrorApi ? e.message : 'No pudimos cargar los proyectos.'));
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
    proyectosAdmin.equipos().then(setEquipos).catch(() => setEquipos([]));
  }, [intento]);

  const terminar = (proyecto: ProyectoAdmin, mensaje: string, tipo?: 'error') => {
    setAccion(null);
    setResaltado(proyecto.codigo);
    notificar(mensaje, tipo);
    setIntento((n) => n + 1);
  };

  const hoy = hoyColombia();
  const columnas: ColumnaTabla<ProyectoAdmin>[] = [
    {
      clave: 'proyecto', titulo: 'Proyecto y cliente', ancha: true, clase: 'celda-proyecto',
      celda: (p) => (
        <span className="celda-principal">
          <strong>{p.nombre}</strong>
          <small><span className="codigo">{p.codigo}</span>{` · ${p.cliente.nombre} · ${p.servicio}`}</small>
        </span>
      ),
    },
    {
      clave: 'equipo', titulo: 'Equipo (Product Owner)',
      celda: (p) => (
        <span className="celda-persona">
          <span className="avatar" aria-hidden="true">{iniciales(p.equipo.productOwner)}</span>
          <span>{p.equipo.productOwner}</span>
        </span>
      ),
    },
    {
      clave: 'avance', titulo: 'Avance',
      celda: (p) => {
        const { avance } = textosDeFila(p, hoy);
        return (
          <span className="avance-tabla" role="img" aria-label={`${avance.titulo}. ${avance.detalle}`}>
            <span className="avance-tabla__texto"><strong>{avance.titulo}</strong><small>{avance.detalle}</small></span>
            {avance.porcentaje !== null && (
              <span className="avance-tabla__barra"><span style={{ ['--valor' as string]: `${avance.porcentaje}%` }} /></span>
            )}
          </span>
        );
      },
    },
    {
      clave: 'entrega', titulo: 'Entrega',
      celda: (p) => {
        const { plazo } = textosDeFila(p, hoy);
        return (
          <span className="celda-principal celda-fecha">
            <strong>{fechaLegible(p.fechaEstimadaEntrega)}</strong>
            <small className={['plazo', plazo.tono !== 'normal' && `plazo--${plazo.tono}`].filter(Boolean).join(' ')}>{plazo.texto}</small>
          </span>
        );
      },
    },
    {
      clave: 'estado', titulo: 'Estado',
      celda: (p) => {
        const { insignia } = textosDeFila(p, hoy);
        return <Insignia variante={insignia.variante}>{insignia.texto}</Insignia>;
      },
    },
    { clave: 'acciones', titulo: 'Acciones', tituloOculto: true, celda: (p) => <Acciones proyecto={p} abrir={(tipo) => setAccion({ tipo, proyecto: p })} /> },
  ];

  const cantidad = (f: FiltroProyectos) => lista?.porFiltro[f] ?? 0;

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Proyectos</h1>
          <p className="encabezado-vista__texto">
            Todos los proyectos de CoreTech. Edita sus datos, revisa su plan de trabajo, cambia el equipo o pausa un proyecto. El avance solo lo registra el Product Owner del equipo.
          </p>
        </div>
        <Link className="boton boton--acento" to="/admin/proyectos/nuevo"><Icono nombre="mas" />Crear proyecto</Link>
      </div>

      {error && !lista ? (
        <>
          <Alerta tipo="error" titulo="No pudimos cargar los proyectos">{error}</Alerta>
          <p><Boton variante="contorno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton></p>
        </>
      ) : !lista ? (
        <Cargando texto="Cargando los proyectos…" />
      ) : (
        <>
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
                <span className="solo-lector">Buscar proyecto</span>
                <input className="campo__control" type="search" placeholder="Buscar proyecto o cliente" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
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
          <Tabla
            titulo="Proyectos de CoreTech"
            columnas={columnas}
            filas={lista.proyectos}
            claveFila={(p) => p.codigo}
            claseFila={(p) => [p.enRiesgo && 'fila--atencion', (p.estado === 'PAUSADO' || p.estado === 'FINALIZADO' || p.estado === 'CANCELADO') && 'fila--apagada',
              resaltado === p.codigo && 'esta-resaltada'].filter(Boolean).join(' ')}
            vacio={(
              <EstadoVacio
                titulo="No hay proyectos con estos filtros"
                texto="Prueba con otro estado, otro equipo u otra búsqueda."
                accion={<Boton variante="contorno" tamano="pequeno" onClick={() => { setFiltro('todas'); setEquipoId(''); setBusqueda(''); setConsulta(''); setPagina(0); }}>Limpiar filtros</Boton>}
              />
            )}
          />
          <Paginacion pagina={lista.pagina} totalPaginas={lista.totalPaginas} alCambiar={setPagina} etiqueta="Páginas de los proyectos" />
        </>
      )}

      {accion?.tipo === 'editar' && <DialogoEditar proyecto={accion.proyecto} alCerrar={() => setAccion(null)} alTerminar={terminar} />}
      {accion?.tipo === 'reasignar' && <DialogoReasignar proyecto={accion.proyecto} equipos={equipos} alCerrar={() => setAccion(null)} alTerminar={terminar} />}
      {(accion?.tipo === 'pausar' || accion?.tipo === 'reanudar' || accion?.tipo === 'cancelar') && (
        <DialogoEstado tipo={accion.tipo} proyecto={accion.proyecto} equipos={equipos} alCerrar={() => setAccion(null)} alTerminar={terminar} />
      )}
    </>
  );
}

function Acciones({ proyecto: p, abrir }: { proyecto: ProyectoAdmin; abrir: (tipo: Accion) => void }) {
  if (p.estado === 'EN_GARANTIA') {
    return <div className="celda-acciones"><span className="celda-principal"><small><Icono nombre="candado" /> Solo correcciones</small></span></div>;
  }
  if (p.estado === 'FINALIZADO' || p.estado === 'CANCELADO') {
    return (
      <div className="celda-acciones">
        <span className="celda-principal"><small><Icono nombre="candado" /> {p.estado === 'FINALIZADO' ? 'Cerrado: no admite cambios' : 'Cancelado: no admite cambios'}</small></span>
      </div>
    );
  }
  const opciones: OpcionMenu[] = [
    { texto: 'Reasignar equipo', icono: 'usuarios', alElegir: () => abrir('reasignar') },
    p.estado === 'PAUSADO'
      ? { texto: 'Reanudar', icono: 'reproducir', alElegir: () => abrir('reanudar') }
      : { texto: 'Pausar', icono: 'pausa', alElegir: () => abrir('pausar') },
    { texto: 'Cancelar proyecto', icono: 'papelera', peligro: true, alElegir: () => abrir('cancelar') },
  ];
  return (
    <div className="celda-acciones">
      <Link className="boton boton--contorno boton--pequeno" to={`/admin/proyectos/${p.codigo}/plan`} aria-label={`Plan de trabajo de ${p.codigo}`}>
        <Icono nombre="lista" />Plan de trabajo
      </Link>
      <Boton variante="contorno" tamano="pequeno" icono="editar" onClick={() => abrir('editar')} aria-label={`Editar ${p.codigo}`}>Editar</Boton>
      <MenuAcciones etiqueta={`Más acciones para ${p.codigo}`} opciones={opciones} />
    </div>
  );
}

function Resumen({ proyecto }: { proyecto: ProyectoAdmin }) {
  return (
    <div className="resumen-dialogo">
      <strong>{`${proyecto.codigo} · ${proyecto.nombre}`}</strong>
      <span>{`Cliente: ${proyecto.cliente.nombre} · Responsable: ${proyecto.equipo.productOwner}`}</span>
    </div>
  );
}

interface PropiedadesDialogo {
  proyecto: ProyectoAdmin;
  alCerrar: () => void;
  alTerminar: (proyecto: ProyectoAdmin, mensaje: string, tipo?: 'error') => void;
}

function useEnvio() {
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const { formulario, enfocar } = useEnfocarError();
  return { errores, setErrores, general, setGeneral, guardando, setGuardando, formulario, enfocar };
}

function DialogoEditar({ proyecto, alCerrar, alTerminar }: PropiedadesDialogo) {
  const id = useId();
  const envio = useEnvio();
  const [nombre, setNombre] = useState(proyecto.nombre);
  const [alcance, setAlcance] = useState(proyecto.alcance);
  const [entrega, setEntrega] = useState(proyecto.fechaEstimadaEntrega);
  const [motivo, setMotivo] = useState('');
  const hoy = hoyColombia();
  const enviado = proyecto.estadoPlan === 'ENVIADO';
  const aprobado = proyecto.estadoPlan === 'APROBADO';
  const cambiaFecha = entrega !== proyecto.fechaEstimadaEntrega;

  async function guardar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const n = nombre.trim();
    const a = alcance.trim();
    const m = motivo.trim();
    const nuevos: Record<string, string> = {};
    if (!n) nuevos.nombre = 'Escribe el nombre del proyecto.';
    else if (n.length < 5) nuevos.nombre = 'Escribe al menos 5 caracteres.';
    if (!a) nuevos.alcance = 'Describe el alcance.';
    else if (a.length < 20) nuevos.alcance = 'Escribe al menos 20 caracteres.';
    if (!entrega) nuevos.fechaEstimadaEntrega = 'Elige la fecha de entrega.';
    else if (cambiaFecha && entrega < hoy) nuevos.fechaEstimadaEntrega = 'La entrega no puede quedar en una fecha pasada.';
    if (!m) nuevos.motivo = 'Escribe el motivo: queda en la auditoría.';
    else if (m.length < 10) nuevos.motivo = 'Escribe al menos 10 caracteres.';
    else if (aprobado && cambiaFecha && m.length < 20) nuevos.motivo = 'El motivo del cambio de fecha debe tener al menos 20 caracteres.';
    envio.setErrores(nuevos);
    envio.setGeneral(null);
    if (Object.keys(nuevos).length > 0) return envio.enfocar();
    envio.setGuardando(true);
    try {
      const resultado = await proyectosAdmin.editar(proyecto.codigo, {
        nombre: n !== proyecto.nombre ? n : undefined,
        alcance: a !== proyecto.alcance ? a : undefined,
        fechaEstimadaEntrega: cambiaFecha ? entrega : undefined,
        motivo: m,
      });
      alTerminar(resultado.proyecto, resultado.cambioFecha
        ? `Le propusimos a ${proyecto.cliente.nombre} entregar el ${fechaLegible(resultado.cambioFecha.nuevaFecha)}. La fecha cambia cuando acepte.`
        : `Cambios guardados en ${proyecto.codigo}. Avisamos al cliente y al responsable.`);
    } catch (e) {
      const { porCampo, general } = separarErrores(e);
      envio.setErrores(porCampo);
      envio.setGeneral(general);
      envio.setGuardando(false);
      envio.enfocar();
    }
  }

  return (
    <Dialogo
      abierto alCerrar={() => !envio.guardando && alCerrar()} ancho icono="editar" titulo="Editar proyecto"
      texto="Los cambios quedan en la auditoría y avisamos al cliente y al responsable."
      acciones={(
        <>
          <Boton variante="contorno" onClick={alCerrar} disabled={envio.guardando}>Volver</Boton>
          <Boton type="submit" form={id} cargando={envio.guardando}>Guardar cambios</Boton>
        </>
      )}
    >
      <form id={id} ref={envio.formulario} onSubmit={(e) => void guardar(e)} noValidate>
        <Resumen proyecto={proyecto} />
        {envio.general && <Alerta tipo="error" titulo={envio.general} />}
        <Campo etiqueta="Nombre" error={envio.errores.nombre}>
          <input type="text" name="nombre" maxLength={60} value={nombre} onChange={(e) => setNombre(e.target.value)} />
        </Campo>
        <Campo etiqueta="Alcance acordado" error={envio.errores.alcance} contador={{ actual: alcance.length, maximo: 600 }}>
          <textarea className="campo__control--area" name="alcance" maxLength={600} value={alcance} onChange={(e) => setAlcance(e.target.value)} />
        </Campo>
        <Campo
          etiqueta="Entrega estimada" error={envio.errores.fechaEstimadaEntrega}
          ayuda={enviado ? 'El plan está enviado al cliente: espera su respuesta para cambiar la entrega.'
            : aprobado ? 'Con el plan aprobado, la nueva fecha se la proponemos al cliente y cambia cuando la acepte.' : undefined}
        >
          <input type="date" name="fechaEstimadaEntrega" min={hoy} value={entrega} disabled={enviado} onChange={(e) => setEntrega(e.target.value)} />
        </Campo>
        <Campo
          etiqueta="Motivo del cambio" error={envio.errores.motivo} contador={{ actual: motivo.length, maximo: 300 }}
        >
          <textarea className="campo__control--area" name="motivo" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)}
                    placeholder="Ejemplo: el cliente pidió agregar el módulo de facturación; se amplía la entrega." />
        </Campo>
      </form>
    </Dialogo>
  );
}

function OpcionesDeEquipo({ equipos, actual, exigeCupo, valor, alElegir, error }: {
  equipos: EquipoParaProyecto[]; actual: string | null; exigeCupo: boolean; valor: string; alElegir: (id: string) => void; error?: string;
}) {
  return (
    <fieldset className={['grupo-opciones campo', error && 'campo--invalido'].filter(Boolean).join(' ')}>
      <legend className="campo__etiqueta">Equipo <span className="campo__opcional">(máximo 3 proyectos activos, RN-05)</span></legend>
      <div className="opciones-persona">
        {equipos.map((e) => {
          const esActual = e.equipoId === actual;
          const sinCupo = exigeCupo && !esActual && e.cupoLibre <= 0;
          const detalle = esActual ? 'Responsable actual'
            : sinCupo ? `Sin cupo: ya tiene ${e.maxProyectos} proyectos activos`
              : `${e.proyectosActivos} de ${e.maxProyectos} proyectos activos`;
          return (
            <label key={e.equipoId} className={['opcion-persona', sinCupo && 'opcion-persona--sin-cupo'].filter(Boolean).join(' ')}>
              <input type="radio" name="equipoId" value={e.equipoId} checked={valor === e.equipoId} disabled={esActual || sinCupo} onChange={() => alElegir(e.equipoId)} />
              <span className="avatar" aria-hidden="true">{iniciales(e.productOwner)}</span>
              <span className="opcion-persona__nombre">{`Equipo de ${e.productOwner}`}<span className="opcion-persona__detalle">{detalle}</span></span>
              <span className="carga" role="img" aria-label={`${e.proyectosActivos} de ${e.maxProyectos} proyectos activos`}>
                {Array.from({ length: e.maxProyectos }, (_, i) => (
                  <span key={i} className={['carga__unidad', i < e.proyectosActivos && 'carga__unidad--ocupada'].filter(Boolean).join(' ')} />
                ))}
              </span>
            </label>
          );
        })}
      </div>
      {error && <p className="campo__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{error}</span></p>}
    </fieldset>
  );
}

function DialogoReasignar({ proyecto, equipos, alCerrar, alTerminar }: PropiedadesDialogo & { equipos: EquipoParaProyecto[] }) {
  const id = useId();
  const envio = useEnvio();
  const [equipoId, setEquipoId] = useState('');
  const [motivo, setMotivo] = useState('');

  async function guardar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const m = motivo.trim();
    const nuevos: Record<string, string> = {};
    if (!equipoId) nuevos.equipoId = 'Elige el nuevo equipo.';
    if (!m) nuevos.motivo = 'Escribe el motivo: queda en la auditoría.';
    else if (m.length < 10) nuevos.motivo = 'Escribe al menos 10 caracteres.';
    envio.setErrores(nuevos);
    envio.setGeneral(null);
    if (Object.keys(nuevos).length > 0) return envio.enfocar();
    envio.setGuardando(true);
    try {
      const resultado = await proyectosAdmin.editar(proyecto.codigo, { equipoId, motivo: m });
      alTerminar(resultado.proyecto, `${proyecto.codigo} pasó de ${proyecto.equipo.productOwner} a ${resultado.proyecto.equipo.productOwner}.`);
    } catch (e) {
      const { porCampo, general } = separarErrores(e);
      envio.setErrores(porCampo);
      envio.setGeneral(general);
      envio.setGuardando(false);
      envio.enfocar();
    }
  }

  return (
    <Dialogo
      abierto alCerrar={() => !envio.guardando && alCerrar()} ancho icono="usuarios" titulo="Reasignar equipo"
      texto="El nuevo equipo recibe el proyecto con su historial; el anterior deja de poder registrar avances."
      acciones={(
        <>
          <Boton variante="contorno" onClick={alCerrar} disabled={envio.guardando}>Volver</Boton>
          <Boton type="submit" form={id} cargando={envio.guardando}>Reasignar</Boton>
        </>
      )}
    >
      <form id={id} ref={envio.formulario} onSubmit={(e) => void guardar(e)} noValidate>
        <Resumen proyecto={proyecto} />
        {envio.general && <Alerta tipo="error" titulo={envio.general} />}
        <OpcionesDeEquipo equipos={equipos} actual={proyecto.equipo.equipoId} exigeCupo={proyecto.estado === 'ACTIVO'} valor={equipoId} alElegir={setEquipoId} error={envio.errores.equipoId} />
        <Campo etiqueta="Motivo" error={envio.errores.motivo} contador={{ actual: motivo.length, maximo: 300 }}>
          <textarea className="campo__control--area" name="motivo" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)}
                    placeholder="Ejemplo: el equipo actual está sin cupo y este proyecto necesita atención inmediata." />
        </Campo>
      </form>
    </Dialogo>
  );
}

const TEXTOS_ESTADO = {
  pausar: {
    titulo: 'Pausar proyecto', texto: 'Deja de contar como proyecto activo del equipo y no admite avances hasta reanudarlo.', icono: 'pausa' as const,
    boton: 'Pausar proyecto', variante: 'oscuro' as const, estado: 'PAUSADO' as const, ejemplo: 'Ejemplo: el cliente pidió retomar en enero por temporada alta.',
    exito: (c: string, p: ProyectoAdmin) => `${c} pausado. Ya no cuenta como proyecto activo de ${p.equipo.productOwner}.`,
  },
  reanudar: {
    titulo: 'Reanudar proyecto', texto: 'Vuelve a contar como proyecto activo del equipo.', icono: 'reproducir' as const,
    boton: 'Reanudar proyecto', variante: 'acento' as const, estado: 'ACTIVO' as const, ejemplo: 'Ejemplo: el cliente confirmó que retomamos esta semana.',
    exito: (c: string) => `${c} reanudado.`,
  },
  cancelar: {
    titulo: 'Cancelar proyecto', texto: 'El proyecto se cierra sin terminar. Su historial y avances se conservan para la auditoría.', icono: 'papelera' as const,
    boton: 'Cancelar proyecto', variante: 'peligro' as const, estado: 'CANCELADO' as const, ejemplo: 'Ejemplo: el cliente desistió del proyecto por presupuesto.',
    exito: (c: string) => `${c} cancelado. Su historial se conserva.`,
  },
};

function DialogoEstado({ tipo, proyecto, equipos, alCerrar, alTerminar }: PropiedadesDialogo & { tipo: 'pausar' | 'reanudar' | 'cancelar'; equipos: EquipoParaProyecto[] }) {
  const id = useId();
  const envio = useEnvio();
  const [motivo, setMotivo] = useState('');
  const [equipoId, setEquipoId] = useState('');
  const t = TEXTOS_ESTADO[tipo];
  // RN-29: un plan vencido o rechazado vuelve a borrador al reanudar, y el administrador elige el equipo
  const eligeEquipo = tipo === 'reanudar' && planVuelve(proyecto);

  async function guardar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const m = motivo.trim();
    const nuevos: Record<string, string> = {};
    if (!m) nuevos.motivo = 'Escribe el motivo: queda en la auditoría.';
    else if (m.length < 10) nuevos.motivo = 'Escribe al menos 10 caracteres.';
    if (eligeEquipo && !equipoId) nuevos.equipoId = 'Elige el equipo que retomará el proyecto (puede ser el mismo).';
    envio.setErrores(nuevos);
    envio.setGeneral(null);
    if (Object.keys(nuevos).length > 0) return envio.enfocar();
    envio.setGuardando(true);
    try {
      const actualizado = await proyectosAdmin.cambiarEstado(proyecto.codigo, t.estado, m, eligeEquipo ? equipoId : undefined);
      alTerminar(actualizado, t.exito(proyecto.codigo, proyecto));
    } catch (e) {
      const { porCampo, general } = separarErrores(e);
      envio.setErrores(porCampo);
      // Sin selector de equipo (el equipo actual ya no tiene cupo), el error del equipo va arriba del formulario
      envio.setGeneral(general ?? (!eligeEquipo && porCampo.equipoId ? `${porCampo.equipoId}. Reasigna el proyecto antes de reanudarlo.` : null));
      envio.setGuardando(false);
      envio.enfocar();
    }
  }

  return (
    <Dialogo
      abierto alCerrar={() => !envio.guardando && alCerrar()} ancho icono={t.icono} tono={tipo === 'cancelar' ? 'peligro' : 'acento'} titulo={t.titulo}
      texto={eligeEquipo ? 'La propuesta no se aprobó: el plan vuelve a borrador y el equipo lo revisa y lo reenvía al cliente.' : t.texto}
      acciones={(
        <>
          <Boton variante="contorno" onClick={alCerrar} disabled={envio.guardando}>Volver</Boton>
          <Boton type="submit" form={id} variante={t.variante} cargando={envio.guardando}>{t.boton}</Boton>
        </>
      )}
    >
      <form id={id} ref={envio.formulario} onSubmit={(e) => void guardar(e)} noValidate>
        <Resumen proyecto={proyecto} />
        {envio.general && <Alerta tipo="error" titulo={envio.general} />}
        {eligeEquipo && (
          <OpcionesDeEquipo equipos={equipos} actual={null} exigeCupo valor={equipoId} alElegir={setEquipoId} error={envio.errores.equipoId} />
        )}
        <Campo etiqueta="Motivo" error={envio.errores.motivo} contador={{ actual: motivo.length, maximo: 300 }}>
          <textarea className="campo__control--area" name="motivo" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder={t.ejemplo} />
        </Campo>
      </form>
    </Dialogo>
  );
}
