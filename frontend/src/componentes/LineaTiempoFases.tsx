import type { ReactNode } from 'react';

import { diaColombia } from '../administracion/formato';
import { fechaSinAnio } from '../proyectos/admin';
import { nombrePila } from '../proyectos/plan';
import { pasoEnCurso, type CambioPedido, type FaseDetalle, type PasoDetalle } from '../seguimiento/api';
import { Icono } from './Icono';
import { Insignia, type VarianteInsignia } from './Insignia';

export type Mirada = 'cliente' | 'equipo';

/** "2026-11-21T02:40:46Z" → "20 nov": el día de Colombia, no el de UTC. */
export const diaCorto = (instante: string): string => fechaSinAnio(diaColombia(new Date(instante)));

interface PropiedadesLinea {
  fases: FaseDetalle[];
  /** Quién mira: cambia a quién se le habla («Esta fase la apruebas tú» / «…la aprueba Laura»). */
  mirada: Mirada;
  /** Nombre del cliente, para hablarle al equipo de él. */
  cliente: string;
  /** Nombre del Product Owner, para hablarle al cliente de él. */
  productOwner: string;
  /** Lo que se muestra arriba de los pasos de una fase que espera la respuesta del cliente (aprobar o pedir cambios, HU-11). */
  revision?: (fase: FaseDetalle) => ReactNode;
}

const INSIGNIA: Record<FaseDetalle['estado'], { variante: VarianteInsignia; texto: string }> = {
  PENDIENTE: { variante: 'neutra', texto: 'Pendiente' },
  EN_CURSO: { variante: 'activo', texto: 'En curso' },
  EN_REVISION: { variante: 'pendiente', texto: 'Esperando aprobación' },
  APROBADA: { variante: 'finalizado', texto: 'Aprobada' },
  EN_CORRECCION: { variante: 'pendiente', texto: 'En corrección' },
};

const porcentaje = (valor: number) => `${String(Math.round(valor * 10) / 10).replace('.', ',')} %`;

/**
 * Línea de tiempo de las fases de un proyecto (prototipo: ol.linea-tiempo de cliente/proyecto-*.html y de
 * desarrollador/registrar-avance-*.html). La comparten el detalle del cliente (HU-04, HU-11) y «Registrar trabajo» (HU-03, HU-12,
 * HU-13): cada fase se despliega con sus pasos, su nota y sus bocetos. La fase en la que se trabaja viene abierta.
 */
export function LineaTiempoFases({ fases, mirada, cliente, productOwner, revision }: PropiedadesLinea) {
  const actual = fases.find((f) => f.estado === 'EN_CURSO' || f.estado === 'EN_REVISION');
  return (
    <ol className="linea-tiempo">
      {fases.map((fase, i) => (
        <Fase key={fase.id} fase={fase} mirada={mirada} cliente={cliente} productOwner={productOwner}
              esActual={fase.id === actual?.id} retraso={i * 40} revision={revision} />
      ))}
    </ol>
  );
}

function Fase({ fase, mirada, cliente, productOwner, esActual, retraso, revision }:
  Omit<PropiedadesLinea, 'fases'> & { fase: FaseDetalle; esActual: boolean; retraso: number }) {
  const aprobada = fase.estado === 'APROBADA';
  const enCorreccion = fase.estado === 'EN_CORRECCION';
  const enRevision = fase.estado === 'EN_REVISION';
  const clase = aprobada || enCorreccion ? 'fase--completa' : esActual ? 'fase--actual' : 'fase--pendiente';
  const insignia = insigniaDe(fase, mirada, cliente);
  const enCurso = pasoEnCurso(fase);
  const hechos = `${fase.pasosHechos} de ${fase.pasosTotal} pasos hechos`;
  const fechas = aprobada && fase.iniciadaAt && fase.aprobadaAt
    ? `${diaCorto(fase.iniciadaAt)} → ${diaCorto(fase.aprobadaAt)} · ${hechos}`
    : fase.iniciadaAt ? `Desde el ${diaCorto(fase.iniciadaAt)} · ${hechos}`
    : `${fase.pasosTotal} ${fase.pasosTotal === 1 ? 'paso' : 'pasos'}`;
  const quienCierra = mirada === 'cliente'
    ? (fase.apruebaCliente ? 'Esta fase la apruebas tú.' : 'Esta fase la cierra el equipo.')
    : (fase.apruebaCliente ? `Esta fase la aprueba ${nombrePila(cliente)}.` : `Esta fase la cierras tú; ${nombrePila(cliente)} ve lo hecho.`);
  // Algo nuevo que pidió el cliente y todavía no se cotiza: se muestra aparte, no es un paso
  const porCotizar = fase.cambios.filter((c) => c.tipo === 'ALCANCE' && c.estado === 'POR_COTIZAR');

  return (
    <li className={['fase', clase, enCorreccion && 'fase--con-correccion', enRevision && 'fase--revision', 'aparecer'].filter(Boolean).join(' ')}
        style={{ ['--retraso' as string]: `${retraso}ms` }} aria-current={esActual ? 'step' : undefined}
        data-fase-numero={fase.orden}>
      <span className="fase__marcador">
        {aprobada ? <Icono nombre="exito" /> : enCorreccion ? <Icono nombre="llave" /> : fase.orden}
        <span className="solo-lector">
          {aprobada ? `Fase ${fase.orden} aprobada` : enCorreccion ? `Fase ${fase.orden} en corrección` : esActual ? ', fase actual' : ', pendiente'}
        </span>
      </span>
      <details className="fase__tarjeta" open={esActual || enCorreccion || undefined}>
        <summary className="fase__cabecera">
          <div>
            <h3 className="fase__nombre">{fase.nombre}</h3>
            <span className="fase__fechas">{fechas}</span>
          </div>
          <span className="fase__resumen">
            <Insignia variante={insignia.variante}>{insignia.texto}</Insignia>
            <span className="fase__valor" title="Lo que vale esta fase en el proyecto">{porcentaje(fase.porcentaje)}</span>
            <Icono nombre="chevronAbajo" className="fase__flecha" />
          </span>
        </summary>
        <div className="fase__cuerpo">
          {fase.queEs && <p className="fase__que-es">{fase.queEs} <span className="fase__quien-cierra">{quienCierra}</span></p>}
          {enCorreccion && (
            <p className="fase__aviso-correccion">
              <Icono nombre="llave" />
              <span>
                {mirada === 'cliente'
                  ? `Estamos corrigiendo algo de esta fase. Mientras tanto no suma su ${porcentaje(fase.porcentaje)} al proyecto; vuelve a sumarlo al terminar la corrección.`
                  : `Esta fase dejó de sumar su ${porcentaje(fase.porcentaje)} hasta que termines la corrección.`}
              </span>
            </p>
          )}
          {enRevision && revision?.(fase)}
          <p className="pasos-fase__titulo">{fase.estado === 'PENDIENTE' ? 'Lo que haremos' : 'Pasos'}</p>
          <ol className="pasos-fase" aria-label="Pasos de la fase">
            {fase.pasos.map((paso) => (
              <Paso key={paso.id} paso={paso} enCurso={paso.id === enCurso} mirada={mirada} productOwner={productOwner} />
            ))}
            {porCotizar.map((c) => <AlcancePorCotizar key={c.creadoAt} cambio={c} mirada={mirada} productOwner={productOwner} />)}
          </ol>
          {aprobada && fase.aprobadaAt && <Aprobacion fase={fase} mirada={mirada} cliente={cliente} />}
        </div>
      </details>
    </li>
  );
}

function Aprobacion({ fase, mirada, cliente }: { fase: FaseDetalle; mirada: Mirada; cliente: string }) {
  const quien = fase.aprobacionAutomatica
    ? (mirada === 'cliente' ? 'Se aprobó sola, porque no respondiste a tiempo,' : 'Se aprobó sola, porque el cliente no respondió a tiempo,')
    : mirada === 'cliente' && fase.apruebaCliente ? 'Aprobaste esta fase'
    : fase.apruebaCliente ? `${nombrePila(cliente)} aprobó esta fase` : 'El equipo cerró esta fase';
  return (
    <p className="fase__aprobacion">
      <Icono nombre="exito" />
      {quien} el {diaCorto(fase.aprobadaAt!)}.
      {fase.apruebaCliente && fase.rondasAjusteUsadas > 0 && ` Usó ${fase.rondasAjusteUsadas} de 2 rondas de ajustes.`}
    </p>
  );
}

function insigniaDe(fase: FaseDetalle, mirada: Mirada, cliente: string): { variante: VarianteInsignia; texto: string } {
  if (fase.estado === 'APROBADA' && !fase.apruebaCliente) return { variante: 'finalizado', texto: 'Cerrada' };
  if (fase.estado === 'EN_REVISION') {
    return { variante: 'pendiente', texto: mirada === 'cliente' ? 'Por aprobar' : `Esperando a ${nombrePila(cliente)}` };
  }
  return INSIGNIA[fase.estado];
}

const ETIQUETA_TIPO: Record<PasoDetalle['tipo'], string> = {
  PASO: '',
  CORRECCION: 'Corrección',
  AJUSTE: 'Ajuste',
  ALCANCE: 'Cambio de alcance',
};

/** «Lo pidió el cliente» se lee distinto según quién mira: al cliente se le dice «Lo pediste tú». */
const origenVisible = (origen: string, mirada: Mirada) => (mirada === 'cliente' && origen === 'Lo pidió el cliente' ? 'Lo pediste tú' : origen);

function Paso({ paso, enCurso, mirada, productOwner }: { paso: PasoDetalle; enCurso: boolean; mirada: Mirada; productOwner: string }) {
  const hecho = paso.estado === 'HECHO';
  const estado = hecho ? 'hecho' : enCurso ? 'en curso' : 'pendiente';
  const etiqueta = ETIQUETA_TIPO[paso.tipo];
  return (
    <li className={['paso-fase', `paso-fase--${hecho ? 'hecho' : enCurso ? 'en-curso' : 'pendiente'}`, paso.tipo !== 'PASO' && 'paso-fase--correccion']
      .filter(Boolean).join(' ')} data-paso={paso.id}>
      <span className="paso-fase__marca" aria-hidden="true">{hecho && <Icono nombre="exito" />}</span>
      <div className="paso-fase__cuerpo">
        {etiqueta && (
          <span className="paso-fase__etiqueta">
            <Icono nombre="llave" />{etiqueta}{paso.origen ? ` · ${origenVisible(paso.origen, mirada)}` : ''}
          </span>
        )}
        <p className="paso-fase__titulo">{paso.titulo}<span className="solo-lector">, {estado}</span></p>
        {paso.explicacion && <p className="paso-fase__explicacion">{paso.explicacion}</p>}
        {hecho && paso.nota && (
          <p className="paso-fase__nota"><strong>{mirada === 'equipo' ? 'Tu nota' : nombrePila(productOwner)}:</strong> {paso.nota}</p>
        )}
        {paso.adjuntos.length > 0 && <Bocetos adjuntos={paso.adjuntos} />}
        {hecho && paso.hechoAt ? <span className="paso-fase__estado">Hecho el {diaCorto(paso.hechoAt)}</span>
          : enCurso ? <span className="paso-fase__estado">En curso</span> : null}
      </div>
    </li>
  );
}

/** Algo nuevo que pidió el cliente: todavía no se hace, primero se cotiza (RN-17, D-32). */
function AlcancePorCotizar({ cambio, mirada, productOwner }: { cambio: CambioPedido; mirada: Mirada; productOwner: string }) {
  return (
    <li className="paso-fase paso-fase--pendiente paso-fase--correccion paso-fase--alcance">
      <span className="paso-fase__marca" aria-hidden="true" />
      <div className="paso-fase__cuerpo">
        <span className="paso-fase__etiqueta"><Icono nombre="llave" />Cambio de alcance · por cotizar</span>
        <p className="paso-fase__titulo">{mirada === 'cliente' ? 'Función nueva que pediste' : 'Algo nuevo que pidió el cliente'}<span className="solo-lector">, pendiente</span></p>
        <p className="paso-fase__explicacion">{cambio.descripcion}</p>
        <p className="paso-fase__nota">
          <strong>Siguiente:</strong>{' '}
          {mirada === 'cliente'
            ? `${nombrePila(productOwner)} te enviará el costo y cuántos días agrega a la entrega. Solo se hace si lo aceptas.`
            : 'Cotízalo con un cambio de plan: costo y días que agrega a la entrega. Solo se hace si el cliente lo acepta.'}
        </p>
      </div>
    </li>
  );
}

function Bocetos({ adjuntos }: { adjuntos: PasoDetalle['adjuntos'] }) {
  return (
    <ul className="bocetos" aria-label="Bocetos y documentos">
      {adjuntos.map((a) => {
        const esImagen = a.tipoMime.startsWith('image/');
        return (
          <li key={a.id}>
            <a className="boceto" href={a.url} target="_blank" rel="noopener noreferrer">
              {esImagen
                ? <img src={a.url} alt={`Boceto: ${a.titulo}`} width={200} height={140} loading="lazy" />
                : <span className="boceto__documento" aria-hidden="true"><Icono nombre="portapapeles" grande /></span>}
              <span className="boceto__titulo">{a.titulo}{esImagen ? '' : ' (PDF)'}</span>
            </a>
          </li>
        );
      })}
    </ul>
  );
}
