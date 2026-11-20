import { useEffect, useMemo, useState } from 'react';

import {
  auditoria as apiAuditoria, type FiltrosAuditoria, type PaginaAuditoria, type RegistroAuditoria, type ResumenAuditoria,
} from '../../administracion/api';
import { diaColombia, fechaEvento, iniciales, resultados } from '../../administracion/formato';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { Icono } from '../../componentes/Icono';
import { useNotificar } from '../../componentes/Notificaciones';
import { Paginacion } from '../../componentes/Paginacion';
import { Tabla, type ColumnaTabla } from '../../componentes/Tabla';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-auditoria.css';

const POR_PAGINA = 20;

type Periodo = '' | 'hoy' | 'semana' | 'mes' | 'rango';

/** Nombres de los campos que guardan los servicios (auditoria.cambios), como los lee el administrador. */
const CAMPOS: Record<string, string> = {
  nombre: 'Nombre', email: 'Correo', celular: 'Celular', empresa: 'Empresa', rol: 'Rol', activo: 'Cuenta activa',
  estado: 'Estado', intentosFallidos: 'Intentos fallidos', bloqueadoHasta: 'Bloqueada hasta',
  debeCambiarPassword: 'Contraseña temporal', datosPersonales: 'Datos personales', registros: 'Registros exportados',
  descripcionCorta: 'Descripción', imagenUrl: 'Imagen', imagenAlt: 'Texto alternativo', visible: 'Visible en el catálogo',
  orden: 'Orden en el carrusel', slug: 'Dirección',
};

/** "descripcionCorta" → "Descripcion corta" cuando el campo no está en la lista. */
export function nombreCampo(campo: string): string {
  if (CAMPOS[campo]) return CAMPOS[campo];
  const texto = campo.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[-_]/g, ' ').toLowerCase();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

const ISO_FECHA_HORA = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;

export function valorCambio(valor: unknown): string {
  if (valor === null || valor === undefined || valor === '') return '—';
  if (valor === true) return 'Sí';
  if (valor === false) return 'No';
  if (typeof valor === 'string' && ISO_FECHA_HORA.test(valor)) return fechaEvento(valor);
  return String(valor);
}

const ROL: Record<string, string> = { ADMINISTRADOR: 'Administrador', PRODUCT_OWNER: 'Product Owner', CLIENTE: 'Cliente' };

/** Rango de fechas (días de Colombia) del periodo elegido. */
function rangoDe(periodo: Periodo, propio: { desde: string; hasta: string }): { desde?: string; hasta?: string } {
  const hoy = diaColombia(new Date());
  const restar = (dias: number) => diaColombia(new Date(Date.parse(`${hoy}T12:00:00-05:00`) - dias * 86_400_000));
  switch (periodo) {
    case 'hoy': return { desde: hoy, hasta: hoy };
    case 'semana': return { desde: restar(6), hasta: hoy };
    case 'mes': return { desde: `${hoy.slice(0, 8)}01`, hasta: hoy };
    case 'rango': return { desde: propio.desde || undefined, hasta: propio.hasta || undefined };
    default: return {};
  }
}

/** HU-22: mismas reglas que el backend para el rango propio. */
function errorRango(desde: string, hasta: string): string | null {
  if (!desde || !hasta) return null;
  if (desde > hasta) return 'La fecha final debe ser igual o posterior a la inicial.';
  const unAnio = `${Number(desde.slice(0, 4)) + 1}${desde.slice(4)}`;
  return hasta > unAnio ? 'Elige un rango de máximo un año.' : null;
}

/** Consultar la auditoría (HU-22, RN-20, RNF-02; prototipo/admin/auditoria.html). Solo lectura. */
export function PaginaAuditoria() {
  useTituloDocumento('Auditoría');
  const notificar = useNotificar();
  const [resumen, setResumen] = useState<ResumenAuditoria | null>(null);
  const [datos, setDatos] = useState<PaginaAuditoria | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  const [categoria, setCategoria] = useState('');
  const [usuarioId, setUsuarioId] = useState('');
  const [periodo, setPeriodo] = useState<Periodo>('');
  const [propio, setPropio] = useState({ desde: '', hasta: '' });
  const [busqueda, setBusqueda] = useState('');
  const [q, setQ] = useState('');
  const [pagina, setPagina] = useState(0);

  // La búsqueda se envía cuando la persona deja de escribir
  useEffect(() => {
    const espera = window.setTimeout(() => { setQ(busqueda.trim()); setPagina(0); }, 300);
    return () => window.clearTimeout(espera);
  }, [busqueda]);

  useEffect(() => {
    apiAuditoria.resumen().then(setResumen).catch(() => undefined);
  }, []);

  const rangoInvalido = periodo === 'rango' ? errorRango(propio.desde, propio.hasta) : null;
  const filtros: FiltrosAuditoria = useMemo(() => ({ q, usuarioId, ...rangoDe(periodo, propio) }), [q, usuarioId, periodo, propio]);

  useEffect(() => {
    if (rangoInvalido) return;
    let vigente = true;
    setCargando(true);
    apiAuditoria.consultar({ ...filtros, categoria }, pagina, POR_PAGINA)
      .then((d) => { if (vigente) { setDatos(d); setError(null); } })
      .catch((e) => { if (vigente) setError(separarErrores(e).general ?? Object.values(separarErrores(e).porCampo)[0] ?? 'No pudimos cargar la auditoría.'); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [filtros, categoria, pagina, rangoInvalido]);

  const totalSinCategoria = Object.values(datos?.porCategoria ?? {}).reduce((a, b) => a + b, 0);

  function limpiar() {
    setCategoria('');
    setUsuarioId('');
    setPeriodo('');
    setPropio({ desde: '', hasta: '' });
    setBusqueda('');
    setQ('');
    setPagina(0);
  }

  function exportar() {
    if (!datos || datos.total === 0) {
      notificar('No hay eventos para exportar con estos filtros.', 'error');
      return;
    }
    const enlace = document.createElement('a');
    enlace.href = apiAuditoria.urlExportar({ ...filtros, categoria });
    enlace.download = '';
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    notificar(`Exportamos ${datos.total} ${datos.total === 1 ? 'evento' : 'eventos'}.`);
  }

  const columnas: ColumnaTabla<RegistroAuditoria>[] = [
    { clave: 'fecha', titulo: 'Fecha', celda: (r) => <time className="celda-hora" dateTime={r.fecha}>{fechaEvento(r.fecha)}</time> },
    {
      clave: 'usuario', titulo: 'Usuario', celda: (r) => (
        <span className="celda-persona">
          {r.usuario ? (
            <span className={['avatar', r.usuario.rol === 'ADMINISTRADOR' && 'avatar--gris', r.usuario.rol === 'CLIENTE' && 'avatar--acento'].filter(Boolean).join(' ')} aria-hidden="true">
              {iniciales(r.usuario.nombre)}
            </span>
          ) : <span className="avatar avatar--alerta" aria-hidden="true">?</span>}
          <span className="celda-principal">
            <strong>{r.usuario?.nombre ?? 'Sin identificar'}</strong>
            <small>{r.usuario ? ROL[r.usuario.rol ?? ''] ?? 'Sin rol' : 'Sin sesión'}</small>
          </span>
        </span>
      ),
    },
    {
      clave: 'accion', titulo: 'Acción', ancha: true, clase: 'celda-evento', celda: (r) => (
        <>
          <span className="celda-principal">
            <strong>{r.accion} {r.alerta && <span className="insignia insignia--vencido">Seguridad</span>}</strong>
            <small>{r.elemento ?? '—'}</small>
          </span>
          <details className="detalle-evento">
            <summary>Ver detalle</summary>
            <div className="detalle-evento__cuerpo">
              {r.cambios.length === 0 ? (
                <p className="detalle-evento__sin-cambios">Sin cambios en los datos.</p>
              ) : (
                <ul className="cambios">
                  {r.cambios.map((c) => (
                    <li key={c.campo} className="cambio">
                      <span className="cambio__campo">{nombreCampo(c.campo)}</span>
                      <span className="cambio__antes">{valorCambio(c.anterior)}</span>
                      <Icono nombre="flechaDerecha" />
                      <span className="cambio__despues">{valorCambio(c.nuevo)}</span>
                    </li>
                  ))}
                </ul>
              )}
              <p className="detalle-evento__origen">
                Origen: {[r.ip, r.agente].filter(Boolean).join(' · ') || 'el sistema'} · Registro #{r.id}
              </p>
            </div>
          </details>
        </>
      ),
    },
    {
      clave: 'modulo', titulo: 'Módulo', celda: (r) => (
        <span className="modulo">{resumen?.categorias.find((c) => c.valor === r.categoria)?.nombre ?? r.categoria}</span>
      ),
    },
  ];

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Auditoría</h1>
          <p className="encabezado-vista__texto">
            Quién hizo qué, cuándo y desde dónde. El registro se guarda automáticamente y nadie lo puede editar ni borrar, ni siquiera el administrador.
          </p>
        </div>
        <Boton variante="contorno" icono="descargar" onClick={exportar}>Exportar CSV</Boton>
      </div>

      <ul className="resumen-auditoria" aria-label="Resumen de la auditoría">
        <li className="resumen-auditoria__dato">
          <span className="indicador__icono"><Icono nombre="calendario" grande /></span>
          <span><strong>{resumen?.eventosHoy ?? '…'}</strong><small>Eventos hoy</small></span>
        </li>
        <li className="resumen-auditoria__dato">
          <span className="indicador__icono indicador__icono--peligro"><Icono nombre="alerta" grande /></span>
          <span>
            <strong>{resumen?.alertasSemana ?? '…'}</strong>
            <small>{resumen?.alertasSemana === 1 ? 'Alerta de seguridad esta semana' : 'Alertas de seguridad esta semana'}</small>
          </span>
        </li>
        <li className="resumen-auditoria__dato">
          <span className="indicador__icono"><Icono nombre="escudo" grande /></span>
          <span>
            <strong>{resumen?.cambiosPermisosMes ?? '…'}</strong>
            <small>{resumen?.cambiosPermisosMes === 1 ? 'Cambio de permisos este mes' : 'Cambios de permisos este mes'}</small>
          </span>
        </li>
        <li className="resumen-auditoria__dato resumen-auditoria__dato--fijo">
          <span className="indicador__icono"><Icono nombre="candado" grande /></span>
          <span><strong>Inmutable</strong><small>Solo lectura (RNF-02)</small></span>
        </li>
      </ul>

      <div className="herramientas">
        <div className="filtros" role="group" aria-label="Filtrar por módulo">
          <button type="button" className="filtro" aria-pressed={categoria === ''} onClick={() => { setCategoria(''); setPagina(0); }}>
            Todo <span className="filtro__cantidad">{totalSinCategoria}</span>
          </button>
          {(resumen?.categorias ?? []).map((c) => (
            <button key={c.valor} type="button" className="filtro" aria-pressed={categoria === c.valor}
                    onClick={() => { setCategoria(c.valor); setPagina(0); }}>
              {c.nombre} <span className="filtro__cantidad">{datos?.porCategoria[c.valor] ?? 0}</span>
            </button>
          ))}
        </div>
        <div className="herramientas__derecha">
          <label className="buscador">
            <span className="solo-lector">Buscar en la auditoría</span>
            <Icono nombre="lupa" />
            <input className="campo__control" type="search" placeholder="Buscar acción, código o persona" maxLength={80}
                   value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
          </label>
          <label>
            <span className="solo-lector">Filtrar por usuario</span>
            <select className="campo__control select" value={usuarioId} onChange={(e) => { setUsuarioId(e.target.value); setPagina(0); }}>
              <option value="">Todos los usuarios</option>
              {(resumen?.usuarios ?? []).map((u) => <option key={u.usuarioId} value={u.usuarioId}>{u.nombre}</option>)}
            </select>
          </label>
          <label>
            <span className="solo-lector">Filtrar por periodo</span>
            <select className="campo__control select" value={periodo} onChange={(e) => { setPeriodo(e.target.value as Periodo); setPagina(0); }}>
              <option value="">Todo el historial</option>
              <option value="hoy">Hoy</option>
              <option value="semana">Últimos 7 días</option>
              <option value="mes">Este mes</option>
              <option value="rango">Elegir fechas…</option>
            </select>
          </label>
          {periodo === 'rango' && (
            <div className="rango-fechas" role="group" aria-label="Rango de fechas">
              <label>
                <span className="solo-lector">Desde</span>
                <input className="campo__control" type="date" value={propio.desde} aria-label="Desde"
                       aria-invalid={rangoInvalido ? true : undefined}
                       onChange={(e) => { setPropio((p) => ({ ...p, desde: e.target.value })); setPagina(0); }} />
              </label>
              <label>
                <span className="solo-lector">Hasta</span>
                <input className="campo__control" type="date" value={propio.hasta} aria-label="Hasta"
                       aria-invalid={rangoInvalido ? true : undefined}
                       onChange={(e) => { setPropio((p) => ({ ...p, hasta: e.target.value })); setPagina(0); }} />
              </label>
              {rangoInvalido && <p className="rango-fechas__error" role="alert">{rangoInvalido}</p>}
            </div>
          )}
        </div>
      </div>

      {error && <Alerta tipo="error" titulo={error} />}
      {!datos && cargando && !error && <Cargando texto="Cargando la auditoría…" />}

      {datos && (
        <>
          <Tabla
            titulo="Registro de auditoría, del más reciente al más antiguo"
            clase="tabla-auditoria"
            columnas={columnas}
            filas={datos.registros}
            claveFila={(r) => String(r.id)}
            claseFila={(r) => (r.alerta ? 'fila--atencion' : undefined)}
            vacio={
              <EstadoVacio titulo="No hay registros con esos filtros" texto="Prueba con otro módulo, otro usuario u otro periodo."
                           accion={<Boton variante="contorno" tamano="pequeno" onClick={limpiar}>Limpiar filtros</Boton>} />
            }
          />
          <p className="pie-tabla" aria-live="polite">{resultados(datos.total)} · Del más reciente al más antiguo</p>
          <Paginacion pagina={datos.pagina} totalPaginas={datos.totalPaginas} alCambiar={setPagina} etiqueta="Páginas de la auditoría" />
        </>
      )}
    </>
  );
}
