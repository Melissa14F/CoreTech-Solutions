import { useEffect, useId, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { usuarios as apiUsuarios, type DatosCuenta, type UsuarioGestion } from '../../administracion/api';
import { iniciales, plural, resultados, ultimoAcceso } from '../../administracion/formato';
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
import { useUsuario } from '../../sesion/ContextoSesion';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-usuarios.css';

const POR_PAGINA = 20;
const DOMINIO = '@coretechsolutions.co';

type Filtro = 'todas' | 'PRODUCT_OWNER' | 'CLIENTE' | 'ADMINISTRADOR' | 'inactivo';
const FILTROS: { valor: Filtro; texto: string }[] = [
  { valor: 'todas', texto: 'Todos' },
  { valor: 'PRODUCT_OWNER', texto: 'Product Owners' },
  { valor: 'CLIENTE', texto: 'Clientes' },
  { valor: 'ADMINISTRADOR', texto: 'Administradores' },
  { valor: 'inactivo', texto: 'Inactivos' },
];

type Confirmacion = 'desactivar' | 'activar' | 'clave' | 'desbloquear' | 'anonimizar';
type Errores = Partial<Record<keyof DatosCuenta, string>>;

const sinTildes = (texto: string) => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const slug = (nombre: string) => sinTildes(nombre).replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
const primerNombre = (nombre: string) => nombre.split(' ')[0] ?? nombre;

/** Mismas reglas y mensajes que el backend (ServicioUsuarios) y prototipo/admin/usuarios.html. */
export function validarCuenta(d: DatosCuenta, personal: boolean): Errores {
  const e: Errores = {};
  const nombre = d.nombre.trim().replace(/\s+/g, ' ');
  if (!nombre) e.nombre = 'Escribe el nombre completo.';
  else if (nombre.length < (personal ? 5 : 3)) e.nombre = 'Escribe nombre y apellido.';
  else if (nombre.length > 60) e.nombre = 'Escribe máximo 60 caracteres.';
  else if (!/^[\p{L} ]+$/u.test(nombre)) e.nombre = 'Escribe solo letras y espacios.';
  const celular = d.celular.trim();
  if (!celular) e.celular = 'Escribe el número de celular.';
  else if (!/^3\d{9}$/.test(celular)) e.celular = 'Escribe un celular de 10 dígitos que empiece por 3.';
  const email = d.email.trim().toLowerCase();
  if (!email) e.email = 'Escribe el correo.';
  else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) e.email = 'Escribe un correo válido, por ejemplo nombre@empresa.com.';
  else if (personal && !email.endsWith(DOMINIO)) e.email = 'Usa el correo corporativo que termina en @coretechsolutions.co.';
  return e;
}

function Rol({ rol }: { rol: UsuarioGestion['rol'] }) {
  if (rol === 'ADMINISTRADOR') return <Insignia variante="oscuro">Administrador</Insignia>;
  if (rol === 'PRODUCT_OWNER') return <Insignia variante="activo">Product Owner</Insignia>;
  return <Insignia variante="cliente">Cliente</Insignia>;
}

/** Columna "Actividad" del prototipo. */
function Actividad({ u }: { u: UsuarioGestion }) {
  const pequeno = (texto: string) => <span className="celda-actividad"><span className="celda-principal"><small>{texto}</small></span></span>;
  if (u.anonimizada) return pequeno('Datos anonimizados (Ley 1581)');
  if (u.rol === 'ADMINISTRADOR') return pequeno('Gestiona todo el sistema');
  if (u.rol === 'PRODUCT_OWNER') {
    const equipo = u.actividad.equipo;
    if (!equipo) return pequeno('Ya no lidera un equipo');
    const texto = `${equipo.proyectosActivos} de ${equipo.maxProyectos} proyectos activos`;
    return (
      <span className="celda-actividad">
        <span className="celda-principal"><strong>Equipo de {u.nombre}</strong><small>{texto}</small></span>
        <span className="carga" role="img" aria-label={texto}>
          {Array.from({ length: equipo.maxProyectos }, (_, i) => (
            <span key={i} className={['carga__unidad', i < equipo.proyectosActivos && 'carga__unidad--ocupada'].filter(Boolean).join(' ')} />
          ))}
        </span>
      </span>
    );
  }
  const a = u.actividad;
  const partes = [
    a.proyectosEnCurso > 0 && plural(a.proyectosEnCurso, 'proyecto', 'proyectos'),
    a.proyectosFinalizados > 0 && plural(a.proyectosFinalizados, 'proyecto finalizado', 'proyectos finalizados'),
    a.citasPendientes > 0 && plural(a.citasPendientes, 'cita pendiente', 'citas pendientes'),
    a.citasConfirmadas > 0 && plural(a.citasConfirmadas, 'cita confirmada', 'citas confirmadas'),
    a.citasAtendidas > 0 && a.proyectosEnCurso + a.proyectosFinalizados === 0 && plural(a.citasAtendidas, 'cita atendida', 'citas atendidas'),
  ].filter(Boolean);
  if (partes.length > 0) return pequeno(partes.join(' · '));
  return pequeno(a.citasCanceladas > 0 ? 'Sin actividad: canceló su cita' : 'Sin actividad todavía');
}

function Estado({ u }: { u: UsuarioGestion }) {
  if (!u.activo) return <Insignia variante="cancelada">Inactivo</Insignia>;
  if (u.bloqueadaHasta) return <Insignia variante="vencido">Bloqueada</Insignia>;
  return <Insignia variante="atendida">Activo</Insignia>;
}

/** Gestionar las cuentas de usuario (HU-20, RN-18, RN-26; prototipo/admin/usuarios.html). */
export function PaginaUsuarios() {
  useTituloDocumento('Usuarios');
  const notificar = useNotificar();
  const yo = useUsuario();
  const [lista, setLista] = useState<UsuarioGestion[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(0);
  const [editando, setEditando] = useState<UsuarioGestion | 'nuevo' | null>(null);
  const [confirmando, setConfirmando] = useState<{ tipo: Confirmacion; usuario: UsuarioGestion } | null>(null);
  const [resaltado, setResaltado] = useState<string | null>(null);

  useEffect(() => {
    apiUsuarios.listar().then(setLista).catch((e) => setErrorCarga(separarErrores(e).general ?? 'No pudimos cargar los usuarios.'));
  }, []);

  const conteo = useMemo(() => {
    const todos = lista ?? [];
    return {
      todas: todos.length,
      PRODUCT_OWNER: todos.filter((u) => u.rol === 'PRODUCT_OWNER').length,
      CLIENTE: todos.filter((u) => u.rol === 'CLIENTE').length,
      ADMINISTRADOR: todos.filter((u) => u.rol === 'ADMINISTRADOR').length,
      inactivo: todos.filter((u) => !u.activo).length,
    } satisfies Record<Filtro, number>;
  }, [lista]);

  const visibles = useMemo(() => {
    const texto = sinTildes(busqueda.trim());
    return (lista ?? []).filter((u) =>
      (filtro === 'todas' || (filtro === 'inactivo' ? !u.activo : u.rol === filtro))
      && (!texto || sinTildes(`${u.nombre} ${u.email}`).includes(texto)));
  }, [lista, filtro, busqueda]);

  const totalPaginas = Math.max(1, Math.ceil(visibles.length / POR_PAGINA));
  const paginaActual = Math.min(pagina, totalPaginas - 1);
  const filas = visibles.slice(paginaActual * POR_PAGINA, (paginaActual + 1) * POR_PAGINA);

  const reemplazar = (u: UsuarioGestion) => {
    setLista((actual) => {
      const todos = actual ?? [];
      return todos.some((x) => x.usuarioId === u.usuarioId)
        ? todos.map((x) => (x.usuarioId === u.usuarioId ? u : x))
        : [...todos.slice(0, 1), u, ...todos.slice(1)]; // la cuenta nueva, debajo de la primera
    });
    setResaltado(u.usuarioId);
  };

  const limpiar = () => {
    setFiltro('todas');
    setBusqueda('');
    setPagina(0);
  };

  const opciones = (u: UsuarioGestion): OpcionMenu[] => {
    const abrir = (tipo: Confirmacion) => () => setConfirmando({ tipo, usuario: u });
    const lista: (OpcionMenu | false)[] = [
      u.rol === 'PRODUCT_OWNER' && !!u.actividad.equipo && { texto: 'Ver su equipo', icono: 'usuarioMas', a: `/admin/especialidad/equipos#equipo-${slug(u.nombre)}` },
      u.rol === 'PRODUCT_OWNER' && { texto: 'Ver sus proyectos', icono: 'carpeta', a: `/admin/proyectos?buscar=${encodeURIComponent(primerNombre(u.nombre) ?? u.nombre)}` },
      u.rol === 'CLIENTE' && { texto: 'Ver sus citas', icono: 'calendario', a: `/admin/citas?buscar=${encodeURIComponent(u.nombre)}` },
      u.activo && { texto: 'Restablecer contraseña', icono: 'llave', alElegir: abrir('clave') },
      !!u.bloqueadaHasta && u.activo && { texto: 'Desbloquear cuenta', icono: 'candadoAbierto', alElegir: abrir('desbloquear') },
      u.rol === 'CLIENTE' && { texto: 'Anonimizar datos', icono: 'ojoTachado', alElegir: abrir('anonimizar'), peligro: true },
      u.activo
        ? { texto: 'Desactivar cuenta', icono: 'apagar', alElegir: abrir('desactivar'), peligro: true }
        : { texto: 'Activar cuenta', icono: 'exito', alElegir: abrir('activar') },
    ];
    return lista.filter((o): o is OpcionMenu => o !== false);
  };

  const columnas: ColumnaTabla<UsuarioGestion>[] = [
    {
      clave: 'usuario', titulo: 'Usuario', ancha: true, celda: (u) => (
        <span className="celda-usuario">
          <span className={['avatar', u.rol === 'ADMINISTRADOR' && 'avatar--gris', u.rol === 'CLIENTE' && 'avatar--acento'].filter(Boolean).join(' ')} aria-hidden="true">
            {iniciales(u.nombre)}
          </span>
          <span className="celda-principal"><strong>{u.nombre}</strong><small>{u.email}</small></span>
        </span>
      ),
    },
    { clave: 'rol', titulo: 'Rol', celda: (u) => <Rol rol={u.rol} /> },
    { clave: 'actividad', titulo: 'Actividad', celda: (u) => <Actividad u={u} /> },
    { clave: 'acceso', titulo: 'Último acceso', celda: (u) => <span className="celda-principal"><small>{ultimoAcceso(u.ultimoAcceso)}</small></span> },
    { clave: 'estado', titulo: 'Estado', celda: (u) => <Estado u={u} /> },
    {
      clave: 'acciones', titulo: 'Acciones', tituloOculto: true, celda: (u) => (
        <div className="celda-acciones">
          {u.anonimizada ? (
            <span className="etiqueta-tu">Datos anonimizados</span>
          ) : (
            <Boton variante="contorno" tamano="pequeno" icono="editar" onClick={() => setEditando(u)} aria-label={`Editar a ${u.nombre}`}>Editar</Boton>
          )}
          {u.usuarioId === yo?.usuarioId
            ? <span className="etiqueta-tu">Tu cuenta</span>
            : !u.anonimizada && <MenuAcciones etiqueta={`Más acciones para ${u.nombre}`} opciones={opciones(u)} />}
        </div>
      ),
    },
  ];

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Usuarios</h1>
          <p className="encabezado-vista__texto">
            Cuentas para entrar a la plataforma. Los clientes se registran solos desde el sitio y la cuenta de cada Product Owner se crea al armar su equipo en <Link to="/admin/especialidad/equipos">Equipos</Link>.
          </p>
        </div>
        <Boton icono="usuarioMas" onClick={() => setEditando('nuevo')}>Crear cuenta de administrador</Boton>
      </div>

      {errorCarga && <Alerta tipo="error" titulo={errorCarga} />}
      {!lista && !errorCarga && <Cargando texto="Cargando usuarios…" />}

      {lista && (
        <div>
          <div className="herramientas">
            <div className="filtros" role="group" aria-label="Filtrar por rol">
              {FILTROS.map((f) => (
                <button key={f.valor} type="button" className="filtro" aria-pressed={filtro === f.valor}
                        onClick={() => { setFiltro(f.valor); setPagina(0); }}>
                  {f.texto} <span className="filtro__cantidad">{conteo[f.valor]}</span>
                </button>
              ))}
            </div>
            <div className="herramientas__derecha">
              <label className="buscador">
                <span className="solo-lector">Buscar usuario</span>
                <Icono nombre="lupa" />
                <input className="campo__control" type="search" placeholder="Buscar nombre o correo" value={busqueda}
                       onChange={(e) => { setBusqueda(e.target.value); setPagina(0); }} />
              </label>
            </div>
          </div>

          <Tabla
            titulo="Usuarios del sistema"
            columnas={columnas}
            filas={filas}
            claveFila={(u) => u.usuarioId}
            claseFila={(u) => [!u.activo && 'fila--apagada', resaltado === u.usuarioId && 'esta-resaltada'].filter(Boolean).join(' ')}
            vacio={
              <EstadoVacio titulo="No hay usuarios con estos filtros" texto="Prueba con otro rol u otra búsqueda."
                           accion={<Boton variante="contorno" tamano="pequeno" onClick={limpiar}>Limpiar filtros</Boton>} />
            }
          />
          <p className="pie-tabla" aria-live="polite">{resultados(visibles.length)}</p>
          <Paginacion pagina={paginaActual} totalPaginas={totalPaginas} alCambiar={setPagina} etiqueta="Páginas de usuarios" />
        </div>
      )}

      {editando && (
        <FormularioCuenta
          usuario={editando === 'nuevo' ? null : editando}
          alCerrar={() => setEditando(null)}
          alGuardar={(u, nuevo) => {
            reemplazar(u);
            setEditando(null);
            notificar(nuevo ? `Cuenta creada. Enviamos los datos de acceso a ${u.email}.` : `Datos de ${u.nombre} actualizados.`);
          }}
        />
      )}

      {confirmando && (
        <DialogoConfirmacion
          {...confirmando}
          alCerrar={() => setConfirmando(null)}
          alTerminar={(u, mensaje) => {
            if (u) reemplazar(u);
            setConfirmando(null);
            notificar(mensaje);
          }}
        />
      )}
    </>
  );
}

// ---------------------------------------------------------------- Crear o editar una cuenta

function FormularioCuenta({ usuario, alCerrar, alGuardar }: {
  usuario: UsuarioGestion | null;
  alCerrar: () => void;
  alGuardar: (u: UsuarioGestion, nuevo: boolean) => void;
}) {
  const idFormulario = useId();
  const { formulario, enfocar } = useEnfocarError();
  const personal = !usuario || usuario.rol !== 'CLIENTE';
  const [datos, setDatos] = useState<DatosCuenta>(usuario
    ? { nombre: usuario.nombre, celular: usuario.celular, email: usuario.email }
    : { nombre: '', celular: '', email: '' });
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const cambiar = (campo: keyof DatosCuenta, valor: string) => setDatos((d) => ({ ...d, [campo]: valor }));

  const tipo = !usuario ? null : usuario.rol === 'CLIENTE' ? 'cliente' : usuario.rol === 'ADMINISTRADOR' ? 'administrador' : 'Product Owner';

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos = validarCuenta(datos, personal);
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return enfocar();
    const limpios = { nombre: datos.nombre.trim().replace(/\s+/g, ' '), celular: datos.celular.trim(), email: datos.email.trim().toLowerCase() };
    setGuardando(true);
    try {
      alGuardar(usuario ? await apiUsuarios.editar(usuario.usuarioId, limpios) : await apiUsuarios.crear(limpios), !usuario);
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
      icono="usuarioMas"
      titulo={tipo ? `Editar ${tipo}` : 'Crear cuenta de administrador'}
      texto={usuario ? 'Los cambios quedan registrados en la auditoría.' : 'Le llega un correo con sus datos de acceso. Las cuentas de Product Owner se crean al armar un equipo.'}
      acciones={
        <>
          <Boton variante="contorno" onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form={idFormulario} cargando={guardando}>{usuario ? 'Guardar cambios' : 'Crear cuenta'}</Boton>
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
        <Campo etiqueta="Correo" error={errores.email}
               ayuda={personal ? 'Será su usuario para iniciar sesión.' : 'Si cambia el correo, el cliente iniciará sesión con el nuevo.'}>
          <input name="email" type="email" autoComplete="off" maxLength={120}
                 placeholder={personal ? 'nombre.apellido@coretechsolutions.co' : 'nombre@empresa.com'}
                 value={datos.email} onChange={(e) => cambiar('email', e.target.value)} />
        </Campo>
        {!usuario && (
          <div className="clave-temporal">
            <div>
              <p className="clave-temporal__titulo">Contraseña temporal</p>
              <p className="clave-temporal__ayuda clave-temporal__ayuda--sola">
                La generamos al crear la cuenta y le llegará por correo. Deberá cambiarla al iniciar sesión por primera vez.
              </p>
            </div>
          </div>
        )}
      </form>
    </Dialogo>
  );
}

// ---------------------------------------------------------------- Confirmaciones

const TEXTOS: Record<Confirmacion, { titulo: string; boton: string; icono: 'apagar' | 'exito' | 'llave' | 'candadoAbierto' | 'ojoTachado'; peligro: boolean }> = {
  desactivar: { titulo: 'Desactivar cuenta', boton: 'Desactivar', icono: 'apagar', peligro: true },
  activar: { titulo: 'Activar cuenta', boton: 'Activar', icono: 'exito', peligro: false },
  clave: { titulo: 'Restablecer contraseña', boton: 'Enviar enlace', icono: 'llave', peligro: false },
  desbloquear: { titulo: 'Desbloquear cuenta', boton: 'Desbloquear', icono: 'candadoAbierto', peligro: false },
  anonimizar: { titulo: 'Anonimizar datos', boton: 'Anonimizar datos', icono: 'ojoTachado', peligro: true },
};

function DialogoConfirmacion({ tipo, usuario: u, alCerrar, alTerminar }: {
  tipo: Confirmacion;
  usuario: UsuarioGestion;
  alCerrar: () => void;
  alTerminar: (u: UsuarioGestion | null, mensaje: string) => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const [trabajando, setTrabajando] = useState(false);
  const config = TEXTOS[tipo];
  const activos = u.actividad.equipo?.proyectosActivos ?? 0;
  // RN-26: un Product Owner con proyectos activos no se desactiva sin nombrar otro para su equipo
  const bloqueado = tipo === 'desactivar' && u.rol === 'PRODUCT_OWNER' && activos > 0;

  const texto: Record<Confirmacion, ReactNode> = {
    desactivar: `${u.nombre} no podrá iniciar sesión. Sus datos, citas e historial se conservan y puedes activarlo cuando quieras.`,
    activar: `${u.nombre} podrá volver a iniciar sesión con su correo ${u.email}.`,
    clave: `Enviaremos a ${u.email} un enlace para crear una contraseña nueva. El enlace vale 30 minutos y se usa una sola vez.`,
    desbloquear: `${u.nombre} podrá volver a iniciar sesión de inmediato, sin esperar los 15 minutos del bloqueo.`,
    anonimizar: `El nombre, el correo, el celular y la empresa de ${u.nombre} se reemplazarán por valores anónimos y la cuenta quedará desactivada. Sus citas y proyectos se conservan. Esta acción no se puede deshacer.`,
  };

  async function confirmar() {
    setTrabajando(true);
    setError(null);
    try {
      switch (tipo) {
        case 'desactivar':
          return alTerminar(await apiUsuarios.cambiarActivo(u.usuarioId, false), `Cuenta de ${u.nombre} desactivada.`);
        case 'activar':
          return alTerminar(await apiUsuarios.cambiarActivo(u.usuarioId, true), `Cuenta de ${u.nombre} activada.`);
        case 'clave': {
          const { enviadoA } = await apiUsuarios.enviarRecuperacion(u.usuarioId);
          return alTerminar(null, `Enviamos el enlace para restablecer la contraseña a ${enviadoA}.`);
        }
        case 'desbloquear':
          return alTerminar(await apiUsuarios.desbloquear(u.usuarioId), `Cuenta de ${u.nombre} desbloqueada.`);
        case 'anonimizar':
          return alTerminar(await apiUsuarios.anonimizar(u.usuarioId), `Los datos de ${u.nombre} quedaron anonimizados.`);
      }
    } catch (e) {
      setError(separarErrores(e).general ?? 'No se pudo completar la acción.');
      setTrabajando(false);
    }
  }

  return (
    <Dialogo
      abierto
      alCerrar={alCerrar}
      titulo={config.titulo}
      texto={texto[tipo]}
      icono={config.icono}
      tono={config.peligro ? 'peligro' : 'acento'}
      acciones={
        <>
          <Boton variante="contorno" onClick={alCerrar}>Volver</Boton>
          <Boton variante={config.peligro ? 'peligro' : 'acento'} cargando={trabajando} disabled={bloqueado} onClick={() => void confirmar()}>
            {config.boton}
          </Boton>
        </>
      }
    >
      {(bloqueado || error) && (
        <>
          {bloqueado && (
            <div className="alerta alerta--advertencia aviso-proyectos" role="alert">
              <Icono nombre="alerta" />
              <div>
                <strong>{u.nombre} tiene {plural(activos, 'proyecto activo', 'proyectos activos')}.</strong>
                <p>Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo, para que ningún proyecto quede sin responsable.</p>
                <Link className="boton boton--oscuro boton--pequeno" to={`/admin/especialidad/equipos#equipo-${slug(u.nombre)}`}>Ir a su equipo</Link>
              </div>
            </div>
          )}
          {error && <Alerta tipo="error" titulo={error} />}
        </>
      )}
    </Dialogo>
  );
}
