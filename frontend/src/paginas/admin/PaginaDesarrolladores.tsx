import { useEffect, useId, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { iniciales, resultados } from '../../administracion/formato';
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
import { Tabla, type ColumnaTabla } from '../../componentes/Tabla';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import {
  ESPECIALIDADES, ROLES_POSIBLES, desarrolladores as apiDesarrolladores, textoRol,
  type DatosDesarrollador, type Desarrollador, type Especialidad,
} from '../../especialidad/api';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-usuarios.css';
import '../../styles/vistas/admin-desarrolladores.css';

const DOMINIO = '@coretechsolutions.co';
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

type Filtro = 'todas' | 'product-owner' | 'con-equipo' | 'sin-equipo' | 'inactivo';
const FILTROS: { valor: Filtro; texto: string }[] = [
  { valor: 'todas', texto: 'Todos' },
  { valor: 'product-owner', texto: 'Product Owners' },
  { valor: 'con-equipo', texto: 'En un equipo' },
  { valor: 'sin-equipo', texto: 'Sin equipo' },
  { valor: 'inactivo', texto: 'Inactivos' },
];

type Errores = Partial<Record<keyof DatosDesarrollador, string>>;

const sinTildes = (texto: string) => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const slug = (nombre: string) => sinTildes(nombre).replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
const textoEspecialidad = (e: Especialidad) => ESPECIALIDADES.find((x) => x.valor === e)?.texto ?? e;

function ingreso(fecha: string): string {
  const [anio, mes] = fecha.split('-').map(Number);
  return `Ingresó en ${MESES[(mes ?? 1) - 1]} ${anio}`;
}

const esProductOwner = (d: Desarrollador) => d.rol === 'PRODUCT_OWNER';

/** Mismas reglas y mensajes que el backend (ServicioDesarrolladores) y prototipo/admin/desarrolladores.html. */
export function validarDesarrollador(d: DatosDesarrollador): Errores {
  const e: Errores = {};
  const nombre = d.nombre.trim().replace(/\s+/g, ' ');
  if (!nombre) e.nombre = 'Escribe el nombre completo.';
  else if (nombre.length < 5) e.nombre = 'Escribe nombre y apellido.';
  else if (nombre.length > 60) e.nombre = 'Escribe máximo 60 caracteres.';
  else if (!/^[\p{L} ]+$/u.test(nombre)) e.nombre = 'Escribe solo letras y espacios.';
  const celular = d.celular.trim();
  if (!celular) e.celular = 'Escribe el número de celular.';
  else if (!/^3\d{9}$/.test(celular)) e.celular = 'Escribe un celular de 10 dígitos que empiece por 3.';
  const correo = d.correo.trim().toLowerCase();
  if (!correo) e.correo = 'Escribe el correo.';
  else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo)) e.correo = 'Escribe un correo válido, por ejemplo nombre@empresa.com.';
  else if (!correo.endsWith(DOMINIO)) e.correo = 'Usa el correo corporativo que termina en @coretechsolutions.co.';
  if (!d.especialidad) e.especialidad = 'Elige la especialidad.';
  return e;
}

/** Registro del personal técnico (HU-25; prototipo/admin/desarrolladores.html). */
export function PaginaDesarrolladores() {
  useTituloDocumento('Desarrolladores');
  const notificar = useNotificar();
  const [lista, setLista] = useState<Desarrollador[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [especialidad, setEspecialidad] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [editando, setEditando] = useState<Desarrollador | 'nuevo' | null>(null);
  const [cambiando, setCambiando] = useState<Desarrollador | null>(null);
  const [resaltado, setResaltado] = useState<string | null>(null);

  useEffect(() => {
    apiDesarrolladores.listar().then(setLista)
      .catch((e) => setErrorCarga(separarErrores(e).general ?? 'No pudimos cargar los desarrolladores.'));
  }, []);

  const recargar = async () => setLista(await apiDesarrolladores.listar());

  const cumple = (d: Desarrollador, f: Filtro) => {
    switch (f) {
      case 'product-owner': return d.activo && esProductOwner(d);
      case 'con-equipo': return d.activo && !!d.equipo;
      case 'sin-equipo': return d.activo && !d.equipo;
      case 'inactivo': return !d.activo;
      default: return true;
    }
  };

  const conteo = useMemo(() => Object.fromEntries(
    FILTROS.map((f) => [f.valor, (lista ?? []).filter((d) => cumple(d, f.valor)).length]),
  ) as Record<Filtro, number>, [lista]);

  const porEspecialidad = useMemo(() => ESPECIALIDADES.map((e) => ({
    ...e, total: (lista ?? []).filter((d) => d.activo && d.especialidad === e.valor).length,
  })), [lista]);

  const visibles = useMemo(() => {
    const texto = sinTildes(busqueda.trim());
    return (lista ?? []).filter((d) => cumple(d, filtro)
      && (!especialidad || d.especialidad === especialidad)
      && (!texto || sinTildes(`${d.nombre} ${d.correo}`).includes(texto)));
  }, [lista, filtro, especialidad, busqueda]);

  const limpiar = () => {
    setFiltro('todas');
    setEspecialidad('');
    setBusqueda('');
  };

  const opciones = (d: Desarrollador): OpcionMenu[] => {
    const menu: (OpcionMenu | false)[] = [
      !!d.equipo && { texto: 'Ver su equipo', icono: 'equipo', a: `/admin/especialidad/equipos#equipo-${slug(d.equipo.productOwner)}` },
      d.activo
        ? { texto: 'Desactivar', icono: 'apagar', alElegir: () => setCambiando(d), peligro: true }
        : { texto: 'Activar', icono: 'exito', alElegir: () => setCambiando(d) },
    ];
    return menu.filter((o): o is OpcionMenu => o !== false);
  };

  const columnas: ColumnaTabla<Desarrollador>[] = [
    {
      clave: 'desarrollador', titulo: 'Desarrollador', ancha: true, celda: (d) => (
        <span className="celda-usuario">
          <span className="avatar" aria-hidden="true">{iniciales(d.nombre)}</span>
          <span className="celda-principal"><strong>{d.nombre}</strong><small>{d.correo}</small></span>
        </span>
      ),
    },
    { clave: 'especialidad', titulo: 'Especialidad', celda: (d) => <Insignia variante="especialidad">{textoEspecialidad(d.especialidad)}</Insignia> },
    {
      clave: 'equipo', titulo: 'Equipo y rol', celda: (d) => d.equipo && d.rol ? (
        <span className="celda-principal celda-equipo">
          <strong>Equipo de {d.equipo.productOwner}</strong>
          <small>
            {esProductOwner(d)
              ? <span className="rol-cuenta"><Icono nombre="llave" />Product Owner · tiene cuenta</span>
              : `Rol: ${textoRol(d.rol)}`}
          </small>
        </span>
      ) : <span className="celda-principal"><small className="texto-tenue">Sin equipo</small></span>,
    },
    {
      clave: 'contacto', titulo: 'Contacto', celda: (d) => (
        <span className="celda-principal"><small>{d.celular}</small><small className="texto-tenue">{ingreso(d.fechaIngreso)}</small></span>
      ),
    },
    { clave: 'estado', titulo: 'Estado', celda: (d) => (d.activo ? <Insignia variante="atendida">Activo</Insignia> : <Insignia variante="cancelada">Inactivo</Insignia>) },
    {
      clave: 'acciones', titulo: 'Acciones', tituloOculto: true, celda: (d) => (
        <div className="celda-acciones">
          <Boton variante="contorno" tamano="pequeno" icono="editar" onClick={() => setEditando(d)} aria-label={`Editar a ${d.nombre}`}>Editar</Boton>
          <MenuAcciones etiqueta={`Más acciones para ${d.nombre}`} opciones={opciones(d)} />
        </div>
      ),
    },
  ];

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Desarrolladores</h1>
          <p className="encabezado-vista__texto">
            Registro de todo el personal técnico con su especialidad. Con ellos armas los <Link to="/admin/especialidad/equipos">equipos</Link>;
            solo el Product Owner de cada equipo tiene cuenta en la plataforma.
          </p>
        </div>
        <Boton icono="codigo" onClick={() => setEditando('nuevo')}>Registrar desarrollador</Boton>
      </div>

      {errorCarga && <Alerta tipo="error" titulo={errorCarga} />}
      {!lista && !errorCarga && <Cargando texto="Cargando desarrolladores…" />}

      {lista && (
        <>
          <section className="tarjeta resumen-especialidad" aria-labelledby="titulo-resumen">
            <div className="resumen-especialidad__cabecera">
              <h2 className="tarjeta__titulo" id="titulo-resumen">Desarrolladores activos por especialidad</h2>
              <a className="enlace-flecha" href="?estado=sin-equipo" onClick={(e) => { e.preventDefault(); setFiltro('sin-equipo'); }}>
                <span>{conteo['sin-equipo']}</span>&nbsp;disponibles sin equipo
              </a>
            </div>
            <ul className="resumen-especialidad__lista">
              {porEspecialidad.map((e) => (
                <li key={e.valor} className="resumen-especialidad__item">
                  <span className="resumen-especialidad__numero">{e.total}</span>
                  <span className="resumen-especialidad__texto">{e.texto}</span>
                </li>
              ))}
            </ul>
          </section>

          <div>
            <div className="herramientas">
              <div className="filtros" role="group" aria-label="Filtrar desarrolladores">
                {FILTROS.map((f) => (
                  <button key={f.valor} type="button" className="filtro" aria-pressed={filtro === f.valor} onClick={() => setFiltro(f.valor)}>
                    {f.texto} <span className="filtro__cantidad">{conteo[f.valor]}</span>
                  </button>
                ))}
              </div>
              <div className="herramientas__derecha">
                <label>
                  <span className="solo-lector">Filtrar por especialidad</span>
                  <select className="campo__control select" value={especialidad} onChange={(e) => setEspecialidad(e.target.value)}>
                    <option value="">Todas las especialidades</option>
                    {ESPECIALIDADES.map((e) => <option key={e.valor} value={e.valor}>{e.texto}</option>)}
                  </select>
                </label>
                <label className="buscador">
                  <span className="solo-lector">Buscar desarrollador</span>
                  <Icono nombre="lupa" />
                  <input className="campo__control" type="search" placeholder="Buscar nombre o correo" value={busqueda}
                         onChange={(e) => setBusqueda(e.target.value)} />
                </label>
              </div>
            </div>

            <Tabla
              titulo="Registro de desarrolladores"
              columnas={columnas}
              filas={visibles}
              claveFila={(d) => d.desarrolladorId}
              claseFila={(d) => [!d.activo && 'fila--apagada', resaltado === d.desarrolladorId && 'esta-resaltada'].filter(Boolean).join(' ')}
              vacio={
                <EstadoVacio titulo="No hay desarrolladores con estos filtros" texto="Prueba con otra especialidad u otra búsqueda."
                             accion={<Boton variante="contorno" tamano="pequeno" onClick={limpiar}>Limpiar filtros</Boton>} />
              }
            />
            <p className="pie-tabla" aria-live="polite">{resultados(visibles.length)}</p>
          </div>
        </>
      )}

      {editando && (
        <FormularioDesarrollador
          desarrollador={editando === 'nuevo' ? null : editando}
          alCerrar={() => setEditando(null)}
          alGuardar={async (d, nuevo) => {
            await recargar();
            setResaltado(d.desarrolladorId);
            setEditando(null);
            notificar(nuevo ? `${d.nombre} quedó registrado. Ya puedes sumarlo a un equipo.` : `Datos de ${d.nombre} actualizados.`);
          }}
        />
      )}

      {cambiando && (
        <DialogoEstado
          desarrollador={cambiando}
          alCerrar={() => setCambiando(null)}
          alTerminar={async (d, mensaje) => {
            await recargar();
            setResaltado(d.desarrolladorId);
            setCambiando(null);
            notificar(mensaje);
          }}
        />
      )}
    </>
  );
}

// ---------------------------------------------------------------- Registrar o editar

function FormularioDesarrollador({ desarrollador, alCerrar, alGuardar }: {
  desarrollador: Desarrollador | null;
  alCerrar: () => void;
  alGuardar: (d: Desarrollador, nuevo: boolean) => Promise<void>;
}) {
  const idFormulario = useId();
  const { formulario, enfocar } = useEnfocarError();
  const [datos, setDatos] = useState<DatosDesarrollador>(desarrollador
    ? { nombre: desarrollador.nombre, celular: desarrollador.celular, correo: desarrollador.correo, especialidad: desarrollador.especialidad }
    : { nombre: '', celular: '', correo: '', especialidad: '' });
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const cambiar = (campo: keyof DatosDesarrollador, valor: string) => setDatos((d) => ({ ...d, [campo]: valor }));

  // Si cambia la especialidad de alguien cuyo rol ya no corresponde, se avisa (HU-25)
  const rol = desarrollador?.rol;
  const posibles = datos.especialidad ? ROLES_POSIBLES[datos.especialidad] : [];
  const rolChoca = !!desarrollador && !!rol && rol !== 'PRODUCT_OWNER' && posibles.length > 0 && !posibles.includes(rol);

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos = validarDesarrollador(datos);
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return enfocar();
    const limpios = { ...datos, nombre: datos.nombre.trim().replace(/\s+/g, ' '), celular: datos.celular.trim(), correo: datos.correo.trim().toLowerCase() };
    setGuardando(true);
    try {
      const guardado = desarrollador
        ? await apiDesarrolladores.editar(desarrollador.desarrolladorId, limpios)
        : await apiDesarrolladores.registrar(limpios);
      await alGuardar(guardado, !desarrollador);
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
      alCerrar={alCerrar}
      ancho
      icono="codigo"
      titulo={desarrollador ? 'Editar desarrollador' : 'Registrar desarrollador'}
      texto={desarrollador
        ? (esProductOwner(desarrollador) ? 'Es Product Owner: su correo es también su usuario para iniciar sesión.' : 'Los cambios quedan registrados en la auditoría.')
        : 'Queda en el registro de CoreTech. Sin cuenta en la plataforma: solo el Product Owner de cada equipo entra a la plataforma.'}
      acciones={
        <>
          <Boton variante="contorno" onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form={idFormulario} cargando={guardando}>{desarrollador ? 'Guardar cambios' : 'Registrar'}</Boton>
        </>
      }
    >
      <form id={idFormulario} ref={formulario} onSubmit={guardar} noValidate>
        {general && <Alerta tipo="error" titulo={general} />}
        <div className="fila-campos">
          <Campo etiqueta="Nombre completo" error={errores.nombre}>
            <input name="nombre" type="text" autoComplete="off" maxLength={60} placeholder="Ejemplo: Daniela Castro"
                   value={datos.nombre} onChange={(e) => cambiar('nombre', e.target.value)} />
          </Campo>
          <Campo etiqueta="Celular" error={errores.celular}>
            <input name="celular" type="tel" inputMode="numeric" autoComplete="off" maxLength={10} placeholder="3001234567"
                   value={datos.celular} onChange={(e) => cambiar('celular', e.target.value)} />
          </Campo>
        </div>
        <Campo etiqueta="Correo corporativo" error={errores.correo}>
          <input name="correo" type="email" autoComplete="off" maxLength={120} placeholder="nombre.apellido@coretechsolutions.co"
                 value={datos.correo} onChange={(e) => cambiar('correo', e.target.value)} />
        </Campo>
        <Campo etiqueta="Especialidad" error={errores.especialidad}
               ayuda="Define qué rol puede tener en un equipo. Un Full stack puede ser Frontend, Backend o Full stack.">
          <select className="select" name="especialidad" value={datos.especialidad} onChange={(e) => cambiar('especialidad', e.target.value)}>
            <option value="">Elige una especialidad</option>
            {ESPECIALIDADES.map((e) => <option key={e.valor} value={e.valor}>{e.texto}</option>)}
          </select>
        </Campo>
        {rolChoca && desarrollador?.equipo && rol && (
          <div className="alerta alerta--info aviso-rol">
            <Icono nombre="informacion" />
            <p>
              En el equipo de {desarrollador.equipo.productOwner} tiene el rol {textoRol(rol)}. Con esta especialidad puede ser{' '}
              {posibles.map(textoRol).join(', ')}: al guardar, su rol cambia a {textoRol(posibles[0] as Especialidad)}.
            </p>
          </div>
        )}
      </form>
    </Dialogo>
  );
}

// ---------------------------------------------------------------- Desactivar y activar

function DialogoEstado({ desarrollador: d, alCerrar, alTerminar }: {
  desarrollador: Desarrollador;
  alCerrar: () => void;
  alTerminar: (d: Desarrollador, mensaje: string) => Promise<void>;
}) {
  const [error, setError] = useState<string | null>(null);
  const [trabajando, setTrabajando] = useState(false);
  const activar = !d.activo;
  const po = esProductOwner(d);

  async function confirmar() {
    setTrabajando(true);
    setError(null);
    try {
      const resultado = await apiDesarrolladores.cambiarActivo(d.desarrolladorId, activar);
      await alTerminar(resultado, activar
        ? `${d.nombre} está activo de nuevo.`
        : `${d.nombre} quedó inactivo${d.equipo && !po ? ` y salió del equipo de ${d.equipo.productOwner}.` : '.'}`);
    } catch (e) {
      setError(separarErrores(e).general ?? 'No se pudo completar la acción.');
      setTrabajando(false);
    }
  }

  return (
    <Dialogo
      abierto
      alCerrar={alCerrar}
      titulo={`${activar ? 'Activar' : 'Desactivar'} desarrollador`}
      texto={activar
        ? `${d.nombre} vuelve a estar disponible para sumarse a un equipo.`
        : `${d.nombre} queda en el registro como inactivo y no se puede asignar a equipos.${d.equipo && !po ? ` Saldrá del equipo de ${d.equipo.productOwner}.` : ''}${po ? ' Su cuenta quedará bloqueada hasta que la reactives.' : ''}`}
      icono={activar ? 'exito' : 'apagar'}
      tono={activar ? 'acento' : 'peligro'}
      acciones={
        <>
          <Boton variante="contorno" onClick={alCerrar}>Volver</Boton>
          <Boton variante={activar ? 'acento' : 'peligro'} cargando={trabajando} onClick={() => void confirmar()}>
            {activar ? 'Activar' : 'Desactivar'}
          </Boton>
        </>
      }
    >
      {error && (
        <div className="alerta alerta--advertencia aviso-proyectos" role="alert">
          <Icono nombre="alerta" />
          <div>
            <strong>{error}</strong>
            {d.equipo && (
              <p><Link className="boton boton--oscuro boton--pequeno" to={`/admin/especialidad/equipos#equipo-${slug(d.equipo.productOwner)}`}>Ir a su equipo</Link></p>
            )}
          </div>
        </div>
      )}
    </Dialogo>
  );
}
