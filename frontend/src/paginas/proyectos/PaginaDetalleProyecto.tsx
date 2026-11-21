import { Link, useParams } from 'react-router-dom';

import { Alerta } from '../../componentes/Alerta';
import { EnlaceBoton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { Icono } from '../../componentes/Icono';
import { Insignia, type VarianteInsignia } from '../../componentes/Insignia';
import { diaCorto, LineaTiempoFases } from '../../componentes/LineaTiempoFases';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { diasEntre, fechaLegible, hoyColombia } from '../../proyectos/admin';
import { nombrePila } from '../../proyectos/plan';
import type { DetalleProyecto } from '../../seguimiento/api';
import { useDetalleProyecto } from '../../seguimiento/useDetalleProyecto';
import { ProyectoNoEncontrado } from './ProyectoNoEncontrado';

const iniciales = (nombre: string) => nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');
const dias = (n: number) => `${n} ${n === 1 ? 'día' : 'días'}`;

/** Cómo se llama el proyecto en pantalla: el estado real, o el del plan mientras el trabajo no empieza. */
function insigniaDe(d: DetalleProyecto): { variante: VarianteInsignia; texto: string } {
  if (d.estado === 'ACTIVO') {
    if (d.estadoPlan === 'ENVIADO') return { variante: 'plan', texto: 'Plan por aprobar' };
    if (d.estadoPlan !== 'APROBADO') return { variante: 'borrador', texto: 'Plan en preparación' };
    return { variante: 'activo', texto: 'En desarrollo' };
  }
  const otros: Record<string, { variante: VarianteInsignia; texto: string }> = {
    PAUSADO: { variante: 'pausado', texto: 'Pausado' },
    EN_GARANTIA: { variante: 'garantia', texto: 'En garantía' },
    FINALIZADO: { variante: 'finalizado', texto: 'Finalizado' },
    CANCELADO: { variante: 'cancelada', texto: 'Cancelado' },
  };
  return otros[d.estado] ?? { variante: 'neutra', texto: d.estado };
}

/**
 * Detalle del proyecto del cliente (HU-04; prototipo/cliente/proyecto-tienda-en-linea.html): el avance general, las fases con
 * lo que se hizo en cada una y quién trabaja en su proyecto. Un proyecto que no es suyo se ve como «Proyecto no encontrado».
 * Los mensajes, las reuniones y los cambios del plan llegan en sus propios pasos.
 */
export function PaginaDetalleProyecto() {
  const { codigo = '' } = useParams();
  const { detalle, error } = useDetalleProyecto(codigo);
  useTituloDocumento(detalle ? detalle.nombre : 'Proyecto');

  if (error?.estado === 404) return <ProyectoNoEncontrado vista="cliente" />;
  if (error) {
    return (
      <>
        <Alerta tipo="error">{error.mensaje}</Alerta>
        <EnlaceBoton a="/cliente/proyectos" variante="contorno" icono="carpeta">Ir a mis proyectos</EnlaceBoton>
      </>
    );
  }
  if (!detalle) return <Cargando texto="Cargando tu proyecto…" />;
  return <Detalle d={detalle} />;
}

function Detalle({ d }: { d: DetalleProyecto }) {
  const hoy = hoyColombia();
  const insignia = insigniaDe(d);
  const pila = nombrePila(d.productOwner);
  const sinFases = d.fases.length === 0;
  const faltan = diasEntre(hoy, d.fechaEstimadaEntrega);
  const hace = diasEntre(d.fechaInicio, hoy);
  const actual = d.fases.find((f) => f.estado !== 'APROBADA');
  const comenzo = d.estadoPlan === 'APROBADO';
  const avance = Math.round(d.avancePorcentaje * 10) / 10;

  return (
    <>
      <Link className="volver" to="/cliente/proyectos"><Icono nombre="chevronIzquierda" />Volver a mis proyectos</Link>

      <div className="proyecto-cabecera aparecer">
        <div>
          <h1 className="proyecto-cabecera__titulo">{d.nombre} <Insignia variante={insignia.variante}>{insignia.texto}</Insignia></h1>
          <p className="proyecto-cabecera__subtitulo">{d.codigo} · {d.servicio}</p>
        </div>
        <div className="proyecto-cabecera__acciones">
          <EnlaceBoton a="/cliente/citas" variante="contorno" icono="calendario">Mis citas</EnlaceBoton>
        </div>
      </div>

      {d.estadoPlan === 'ENVIADO' && d.estado === 'ACTIVO' && (
        <Alerta tipo="info" titulo="Tu propuesta espera tu respuesta">
          Revisa el plan de trabajo y la cotización que te envió {pila}. <Link className="enlace" to={`/cliente/proyectos/${d.codigo}/propuesta`}>Ver la propuesta</Link>.
        </Alerta>
      )}

      <ul className="datos-proyecto" aria-label="Datos del proyecto">
        <li className="dato-proyecto dato-proyecto--general aparecer">
          <span className="anillo" style={{ ['--valor' as string]: avance }} role="img" aria-label={`Avance general ${avance} %`}>
            <span className="anillo__valor">{avance} %</span>
          </span>
          <span>
            <span className="dato-proyecto__etiqueta">Avance general</span>
            <span className="dato-proyecto__valor">
              {comenzo && actual ? `Fase ${actual.orden} de ${d.fases.length}` : comenzo ? 'Todas las fases aprobadas' : 'Sin empezar'}
            </span>
            <span className="dato-proyecto__detalle">
              {comenzo && actual ? `${actual.nombre} · ${actual.pasosHechos} de ${actual.pasosTotal} pasos hechos` : comenzo ? 'Solo falta el cierre' : 'Empieza cuando apruebes el plan'}
            </span>
          </span>
        </li>
        <li className="dato-proyecto aparecer" style={{ ['--retraso' as string]: '60ms' }}>
          <span className="dato-proyecto__etiqueta">Entrega estimada</span>
          <span className="dato-proyecto__valor">{fechaLegible(d.fechaEstimadaEntrega)}</span>
          <span className="dato-proyecto__detalle">{faltan > 0 ? `Faltan ${dias(faltan)}` : faltan === 0 ? 'Es hoy' : `Pasó hace ${dias(-faltan)}`}</span>
        </li>
        <li className="dato-proyecto aparecer" style={{ ['--retraso' as string]: '120ms' }}>
          <span className="dato-proyecto__etiqueta">Inicio del proyecto</span>
          <span className="dato-proyecto__valor">{fechaLegible(d.fechaInicio)}</span>
          <span className="dato-proyecto__detalle">{hace > 0 ? `Hace ${dias(hace)}` : 'Empieza pronto'}</span>
        </li>
        <li className="dato-proyecto aparecer" style={{ ['--retraso' as string]: '180ms' }}>
          <span className="dato-proyecto__etiqueta">Último paso hecho</span>
          <span className="dato-proyecto__valor">{d.ultimoPaso ? `${diaCorto(d.ultimoPaso.fecha)}` : 'Todavía ninguno'}</span>
          <span className="dato-proyecto__detalle">{d.ultimoPaso ? d.ultimoPaso.titulo : 'Verás aquí cada avance'}</span>
        </li>
      </ul>

      <div className="proyecto-detalle">
        <div className="proyecto-detalle__cabecera">
          <h2 className="proyecto-detalle__titulo" id="titulo-linea">Fases del proyecto</h2>
          {!sinFases && (
            <p className="proyecto-detalle__nota">
              Tu proyecto tiene {d.fases.length} fases y cada una vale {Math.round((d.fases[0]?.porcentaje ?? 0) * 10) / 10} %.
              Abre cada fase para ver qué se ha hecho.
            </p>
          )}
        </div>
        <section className="proyecto-detalle__principal" aria-labelledby="titulo-linea">
          {sinFases
            ? <p className="proyecto-detalle__nota">{pila} está preparando el plan de trabajo. Cuando te lo envíe, verás aquí sus fases.</p>
            : <LineaTiempoFases fases={d.fases} mirada="cliente" cliente={d.cliente} productOwner={d.productOwner} />}
        </section>

        <aside className="proyecto-detalle__lateral">
          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '120ms' }} aria-labelledby="titulo-dev">
            <h2 className="tarjeta__titulo" id="titulo-dev">Tu equipo</h2>
            <div className="persona-contacto">
              <span className="avatar" aria-hidden="true">{iniciales(d.productOwner)}</span>
              <span>
                <span className="persona-contacto__nombre">{d.productOwner}</span>
                <span className="persona-contacto__rol">Product Owner · tu contacto</span>
              </span>
            </div>
            <p className="equipo__nota">{pila} publica el avance de tu proyecto y responde tus mensajes en nombre del equipo.</p>
            {d.equipo.length > 0 && (
              <>
                <p className="equipo__titulo">Equipo que trabaja en tu proyecto</p>
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
            {d.productOwnerCorreo && (
              <p className="conversacion__correo">Puedes escribir a <a className="enlace" href={`mailto:${d.productOwnerCorreo}`}>{d.productOwnerCorreo}</a></p>
            )}
          </section>
          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '180ms' }} aria-labelledby="titulo-datos">
            <h2 className="tarjeta__titulo" id="titulo-datos">Datos del proyecto</h2>
            <dl className="lista-datos">
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Código</dt><dd className="lista-datos__valor">{d.codigo}</dd></div>
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Servicio</dt><dd className="lista-datos__valor">{d.servicio}</dd></div>
              {d.citaCodigo && <div className="lista-datos__fila"><dt className="lista-datos__dato">Cita de origen</dt><dd className="lista-datos__valor">{d.citaCodigo}</dd></div>}
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Pasos hechos</dt><dd className="lista-datos__valor">{d.pasosHechos}</dd></div>
            </dl>
          </section>
        </aside>
      </div>
    </>
  );
}
