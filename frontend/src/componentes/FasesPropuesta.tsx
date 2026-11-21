import { porcentajeFase, type FaseApi } from '../proyectos/plan';
import { Icono } from './Icono';
import { Insignia } from './Insignia';

interface PropiedadesFasesPropuesta {
  fases: FaseApi[];
  /** Quién la mira: el cliente ve «Tú la apruebas»; el equipo, «La aprueba {nombre}». */
  vista: { tipo: 'cliente' } | { tipo: 'equipo'; cliente: string };
}

/**
 * Las fases del plan en lectura, como las ve el cliente en su propuesta y el equipo después de enviarla
 * (prototipo: .fases-propuesta). Cada fase vale lo mismo y se dice cuál aprueba el cliente.
 */
export function FasesPropuesta({ fases, vista }: PropiedadesFasesPropuesta) {
  const porcentaje = porcentajeFase(fases.length);
  return (
    <ol className="fases-propuesta">
      {fases.map((fase) => (
        <li key={fase.orden} className={['fase-propuesta', fase.apruebaCliente && 'fase-propuesta--cliente'].filter(Boolean).join(' ')}>
          <div>
            <div className="fase-propuesta__cabecera">
              <h3 className="fase-propuesta__titulo">{fase.nombre}</h3>
              {fase.apruebaCliente
                ? <Insignia variante="aprueba"><Icono nombre="persona" />{vista.tipo === 'cliente' ? 'Tú la apruebas' : `La aprueba ${vista.cliente}`}</Insignia>
                : <Insignia variante="neutra">La cierra el equipo</Insignia>}
            </div>
            <p className="fase-propuesta__que-es">{[fase.queEs, `Vale ${porcentaje} del proyecto.`].filter(Boolean).join(' ')}</p>
            <ul className="fase-propuesta__pasos">
              {fase.pasos.map((paso) => (
                <li key={paso.orden}>
                  <span>
                    <strong>{paso.titulo.replace(/\.$/, '')}.</strong>
                    {paso.explicacion && <> <span>{paso.explicacion}</span></>}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </li>
      ))}
    </ol>
  );
}
