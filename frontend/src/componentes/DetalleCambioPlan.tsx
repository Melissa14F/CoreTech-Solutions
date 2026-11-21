import { fechaLegible, pesos } from '../proyectos/admin';
import { TEXTO_ESTADO, TEXTO_TIPO, type Cambio, type Foto } from '../proyectos/cambiosPlan';

const porcentaje = (valor: number) => `${String(Math.round(valor * 10) / 10).replace('.', ',')} %`;

/**
 * El plan antes y después de un cambio (HU-30, paso 3: fases con el valor de cada una, avance, entrega y valor total del
 * proyecto). El «después» marca lo que cambia: la fase nueva, la que se quita y la entrega.
 */
export function ComparacionPlan({ cambio }: { cambio: Cambio }) {
  const { antes, despues } = cambio;
  if (!antes || !despues) return null;
  return (
    <div className="comparacion-plan" role="group" aria-label="El plan antes y después del cambio">
      <Columna titulo="Hoy" foto={antes} quitada={cambio.tipo === 'QUITAR_FASE' ? cambio.faseNombre : null} />
      <Columna titulo="Con el cambio" foto={despues} nueva={cambio.tipo === 'AGREGAR_FASE' ? cambio.faseNombre : null} anterior={antes} />
    </div>
  );
}

function Columna({ titulo, foto, nueva, quitada, anterior }: { titulo: string; foto: Foto; nueva?: string | null; quitada?: string | null; anterior?: Foto }) {
  return (
    <section className={['comparacion-plan__columna', anterior && 'comparacion-plan__columna--despues'].filter(Boolean).join(' ')}>
      <h4 className="comparacion-plan__titulo">{titulo}</h4>
      <p className="comparacion-plan__resumen">{foto.fases.length} {foto.fases.length === 1 ? 'fase' : 'fases'}</p>
      <ol className="comparacion-plan__fases">
        {foto.fases.map((f, i) => {
          const esNueva = nueva && f.nombre === nueva && i >= 0;
          const seQuita = quitada && f.nombre === quitada;
          return (
            <li key={`${f.nombre}-${i}`} className={[esNueva && 'es-nueva', seQuita && 'se-quita'].filter(Boolean).join(' ')}>
              <span>{f.nombre}{esNueva && <small> · nueva</small>}{seQuita && <small> · se quita</small>}</span>
              <span>{porcentaje(f.porcentaje)}</span>
            </li>
          );
        })}
      </ol>
      <dl className="lista-datos">
        <div className="lista-datos__fila"><dt className="lista-datos__dato">Avance</dt><dd className="lista-datos__valor">{porcentaje(foto.avance)}</dd></div>
        <div className="lista-datos__fila">
          <dt className="lista-datos__dato">Entrega</dt>
          <dd className={['lista-datos__valor', anterior && anterior.entrega !== foto.entrega && 'cambia'].filter(Boolean).join(' ')}>{fechaLegible(foto.entrega)}</dd>
        </div>
        <div className="lista-datos__fila">
          <dt className="lista-datos__dato">Valor total</dt>
          <dd className={['lista-datos__valor', anterior && anterior.valorTotal !== foto.valorTotal && 'cambia'].filter(Boolean).join(' ')}>{pesos(foto.valorTotal)}</dd>
        </div>
      </dl>
    </section>
  );
}

/** Qué se cambia y por qué, con la comparación cuando el cambio sigue abierto. */
export function DetalleCambioPlan({ cambio: c, conComparacion = true }: { cambio: Cambio; conComparacion?: boolean }) {
  const estado = TEXTO_ESTADO[c.estado];
  return (
    <article className="cambio-plan">
      <header className="cambio-plan__cabecera">
        <h3 className="cambio-plan__titulo">{TEXTO_TIPO[c.tipo]}</h3>
        <span className={`insignia ${estado.clase}`}>{estado.texto}</span>
      </header>
      <p className="cambio-plan__que">{queSeCambia(c)}</p>
      {c.tipo === 'AGREGAR_FASE' && c.pasosNuevos && c.pasosNuevos.length > 0 && (
        <ul className="cambio-plan__pasos">{c.pasosNuevos.map((p, i) => <li key={`${p.titulo}-${i}`}>{p.titulo}</li>)}</ul>
      )}
      {c.tipo === 'AGREGAR_ALCANCE' && c.solicitud && <p className="cambio-plan__nota"><strong>Lo que pidió el cliente:</strong> «{c.solicitud}»</p>}
      {c.tipo === 'AGREGAR_ALCANCE' && c.explicacionPaso && <p className="cambio-plan__nota"><strong>Cómo se hará:</strong> {c.explicacionPaso}</p>}
      <p className="cambio-plan__nota"><strong>Motivo:</strong> {c.motivo}</p>
      <ul className="cambio-plan__datos">
        <li>Lo propone {c.propuestoPor}</li>
        {c.nuevaEntrega && <li>Nueva entrega: {fechaLegible(c.nuevaEntrega)}</li>}
        {c.tipo !== 'QUITAR_PASO' && c.tipo !== 'CAMBIAR_FECHA' && <li>Valor adicional: {pesos(c.valorAdicional)}</li>}
      </ul>
      {c.motivoRechazo && <p className="cambio-plan__nota"><strong>{c.estado === 'RECHAZADO_CLIENTE' ? 'Motivo del cliente' : 'Motivo del rechazo'}:</strong> {c.motivoRechazo}</p>}
      {conComparacion && <ComparacionPlan cambio={c} />}
    </article>
  );
}

function queSeCambia(c: Cambio): string {
  switch (c.tipo) {
    case 'AGREGAR_FASE':
      return `Se agrega la fase «${c.faseNombre}» en la posición ${c.posicion}${c.apruebaCliente ? ': la aprueba el cliente' : ': la cierra el equipo'}.`;
    case 'QUITAR_FASE':
      return `Se quita la fase «${c.faseNombre}», que todavía no empieza.`;
    case 'CAMBIAR_FECHA':
      return 'Solo cambia la fecha de entrega; las fases y los pasos siguen igual.';
    case 'QUITAR_PASO':
      return `Se quita el paso «${c.pasoTitulo}» de ${c.pasoFase}, que todavía no empieza. No se borra: queda como quitado.`;
    default:
      return `Se agrega el trabajo nuevo «${c.tituloPaso}» a la fase en curso.`;
  }
}
