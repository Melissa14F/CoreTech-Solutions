import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { iniciales } from '../../administracion/formato';
import { Alerta as AvisoError } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { Icono, type NombreIcono } from '../../componentes/Icono';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { PERIODOS, dashboardApi, type Alerta, type Periodo, type Tablero } from '../../dashboard/api';
import { useUsuario } from '../../sesion/ContextoSesion';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-dashboard.css';

const ZONA = { timeZone: 'America/Bogota' } as const;
const NIVELES: Record<Alerta['nivel'], { clase: string; lector: string; icono: NombreIcono }> = {
  CRITICA: { clase: 'critica', lector: 'Crítica: ', icono: 'alerta' },
  ADVERTENCIA: { clase: 'advertencia', lector: 'Advertencia: ', icono: 'alerta' },
  INFO: { clase: 'info', lector: 'Pendiente: ', icono: 'mensaje' },
};
const TIPO_DE_ACTIVIDAD: Record<string, string> = {
  citas: 'Cita', proyectos: 'Proyecto', avances: 'Avance', solicitudes: 'Solicitud', usuarios: 'Usuario', permisos: 'Permisos',
  sitio: 'Sitio', sesiones: 'Sesión', seguridad: 'Seguridad',
};

/** Solo los códigos que ve la gente (C-0059, P-0031, S-0008); el identificador interno de un usuario no se muestra. */
const CODIGO_VISIBLE = /^[A-Z]-\d{4,}$/;
const nombrePila = (nombre: string) => nombre.trim().split(/\s+/)[0] || 'administrador';
const numero = (n: number) => n.toLocaleString('es-CO');
const decimal = (n: number) => String(n).replace('.', ',');

/** «miércoles 11 de noviembre de 2026» a partir de AAAA-MM-DD. */
function fechaLarga(dia: string): string {
  return new Date(`${dia}T12:00:00-05:00`).toLocaleDateString('es-CO', { ...ZONA, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).replace(/^(\p{L}+),\s/u, '$1 ');
}

/** «Hoy, 11:20 a. m.» o «12 nov, 3:05 p. m.», en hora de Colombia. */
function cuando(instante: string, hoy: string): string {
  const fecha = new Date(instante);
  const hora = fecha.toLocaleTimeString('es-CO', { ...ZONA, hour: 'numeric', minute: '2-digit', hour12: true });
  const dia = fecha.toLocaleDateString('en-CA', ZONA);
  if (dia === hoy) return `Hoy, ${hora}`;
  return `${fecha.toLocaleDateString('es-CO', { ...ZONA, day: 'numeric', month: 'short' }).replace('.', '')}, ${hora}`;
}

/**
 * Dashboard del administrador (HU-23; prototipo: admin/dashboard.html): indicadores clave, «Requiere atención» del más grave al
 * menos grave (cada alerta lleva al detalle), citas por mes, carga de los equipos, proyectos por fase, servicios más pedidos,
 * citas por estado y la actividad reciente. El periodo (semana, mes o trimestre) mueve el indicador de citas y las citas por estado.
 */
export function PaginaDashboard() {
  useTituloDocumento('Dashboard');
  const usuario = useUsuario();
  const [periodo, setPeriodo] = useState<Periodo>('mes');
  const [tablero, setTablero] = useState<Tablero | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [intento, setIntento] = useState(0);

  const cargar = useCallback(async (p: Periodo) => {
    try {
      setTablero(await dashboardApi.tablero(p));
      setError(null);
    } catch (e) {
      setError(separarErrores(e).general ?? 'No pudimos cargar el dashboard.');
    }
  }, []);

  useEffect(() => {
    void cargar(periodo);
  }, [cargar, periodo, intento]);

  const saludo = (
    <div className="encabezado-vista">
      <div>
        <h1 className="encabezado-vista__titulo">Hola, {usuario ? nombrePila(usuario.nombre) : 'administrador'}</h1>
        <p className="encabezado-vista__texto">{tablero ? `Así va CoreTech al corte del ${fechaLarga(tablero.corte)}.` : 'Así va CoreTech.'}</p>
      </div>
      <div className="encabezado-vista__acciones">
        <Link className="boton boton--contorno" to="/admin/auditoria">Ver auditoría</Link>
        <Link className="boton boton--acento" to="/admin/proyectos/nuevo">Crear proyecto</Link>
      </div>
    </div>
  );

  if (error && !tablero) {
    return (
      <>
        {saludo}
        <AvisoError tipo="error" titulo="No pudimos cargar el dashboard">{error}</AvisoError>
        <p><Boton variante="contorno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton></p>
      </>
    );
  }
  if (!tablero) return <>{saludo}<Cargando texto="Cargando el dashboard…" /></>;

  const { indicadores: i, conteos: c } = tablero;
  const maxMes = Math.max(1, ...tablero.citasPorMes.map((m) => m.citas));
  const maxServicio = Math.max(1, ...tablero.servicios.map((s) => s.citas));
  const e = tablero.citasPorEstado;

  return (
    <>
      {saludo}

      <div className="filtros" role="group" aria-label="Periodo del indicador de citas">
        {PERIODOS.map((p) => (
          <button key={p.valor} type="button" className="filtro" aria-pressed={periodo === p.valor} onClick={() => setPeriodo(p.valor)}>{p.texto}</button>
        ))}
      </div>

      {/* ===================== Indicadores clave ===================== */}
      <ul className="kpis" aria-label="Indicadores clave">
        <li>
          <Link className="kpi aparecer" to="/admin/citas">
            <span className="kpi__cabecera"><span className="indicador__icono"><Icono nombre="calendario" /></span><span className="kpi__texto">{i.citas.etiqueta}</span></span>
            <span className="kpi__valor">{numero(i.citas.total)}</span>
            <span className="kpi__detalle">
              {i.citas.variacion === null
                ? (tablero.sinDatos ? 'Sin datos en este periodo' : `Sin citas en ${i.citas.etiquetaAnterior}`)
                : <><span className={`tendencia ${i.citas.variacion >= 0 ? 'tendencia--sube' : 'tendencia--baja'}`}>{Math.abs(i.citas.variacion)} %</span> frente a {i.citas.etiquetaAnterior}</>}
            </span>
          </Link>
        </li>
        <li>
          <div className="kpi aparecer">
            <span className="kpi__cabecera"><span className="indicador__icono"><Icono nombre="exito" /></span><span className="kpi__texto">Conversión a proyecto</span></span>
            <span className="kpi__valor">{i.conversion.porcentaje ?? 0} %</span>
            <span className="kpi__barra" role="img" aria-label={`${i.conversion.porcentaje ?? 0} %`}><span style={{ ['--valor' as string]: `${i.conversion.porcentaje ?? 0}%` }} /></span>
            <span className="kpi__detalle">{i.conversion.proyectos} de {i.conversion.citas} citas en 6 meses</span>
          </div>
        </li>
        <li>
          <Link className="kpi aparecer" to="/admin/proyectos">
            <span className="kpi__cabecera"><span className="indicador__icono"><Icono nombre="carpeta" /></span><span className="kpi__texto">Proyectos activos</span></span>
            <span className="kpi__valor">{i.proyectos.activos} <small>de {i.proyectos.capacidad}</small></span>
            <span className="kpi__barra" role="img" aria-label={`${i.proyectos.porcentaje} %`}><span style={{ ['--valor' as string]: `${Math.min(100, i.proyectos.porcentaje)}%` }} /></span>
            <span className="kpi__detalle">{i.proyectos.porcentaje} % de la capacidad · {i.proyectos.planesPorAprobar === 0 ? 'ningún plan por aprobar' : `${i.proyectos.planesPorAprobar} con plan por aprobar`}</span>
          </Link>
        </li>
        <li>
          <div className="kpi aparecer">
            <span className="kpi__cabecera"><span className="indicador__icono indicador__icono--exito"><Icono nombre="reloj" /></span><span className="kpi__texto">Entregas a tiempo</span></span>
            <span className="kpi__valor">{i.entregas.porcentaje === null ? '—' : `${i.entregas.porcentaje} %`}</span>
            <span className="kpi__barra" role="img" aria-label={`${i.entregas.porcentaje ?? 0} %`}><span style={{ ['--valor' as string]: `${i.entregas.porcentaje ?? 0}%` }} /></span>
            <span className="kpi__detalle">{i.entregas.total === 0 ? `Aún no hay proyectos entregados en ${i.entregas.anio}` : `${i.entregas.aTiempo} de ${i.entregas.total} proyectos en ${i.entregas.anio}`}</span>
          </div>
        </li>
      </ul>

      <div className="tablero">
        {/* ===================== Requiere atención ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--completo aparecer" id="alertas" aria-labelledby="titulo-alertas">
          <div className="bloque__cabecera">
            <div>
              <h2 className="tarjeta__titulo" id="titulo-alertas">Requiere atención</h2>
              <p className="bloque__subtitulo">
                {tablero.alertas.length === 0 ? 'Nada pendiente por ahora' : `${tablero.alertas.length} ${tablero.alertas.length === 1 ? 'asunto abierto' : 'asuntos abiertos'}, del más grave al menos grave`}
              </p>
            </div>
          </div>
          {tablero.alertas.length === 0 ? (
            <EstadoVacio icono="exito" titulo="Todo en orden" texto="No hay citas sin confirmar, proyectos atrasados ni cambios por autorizar." />
          ) : (
            <ul className="alertas-tablero">
              {tablero.alertas.map((a) => {
                const n = NIVELES[a.nivel];
                return (
                  <li key={a.id} className={`alerta-tablero alerta-tablero--${n.clase}`}>
                    <span className="alerta-tablero__icono"><Icono nombre={n.icono} /></span>
                    <strong className="alerta-tablero__titulo"><span className="solo-lector">{n.lector}</span>{a.titulo}</strong>
                    <Link className="boton boton--contorno boton--pequeno" to={a.enlace}>{a.accion}</Link>
                    <p className="alerta-tablero__texto">{a.texto}</p>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {/* ===================== De un vistazo (HU-23) ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--completo aparecer" aria-labelledby="titulo-vistazo">
          <div className="bloque__cabecera"><div><h2 className="tarjeta__titulo" id="titulo-vistazo">De un vistazo</h2><p className="bloque__subtitulo">Lo que espera una respuesta, con su enlace</p></div></div>
          <ul className="de-un-vistazo">
            <Cifra valor={c.citasSemana} texto="Citas de esta semana" a="/admin/citas" />
            <Cifra valor={c.citasSinAsignar} texto="Citas sin equipo" a="/admin/citas" />
            <Cifra valor={c.planesPorAprobar} texto="Planes por aprobar" a="/admin/proyectos" />
            <Cifra valor={c.cambiosPorAutorizar} texto="Cambios de plan por autorizar" a="/admin/proyectos" />
            <Cifra valor={c.fasesPorAprobar} texto={`Fases por aprobar (${c.fasesVencidas} vencidas)`} a="/admin/proyectos" />
            <Cifra valor={c.proyectosEnGarantia} texto={`En garantía (${c.garantiasCumplidas} ya cumplidas)`} a="/admin/proyectos" />
            <Cifra valor={c.solicitudesPendientes} texto="Solicitudes de reunión" a="/admin/solicitudes" />
          </ul>
        </section>

        {/* ===================== Citas por mes ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--ancho aparecer" aria-labelledby="titulo-meses">
          <div className="bloque__cabecera">
            <div><h2 className="tarjeta__titulo" id="titulo-meses">Citas agendadas por mes</h2><p className="bloque__subtitulo">Últimos 6 meses · {numero(tablero.citasPorMes.reduce((s, m) => s + m.citas, 0))} citas</p></div>
            <Link className="enlace-bloque" to="/admin/citas">Ver citas</Link>
          </div>
          <figure className="grafico">
            <div className="barras-mes">
              <div className="barras-mes__guias" aria-hidden="true"><span>{maxMes}</span><span>{Math.round(maxMes / 2)}</span><span>0</span></div>
              <ol className="barras-mes__lista" aria-label="Citas por mes">
                {tablero.citasPorMes.map((m) => (
                  <li key={m.mes} className="barras-mes__columna">
                    <span className={['barras-mes__barra', m.parcial && 'barras-mes__barra--parcial'].filter(Boolean).join(' ')} style={{ ['--alto' as string]: `${(m.citas / maxMes) * 100}%` }}
                          tabIndex={0} aria-label={`${m.etiqueta}: ${m.citas} ${m.citas === 1 ? 'cita' : 'citas'}${m.parcial ? ' (mes en curso)' : ''}`}>
                      <span className="globo" role="presentation">{m.citas} {m.citas === 1 ? 'cita' : 'citas'}</span>
                    </span>
                    <span className="barras-mes__mes" aria-hidden="true">{m.etiqueta}</span>
                  </li>
                ))}
              </ol>
            </div>
            <figcaption className="grafico__nota">La barra rayada es el mes en curso.</figcaption>
          </figure>
        </section>

        {/* ===================== Carga del equipo ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--angosto aparecer" aria-labelledby="titulo-carga">
          <div className="bloque__cabecera"><div><h2 className="tarjeta__titulo" id="titulo-carga">Carga del equipo</h2><p className="bloque__subtitulo">Proyectos activos frente al cupo de cada equipo</p></div></div>
          {tablero.carga.length === 0 ? <p className="bloque__subtitulo">Aún no hay equipos activos.</p> : (
            <ul className="carga-equipo">
              {tablero.carga.map((q) => (
                <li key={q.id} className="carga-equipo__fila">
                  <span className="avatar" aria-hidden="true">{iniciales(q.productOwner)}</span>
                  <span className="carga-equipo__nombre">{q.productOwner}
                    <span className="carga" role="img" aria-label={`${q.activos} de ${q.maximo} proyectos activos`}>
                      {Array.from({ length: q.maximo }, (_, k) => <span key={k} className={['carga__unidad', k < q.activos && 'carga__unidad--ocupada'].filter(Boolean).join(' ')} />)}
                    </span>
                  </span>
                  <span className="carga-equipo__cifra">{q.activos}/{q.maximo}</span>
                  {q.libres === 0 ? <span className="insignia insignia--pendiente">Sin cupo</span> : <span className="carga-equipo__libres">{q.libres} {q.libres === 1 ? 'cupo libre' : 'cupos libres'}</span>}
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* ===================== Proyectos activos por fase ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--ancho aparecer" aria-labelledby="titulo-fases">
          <div className="bloque__cabecera">
            <div><h2 className="tarjeta__titulo" id="titulo-fases">Proyectos activos por fase</h2><p className="bloque__subtitulo">{c.proyectosActivos} en curso · {c.proyectosEnGarantia} en garantía</p></div>
            <Link className="enlace-bloque" to="/admin/proyectos">Ver proyectos</Link>
          </div>
          {tablero.proyectosPorFase.length === 0 ? <p className="bloque__subtitulo">No hay proyectos activos.</p> : (
            <ol className="embudo" aria-label="Proyectos activos en cada fase">
              {tablero.proyectosPorFase.map((f) => (
                <li key={f.fase} className="embudo__fase"><span className="embudo__numero">{f.proyectos}</span><span className="embudo__nombre">{f.fase}</span></li>
              ))}
            </ol>
          )}
        </section>

        {/* ===================== Servicios más solicitados ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--angosto aparecer" aria-labelledby="titulo-servicios">
          <div className="bloque__cabecera">
            <div><h2 className="tarjeta__titulo" id="titulo-servicios">Servicios más solicitados</h2><p className="bloque__subtitulo">Citas por servicio · últimos 6 meses</p></div>
            <Link className="enlace-bloque" to="/admin/servicios">Catálogo</Link>
          </div>
          {tablero.servicios.length === 0 ? <p className="bloque__subtitulo">Sin datos en este periodo</p> : (
            <ul className="barras-servicio" aria-label="Citas por servicio en los últimos 6 meses">
              {tablero.servicios.map((s) => (
                <li key={s.servicio} className="barras-servicio__fila">
                  <span className="barras-servicio__nombre">{s.servicio}</span>
                  <span className="barras-servicio__pista"><span className="barras-servicio__barra" style={{ ['--ancho' as string]: `${(s.citas / maxServicio) * 100}%` }} /></span>
                  <span className="barras-servicio__valor">{s.citas}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* ===================== Citas por estado ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--angosto aparecer" aria-labelledby="titulo-estados">
          <div className="bloque__cabecera"><div><h2 className="tarjeta__titulo" id="titulo-estados">{i.citas.etiqueta.replace(/^Citas (en )?/, 'Citas de ')} por estado</h2><p className="bloque__subtitulo">Cómo va cada cita agendada en el periodo</p></div></div>
          {e.total === 0 ? <p className="bloque__subtitulo">Sin datos en este periodo</p> : (
            <>
              <p className="estados-total"><strong>{e.total}</strong> {e.total === 1 ? 'cita agendada' : 'citas agendadas'} en el periodo</p>
              <div className="barra-estados" role="img" aria-label={`Pendientes ${e.pendientes}, confirmadas ${e.confirmadas}, atendidas ${e.atendidas}, canceladas ${e.canceladas}`}>
                {(['pendiente', 'confirmada', 'atendida', 'cancelada'] as const).map((k) => {
                  const cantidad = { pendiente: e.pendientes, confirmada: e.confirmadas, atendida: e.atendidas, cancelada: e.canceladas }[k];
                  return cantidad > 0 ? <span key={k} className={`barra-estados__segmento barra-estados__segmento--${k}`} style={{ ['--parte' as string]: cantidad }} title={`${k}s: ${cantidad}`} /> : null;
                })}
              </div>
              <ul className="leyenda">
                {([['pendiente', 'Pendientes', e.pendientes], ['confirmada', 'Confirmadas', e.confirmadas], ['atendida', 'Atendidas', e.atendidas], ['cancelada', 'Canceladas', e.canceladas]] as const).map(([k, texto, n]) => (
                  <li key={k} className="leyenda__item"><span className={`leyenda__muestra leyenda__muestra--${k}`} aria-hidden="true" />{texto}<strong>{n}</strong></li>
                ))}
              </ul>
            </>
          )}
          {e.promedioDiasParaConfirmar !== null && (
            <div className="meta-confirmacion">
              <div className="meta-confirmacion__cabecera"><span>Tiempo promedio para confirmar</span><strong>{decimal(e.promedioDiasParaConfirmar)} {e.promedioDiasParaConfirmar === 1 ? 'día' : 'días'}</strong></div>
              <span className="meta-confirmacion__barra" role="img" aria-label={`${decimal(e.promedioDiasParaConfirmar)} de 1 día hábil de meta`}><span style={{ ['--valor' as string]: `${Math.min(100, e.promedioDiasParaConfirmar * 100)}%` }} /></span>
              <p className="meta-confirmacion__nota">Meta: menos de 1 día hábil. Si una cita se pasa, aparece en «Requiere atención».</p>
            </div>
          )}
        </section>

        {/* ===================== Actividad reciente ===================== */}
        <section className="tarjeta tablero__bloque tablero__bloque--ancho aparecer" aria-labelledby="titulo-actividad">
          <div className="bloque__cabecera">
            <div><h2 className="tarjeta__titulo" id="titulo-actividad">Actividad reciente</h2><p className="bloque__subtitulo">Quién hizo qué y cuándo. El registro completo no se puede editar ni borrar.</p></div>
            <Link className="enlace-bloque" to="/admin/auditoria">Ver auditoría completa</Link>
          </div>
          {tablero.actividad.length === 0 ? <p className="bloque__subtitulo">Todavía no hay actividad.</p> : (
            <ol className="actividad">
              {tablero.actividad.map((a, k) => (
                <li key={`${a.fecha}-${k}`} className="actividad__item">
                  <span className="avatar" aria-hidden="true">{iniciales(a.usuario)}</span>
                  <p className="actividad__texto"><strong>{a.usuario}</strong> {a.accion.charAt(0).toLowerCase() + a.accion.slice(1)}{a.entidadId && CODIGO_VISIBLE.test(a.entidadId) ? <> · <strong>{a.entidadId}</strong></> : null}</p>
                  <span className="actividad__tipo">{TIPO_DE_ACTIVIDAD[a.categoria] ?? a.categoria}</span>
                  <time className="actividad__hora" dateTime={a.fecha}>{cuando(a.fecha, tablero.corte)}</time>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </>
  );
}

function Cifra({ valor, texto, a }: { valor: number; texto: string; a: string }) {
  return (
    <li className={['de-un-vistazo__item', valor > 0 && 'de-un-vistazo__item--con-datos'].filter(Boolean).join(' ')}>
      <Link className="de-un-vistazo__enlace" to={a}><strong className="de-un-vistazo__valor">{valor}</strong><span>{texto}</span></Link>
    </li>
  );
}
