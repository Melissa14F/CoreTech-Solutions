import { useEffect, useId, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { iniciales } from '../../administracion/formato';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { Icono } from '../../componentes/Icono';
import { Insignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import {
  ROLES_POSIBLES, desarrolladores as apiDesarrolladores, equipos as apiEquipos, textoRol,
  type Desarrollador, type Equipo, type Especialidad, type Miembro, type RolEquipo,
} from '../../especialidad/api';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-usuarios.css';
import '../../styles/vistas/admin-equipos.css';

const sinTildes = (texto: string) => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const slug = (nombre: string) => sinTildes(nombre).replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
const primerNombre = (nombre: string) => nombre.split(' ')[0] ?? nombre;

const SIN_INTEGRANTES = 'Agrega al menos un integrante además del desarrollador (Product Owner)';
const FILA_VACIA = 'Elige el desarrollador de cada fila';

interface Fila {
  clave: number;
  devId: string;
  rol: RolEquipo | '';
}

/** Equipos de trabajo y desarrolladores disponibles (HU-26; prototipo/admin/equipos.html). */
export function PaginaEquipos() {
  useTituloDocumento('Equipos');
  const notificar = useNotificar();
  const [equipos, setEquipos] = useState<Equipo[] | null>(null);
  const [devs, setDevs] = useState<Desarrollador[]>([]);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [editando, setEditando] = useState<Equipo | 'nuevo' | null>(null);
  const [resaltado, setResaltado] = useState<string | null>(null);

  const cargar = async () => {
    const [e, d] = await Promise.all([apiEquipos.listar(), apiDesarrolladores.listar()]);
    setEquipos(e);
    setDevs(d);
  };

  useEffect(() => {
    cargar().catch((e) => setErrorCarga(separarErrores(e).general ?? 'No pudimos cargar los equipos.'));
  }, []);

  const disponibles = useMemo(() => devs.filter((d) => d.activo && !d.equipo)
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')), [devs]);

  function crear() {
    if (disponibles.length < 2) {
      notificar('Necesitas al menos 2 desarrolladores sin equipo. Regístralos en Desarrolladores.', 'error');
      return;
    }
    setEditando('nuevo');
  }

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Equipos</h1>
          <p className="encabezado-vista__texto">
            Cada proyecto lo trabaja un equipo. Su Product Owner tiene cuenta y responde por el proyecto; los demás integrantes vienen del{' '}
            <Link to="/admin/especialidad/desarrolladores">registro de desarrolladores</Link> con el rol que les permite su especialidad.
          </p>
        </div>
        <Boton icono="equipo" onClick={crear}>Crear equipo</Boton>
      </div>

      {errorCarga && <Alerta tipo="error" titulo={errorCarga} />}
      {!equipos && !errorCarga && <Cargando texto="Cargando equipos…" />}

      {equipos && (
        <div className="equipos">
          <div className="equipos__rejilla">
            {equipos.map((e) => (
              <TarjetaEquipo key={e.equipoId} equipo={e} resaltada={resaltado === e.equipoId} alEditar={() => setEditando(e)} />
            ))}
          </div>
          <aside className="tarjeta disponibles" aria-labelledby="titulo-disponibles">
            <h2 className="tarjeta__titulo" id="titulo-disponibles">Disponibles sin equipo</h2>
            <p className="texto-tenue disponibles__texto">Desarrolladores activos que puedes sumar a un equipo.</p>
            <ul className="disponibles__lista">
              {disponibles.length > 0 ? disponibles.map((d) => (
                <li key={d.desarrolladorId} className="disponibles__item">
                  <span className="avatar avatar--pequeno avatar--gris" aria-hidden="true">{iniciales(d.nombre)}</span>
                  <span className="celda-principal"><strong>{d.nombre}</strong><small>{textoRol(d.especialidad)}</small></span>
                </li>
              )) : <li className="disponibles__vacio">Todos los desarrolladores activos ya están en un equipo.</li>}
            </ul>
            <Link className="enlace-flecha" to="/admin/especialidad/desarrolladores">Ir a Desarrolladores</Link>
          </aside>
        </div>
      )}

      {editando && equipos && (
        <FormularioEquipo
          equipo={editando === 'nuevo' ? null : editando}
          devs={devs}
          alCerrar={() => setEditando(null)}
          alGuardar={async (guardado, cambioPO) => {
            await cargar();
            setResaltado(guardado.equipoId);
            setEditando(null);
            const nombre = guardado.productOwner.nombre;
            notificar(editando === 'nuevo'
              ? `Equipo de ${nombre} creado. Enviamos los datos de acceso a ${primerNombre(nombre)}.`
              : cambioPO ? `Equipo actualizado. ${nombre} es ahora el Product Owner y recibió su cuenta.` : `Equipo de ${nombre} actualizado.`);
          }}
        />
      )}
    </>
  );
}

function Miembros({ miembro: m }: { miembro: Miembro }) {
  const po = m.rol === 'PRODUCT_OWNER';
  return (
    <li className={['tarjeta-equipo__miembro', po && 'tarjeta-equipo__miembro--po'].filter(Boolean).join(' ')}>
      <span className={['avatar avatar--pequeno', !po && 'avatar--gris'].filter(Boolean).join(' ')} aria-hidden="true">{iniciales(m.nombre)}</span>
      <span className="celda-principal"><strong>{m.nombre}</strong><small>Especialidad: {textoRol(m.especialidad)}</small></span>
      {po
        ? <Insignia variante="oscuro"><Icono nombre="llave" />Product Owner</Insignia>
        : <Insignia variante="especialidad">{textoRol(m.rol)}</Insignia>}
    </li>
  );
}

function TarjetaEquipo({ equipo: e, resaltada, alEditar }: { equipo: Equipo; resaltada: boolean; alEditar: () => void }) {
  const nombre = e.productOwner.nombre;
  const carga = `${e.proyectosActivos} de ${e.maxProyectos} proyectos activos`;
  return (
    <article className={['tarjeta tarjeta-equipo', resaltada && 'esta-resaltada'].filter(Boolean).join(' ')} id={`equipo-${slug(nombre)}`}>
      <header className="tarjeta-equipo__cabecera">
        <div>
          <h2 className="tarjeta-equipo__titulo">Equipo de {nombre}</h2>
          <p className="tarjeta-equipo__subtitulo">{e.integrantes.length + 1} integrantes · solo {primerNombre(nombre)} tiene cuenta</p>
        </div>
        <span className="tarjeta-equipo__carga">
          <span className="carga" role="img" aria-label={carga}>
            {Array.from({ length: e.maxProyectos }, (_, i) => (
              <span key={i} className={['carga__unidad', i < e.proyectosActivos && 'carga__unidad--ocupada'].filter(Boolean).join(' ')} />
            ))}
          </span>
          <small>{carga}</small>
        </span>
      </header>
      <ul className="tarjeta-equipo__lista">
        <Miembros miembro={e.productOwner} />
        {e.integrantes.map((m) => <Miembros key={m.desarrolladorId} miembro={m} />)}
      </ul>
      <footer className="tarjeta-equipo__pie">
        <Link className="enlace-flecha" to={`/admin/proyectos?buscar=${encodeURIComponent(primerNombre(nombre))}`}>
          <Icono nombre="carpeta" />Ver sus proyectos
        </Link>
        <Boton variante="contorno" tamano="pequeno" icono="editar" onClick={alEditar} aria-label={`Editar equipo de ${nombre}`}>Editar equipo</Boton>
      </footer>
    </article>
  );
}

// ---------------------------------------------------------------- Crear o editar un equipo

function FormularioEquipo({ equipo, devs, alCerrar, alGuardar }: {
  equipo: Equipo | null;
  devs: Desarrollador[];
  alCerrar: () => void;
  alGuardar: (e: Equipo, cambioPO: boolean) => Promise<void>;
}) {
  const idFormulario = useId();
  const contador = useMemo(() => ({ n: 0 }), []);
  const nuevaClave = () => ++contador.n;
  const equipoId = equipo?.equipoId ?? null;
  const poOriginal = equipo?.productOwner.desarrolladorId ?? '';

  const [poId, setPoId] = useState(poOriginal);
  const [filas, setFilas] = useState<Fila[]>(() => equipo
    ? equipo.integrantes.map((m) => ({ clave: nuevaClave(), devId: m.desarrolladorId, rol: m.rol }))
    : [{ clave: nuevaClave(), devId: '', rol: '' }]);
  const [errorPO, setErrorPO] = useState<string | null>(null);
  const [errorIntegrantes, setErrorIntegrantes] = useState<string | null>(null);
  const [filaInvalida, setFilaInvalida] = useState<number | null>(null);
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const porId = useMemo(() => new Map(devs.map((d) => [d.desarrolladorId, d])), [devs]);
  // Un desarrollador activo que no esté en otro equipo (los de este equipo siguen disponibles)
  const elegibles = useMemo(() => devs.filter((d) => d.activo && (!d.equipo || d.equipo.equipoId === equipoId))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')), [devs, equipoId]);
  const etiqueta = (d: Desarrollador) => `${d.nombre} · ${textoRol(d.especialidad)}`;

  const limpiarErrores = () => {
    setErrorIntegrantes(null);
    setFilaInvalida(null);
  };

  function elegirPO(nuevo: string) {
    const anterior = poId;
    setErrorPO(null);
    setGeneral(null);
    setPoId(nuevo);
    setFilas((actuales) => {
      // El nuevo Product Owner sale de la lista de integrantes; el que deja el cargo pasa a ser integrante (puedes quitarlo)
      let siguientes = actuales.filter((f) => f.devId !== nuevo);
      const previo = anterior ? porId.get(anterior) : undefined;
      if (previo && previo.activo && anterior !== nuevo && !siguientes.some((f) => f.devId === anterior)) {
        siguientes = [...siguientes.filter((f) => f.devId !== ''), {
          clave: nuevaClave(), devId: anterior, rol: ROLES_POSIBLES[previo.especialidad][0] ?? '',
        }];
      }
      return siguientes;
    });
  }

  function cambiarFila(clave: number, devId: string) {
    limpiarErrores();
    setFilas((f) => f.map((x) => {
      if (x.clave !== clave) return x;
      const dev = porId.get(devId);
      return { ...x, devId, rol: dev ? ROLES_POSIBLES[dev.especialidad][0] ?? '' : '' };
    }));
  }

  const cambioPO = !!equipo && !!poId && poId !== poOriginal;
  const poNuevo = !!poId && poId !== poOriginal;
  const poAnterior = equipo?.productOwner;

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    setGeneral(null);
    limpiarErrores();
    let hayError = false;
    if (!poId) {
      setErrorPO('Elige al Product Owner del equipo.');
      hayError = true;
    } else setErrorPO(null);
    const vacia = filas.find((f) => !f.devId);
    const llenas = filas.filter((f) => f.devId);
    if (vacia && filas.length > 1) {
      setErrorIntegrantes(FILA_VACIA);
      setFilaInvalida(vacia.clave);
      hayError = true;
    } else if (llenas.length === 0) {
      setErrorIntegrantes(SIN_INTEGRANTES);
      setFilaInvalida(filas[0]?.clave ?? null);
      hayError = true;
    }
    if (hayError) return;

    const datos = { productOwnerId: poId, integrantes: llenas.map((f) => ({ desarrolladorId: f.devId, rol: f.rol as RolEquipo })) };
    setGuardando(true);
    try {
      const guardado = equipoId ? await apiEquipos.editar(equipoId, datos) : await apiEquipos.crear(datos);
      await alGuardar(guardado, cambioPO);
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setErrorPO(porCampo.productOwnerId ?? null);
      setErrorIntegrantes(porCampo.integrantes ?? null);
      setGeneral(mensaje ?? porCampo.maxProyectos ?? null);
      setGuardando(false);
    }
  }

  const opcionesFila = (fila: Fila) => elegibles.filter((d) => d.desarrolladorId !== poId
    && (d.desarrolladorId === fila.devId || !filas.some((f) => f.devId === d.desarrolladorId)));

  return (
    <Dialogo
      abierto
      alCerrar={alCerrar}
      ancho
      clase="dialogo-equipo"
      icono="equipo"
      titulo={equipo ? `Editar equipo de ${equipo.productOwner.nombre}` : 'Crear equipo'}
      texto="Elige al Product Owner y suma integrantes del registro de desarrolladores. El rol de cada uno depende de su especialidad."
      acciones={
        <>
          <Boton variante="contorno" onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form={idFormulario} cargando={guardando}>{equipo ? 'Guardar cambios' : 'Crear equipo'}</Boton>
        </>
      }
    >
      <form id={idFormulario} onSubmit={guardar} noValidate>
        {general && <Alerta tipo="error" titulo={general} />}
        <div className={['campo', errorPO && 'campo--invalido'].filter(Boolean).join(' ')}>
          <label className="campo__etiqueta" htmlFor="equipo-po">Product Owner</label>
          <select className="campo__control select" id="equipo-po" name="po" value={poId} aria-invalid={errorPO ? true : undefined}
                  aria-describedby="equipo-po-ayuda" onChange={(e) => elegirPO(e.target.value)}>
            <option value="">Elige un desarrollador</option>
            {elegibles.map((d) => <option key={d.desarrolladorId} value={d.desarrolladorId}>{etiqueta(d)}</option>)}
          </select>
          <p className="campo__ayuda" id="equipo-po-ayuda">Es el único del equipo con cuenta: registra el avance de los proyectos y habla con el cliente.</p>
          {errorPO && (
            <p className="campo__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errorPO}</span></p>
          )}
        </div>

        {cambioPO && poAnterior && (
          <div className="alerta alerta--info aviso-cambio-po">
            <Icono nombre="informacion" />
            <p>
              {porId.get(poId)?.nombre} tendrá cuenta y quedará a cargo
              {(equipo?.proyectosActivos ?? 0) > 0 ? ` de los ${equipo?.proyectosActivos} proyectos activos del equipo` : ' del equipo'}.{' '}
              {poAnterior.nombre} pierde el acceso a la plataforma{porId.get(poAnterior.desarrolladorId)?.activo ? ' y queda como integrante (puedes quitarlo)' : ''}.
            </p>
          </div>
        )}
        {poNuevo && (
          <div className="clave-temporal">
            <div>
              <p className="clave-temporal__titulo">Cuenta del Product Owner · contraseña temporal</p>
              <p className="clave-temporal__ayuda clave-temporal__ayuda--sola">
                La generamos al guardar y se la enviamos a su correo corporativo; no se muestra en pantalla. Deberá cambiarla en su primer ingreso.
              </p>
            </div>
          </div>
        )}

        <fieldset className={['integrantes', errorIntegrantes && 'integrantes--invalido'].filter(Boolean).join(' ')}>
          <legend className="campo__etiqueta">Integrantes</legend>
          <p className="campo__ayuda">Cada desarrollador solo puede estar en un equipo. Sin cuenta: aparecen en el proyecto para que el cliente sepa quién trabaja.</p>
          <div className="integrantes__encabezado" aria-hidden="true"><span>Desarrollador</span><span>Rol en el equipo</span></div>
          <ol className="integrantes__lista">
            {filas.map((fila, i) => {
              const dev = fila.devId ? porId.get(fila.devId) : undefined;
              const roles: Especialidad[] = dev ? ROLES_POSIBLES[dev.especialidad] : [];
              return (
                <li key={fila.clave} className={['integrantes__fila', filaInvalida === fila.clave && 'es-invalida'].filter(Boolean).join(' ')}>
                  <label className="integrantes__campo">
                    <span className="solo-lector">Desarrollador {i + 1}</span>
                    <select className="campo__control select" value={fila.devId} onChange={(e) => cambiarFila(fila.clave, e.target.value)}>
                      <option value="">Elige un desarrollador</option>
                      {opcionesFila(fila).map((d) => <option key={d.desarrolladorId} value={d.desarrolladorId}>{etiqueta(d)}</option>)}
                    </select>
                  </label>
                  <label className="integrantes__campo">
                    <span className="solo-lector">Rol en el equipo {i + 1}</span>
                    <select className="campo__control select" value={fila.rol} disabled={roles.length === 0}
                            onChange={(e) => setFilas((f) => f.map((x) => (x.clave === fila.clave ? { ...x, rol: e.target.value as RolEquipo } : x)))}>
                      {roles.length === 0 ? <option value="">Primero elige</option> : roles.map((r) => <option key={r} value={r}>{textoRol(r)}</option>)}
                    </select>
                  </label>
                  <button type="button" className="boton-icono boton-icono--pequeno integrantes__quitar" aria-label="Quitar integrante" title="Quitar integrante"
                          onClick={() => { limpiarErrores(); setFilas((f) => f.filter((x) => x.clave !== fila.clave)); }}>
                    <Icono nombre="papelera" />
                  </button>
                </li>
              );
            })}
          </ol>
          <Boton variante="contorno" tamano="pequeno" icono="mas" className="integrantes__agregar"
                 onClick={() => { limpiarErrores(); setFilas((f) => [...f, { clave: nuevaClave(), devId: '', rol: '' }]); }}>
            Agregar integrante
          </Boton>
          <p className="campo__mensaje" aria-live="polite">
            {errorIntegrantes && <Icono nombre="alerta" />}<span className="campo__mensaje-texto">{errorIntegrantes}</span>
          </p>
        </fieldset>
      </form>
    </Dialogo>
  );
}
