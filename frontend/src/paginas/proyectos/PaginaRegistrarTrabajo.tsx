import { Link, useParams } from 'react-router-dom';

import { Alerta } from '../../componentes/Alerta';
import { EnlaceBoton } from '../../componentes/Boton';
import { CambiosDelPlanEquipo } from '../../componentes/CambiosDelPlanEquipo';
import { Cargando } from '../../componentes/Cargando';
import { ConversacionProyecto } from '../../componentes/ConversacionProyecto';
import { Icono } from '../../componentes/Icono';
import { LineaTiempoFases } from '../../componentes/LineaTiempoFases';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { fechaLegible, hoyColombia } from '../../proyectos/admin';
import { fechaConDia, nombrePila } from '../../proyectos/plan';
import { faseActual, garantiaCumplida, pasosPorHacer, type DetalleProyecto } from '../../seguimiento/api';
import { useDetalleProyecto } from '../../seguimiento/useDetalleProyecto';
import { ProyectoNoEncontrado } from './ProyectoNoEncontrado';
import { FormularioRegistro } from './registro/FormularioRegistro';
import { CorreccionesAbiertas, EntregaFase } from './registro/PanelesDeFase';

const iniciales = (nombre: string) => nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');

/**
 * Registrar trabajo del desarrollador (Product Owner) (HU-03, HU-12, HU-13; prototipo:
 * desarrollador/registrar-avance-tienda-en-linea.html): marca pasos de la fase actual como hechos, agrega y termina correcciones
 * y entrega o cierra la fase. Lo publicado no se edita ni se borra. Con el proyecto en garantía solo se registran correcciones.
 */
export function PaginaRegistrarTrabajo() {
  const { codigo = '' } = useParams();
  const { detalle, error, recargar } = useDetalleProyecto(codigo);
  useTituloDocumento(detalle ? `Registrar trabajo · ${detalle.nombre}` : 'Registrar trabajo');

  if (error?.estado === 404 || error?.estado === 403) return <ProyectoNoEncontrado vista="equipo" />;
  if (error) {
    return (
      <>
        <Alerta tipo="error">{error.mensaje}</Alerta>
        <EnlaceBoton a="/equipo/proyectos" variante="contorno" icono="carpeta">Ir a mis proyectos</EnlaceBoton>
      </>
    );
  }
  if (!detalle) return <Cargando texto="Cargando el proyecto…" />;
  return <Registro d={detalle} recargar={recargar} />;
}

function Registro({ d, recargar }: { d: DetalleProyecto; recargar: () => Promise<void> }) {
  const pila = nombrePila(d.cliente);
  const hoy = hoyColombia();
  const fase = faseActual(d);
  const enGarantia = d.estado === 'EN_GARANTIA';
  const vencida = garantiaCumplida(d, hoy);
  const enRevision = fase?.estado === 'EN_REVISION';
  const habilitado = d.puedeRegistrar && d.estadoPlan === 'APROBADO' && !enRevision;

  // Lo que se puede registrar ahora: pasos de la fase en curso y correcciones en fases en curso o aprobadas (HU-12)
  const pendientes = habilitado && fase?.estado === 'EN_CURSO' && d.estado === 'ACTIVO' ? pasosPorHacer(fase) : [];
  const corregibles = d.fases.filter((f) => ['EN_CURSO', 'APROBADA', 'EN_CORRECCION'].includes(f.estado));
  const puedePaso = pendientes.length > 0;
  const puedeCorreccion = habilitado && corregibles.length > 0 && !vencida;
  const hayFormulario = puedePaso || puedeCorreccion;

  return (
    <>
      <Link className="volver" to="/equipo/proyectos"><Icono nombre="chevronIzquierda" />Volver a mis proyectos</Link>
      <div className="proyecto-cabecera aparecer">
        <div>
          <h1 className="proyecto-cabecera__titulo">Registrar trabajo</h1>
          <p className="proyecto-cabecera__subtitulo"><strong>{d.nombre}</strong> · {d.codigo} · {d.servicio}</p>
        </div>
      </div>

      <div className="registro-avance">
        <div className="registro-avance__principal">
          <section className="tarjeta formulario-avance aparecer" aria-labelledby="titulo-nuevo">
            <div className="formulario-avance__cabecera">
              <h2 className="tarjeta__titulo" id="titulo-nuevo">Registrar trabajo</h2>
              {fase && (
                <span className="fase-bloqueada" title="Las fases avanzan en orden (RN-08)">
                  <Icono nombre="candado" />
                  <span>Fase {fase.orden} de {d.fases.length} · <strong>{fase.nombre}</strong></span>
                </span>
              )}
            </div>

            <AvisoDeEstado d={d} vencida={vencida} enRevision={enRevision} habilitado={habilitado} hayFormulario={hayFormulario} />
            {habilitado && <CorreccionesAbiertas detalle={d} alTerminar={recargar} />}
            {hayFormulario && (
              <FormularioRegistro detalle={d} fase={fase?.estado === 'EN_CURSO' ? fase : undefined} pendientes={pendientes} corregibles={corregibles}
                                  puedePaso={puedePaso} puedeCorreccion={puedeCorreccion} alPublicar={recargar} />
            )}
            {habilitado && d.estado === 'ACTIVO' && fase?.estado === 'EN_CURSO' && <EntregaFase detalle={d} fase={fase} alEntregar={recargar} />}
          </section>

          <section aria-labelledby="titulo-linea">
            <h2 className="proyecto-detalle__titulo" id="titulo-linea">Fases del proyecto</h2>
            {d.fases.length === 0
              ? <p className="proyecto-detalle__nota">Este proyecto todavía no tiene un plan de trabajo.</p>
              : <LineaTiempoFases fases={d.fases} mirada="equipo" cliente={d.cliente} productOwner={d.productOwner} />}
          </section>
        </div>

        <aside className="registro-avance__lateral">
          <section className="tarjeta aparecer" aria-labelledby="titulo-cliente">
            <h2 className="tarjeta__titulo" id="titulo-cliente">Cliente</h2>
            <div className="persona-contacto">
              <span className="avatar avatar--acento" aria-hidden="true">{iniciales(d.cliente)}</span>
              <span><span className="persona-contacto__nombre">{d.cliente}</span><span className="persona-contacto__rol">Recibe un aviso con cada avance</span></span>
            </div>
            {d.clienteCorreo && (
              <a className="boton boton--contorno boton--bloque" href={`mailto:${d.clienteCorreo}`}><Icono nombre="correo" />Escribir a {pila}</a>
            )}
          </section>

          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '30ms' }} aria-labelledby="titulo-equipo">
            <h2 className="tarjeta__titulo" id="titulo-equipo">Equipo</h2>
            <div className="persona-contacto">
              <span className="avatar" aria-hidden="true">{iniciales(d.productOwner)}</span>
              <span><span className="persona-contacto__nombre">Tú · {d.productOwner}</span><span className="persona-contacto__rol">Product Owner</span></span>
            </div>
            <p className="equipo__nota">Registras el avance de todo el equipo y eres quien habla con {pila}.</p>
            {d.equipo.length > 0 && (
              <>
                <p className="equipo__titulo">Tu equipo en este proyecto</p>
                <ul className="equipo">
                  {d.equipo.map((p) => (
                    <li className="equipo__miembro" key={p.nombre}>
                      <span className="avatar avatar--pequeno" aria-hidden="true">{iniciales(p.nombre)}</span>
                      <span><span className="equipo__nombre">{p.nombre}</span><span className="equipo__rol">{p.rol}</span></span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>

          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '45ms' }} aria-labelledby="titulo-mensajes">
            <ConversacionProyecto codigo={d.codigo} interlocutor={nombrePila(d.cliente)} />
          </section>

          <CambiosDelPlanEquipo detalle={d} />

          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '60ms' }} aria-labelledby="titulo-datos">
            <h2 className="tarjeta__titulo" id="titulo-datos">Estado del proyecto</h2>
            <dl className="lista-datos">
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Avance del proyecto</dt><dd className="lista-datos__valor">{Math.round(d.avancePorcentaje * 10) / 10} %</dd></div>
              <div className="lista-datos__fila">
                <dt className="lista-datos__dato">Fase actual</dt>
                <dd className="lista-datos__valor">{fase ? `${fase.nombre} · ${fase.pasosHechos} de ${fase.pasosTotal} pasos` : enGarantia ? 'Todas aprobadas' : '—'}</dd>
              </div>
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Entrega estimada</dt><dd className="lista-datos__valor">{fechaLegible(d.fechaEstimadaEntrega)}</dd></div>
              {d.garantiaHasta && <div className="lista-datos__fila"><dt className="lista-datos__dato">Garantía hasta</dt><dd className="lista-datos__valor">{fechaLegible(d.garantiaHasta)}</dd></div>}
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Pasos hechos</dt><dd className="lista-datos__valor">{d.pasosHechos}</dd></div>
            </dl>
          </section>

          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '90ms' }} aria-labelledby="titulo-plan">
            <h2 className="tarjeta__titulo" id="titulo-plan">Plan de trabajo</h2>
            <p className="equipo__nota">El plan está aprobado por {pila}: las fases avanzan en orden y cada una vale lo mismo en el avance.</p>
            <EnlaceBoton a={`/equipo/proyectos/${d.codigo}/plan`} variante="contorno" bloque icono="lista">Ver el plan de trabajo</EnlaceBoton>
          </section>

          <section className="tarjeta reglas-avance aparecer" style={{ ['--retraso' as string]: '120ms' }} aria-labelledby="titulo-reglas">
            <h2 className="tarjeta__titulo" id="titulo-reglas">Antes de publicar</h2>
            <ul className="reglas-avance__lista">
              <li>Marca cada paso cuando esté hecho y deja una nota sencilla: {pila} no es programador(a).</li>
              <li>Puedes agregar hasta 5 bocetos o documentos (PNG, JPG, SVG o PDF) para que {pila} los vea.</li>
              <li>Entregas o cierras la fase cuando todos sus pasos están hechos y no tiene correcciones abiertas. Las fases que aprueba {pila} tienen 3 días hábiles para revisarse.</li>
              <li>Si hay que arreglar algo, agrega una corrección. Si la fase ya estaba aprobada, deja de sumar su porcentaje hasta que la termines; tú sigues avanzando.</li>
              <li>Cada fase que aprueba {pila} incluye 2 rondas de ajustes. Lo que pida de más, o algo nuevo, se cotiza antes de hacerlo.</li>
              <li>Con el proyecto en garantía (30 días) solo se registran correcciones.</li>
              <li>Lo publicado no se edita ni se borra: queda en la auditoría.</li>
            </ul>
          </section>
        </aside>
      </div>
    </>
  );
}

/** Por qué el formulario no está, o qué hay que saber antes de usarlo: espera al cliente, garantía, proyecto cerrado o sin plan. */
function AvisoDeEstado({ d, vencida, enRevision, habilitado, hayFormulario }:
  { d: DetalleProyecto; vencida: boolean; enRevision: boolean; habilitado: boolean; hayFormulario: boolean }) {
  const pila = nombrePila(d.cliente);
  const fase = faseActual(d);
  if (!d.puedeRegistrar || (d.estado !== 'ACTIVO' && d.estado !== 'EN_GARANTIA')) {
    return <Alerta tipo="info">El proyecto no admite registros en su estado actual.</Alerta>;
  }
  if (d.estadoPlan !== 'APROBADO') {
    return <Alerta tipo="info">El trabajo empieza cuando {pila} apruebe el plan y la cotización.</Alerta>;
  }
  if (enRevision && fase) {
    return (
      <Alerta tipo="advertencia" titulo={`Fase terminada · esperando la aprobación de ${pila}`}>
        Tiene {fase.plazoAprobacion ? <>hasta el {fechaConDia(fase.plazoAprobacion)}</> : '3 días hábiles'} para responder; si no lo hace, se le recuerda y la fase se da por aprobada.
        Si pide ajustes, aparecerán como un paso de ajuste en esta fase.
      </Alerta>
    );
  }
  if (d.estado === 'EN_GARANTIA' && d.garantiaHasta) {
    return vencida
      ? <Alerta tipo="advertencia" titulo="La garantía ya terminó">Solo se terminan las correcciones abiertas; no se registran correcciones nuevas. Cuando termines la última, el proyecto queda finalizado.</Alerta>
      : <Alerta tipo="exito" titulo="Proyecto en garantía">Todas las fases están aprobadas. Hasta el {fechaConDia(d.garantiaHasta)} solo se registran correcciones, sin costo para {pila}.</Alerta>;
  }
  if (habilitado && !hayFormulario && !fase) return <Alerta tipo="exito">Todas las fases del proyecto están aprobadas.</Alerta>;
  return null;
}
