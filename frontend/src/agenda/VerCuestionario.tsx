import { useEffect, useState } from 'react';

import { ErrorApi } from '../api/cliente';
import { Alerta } from '../componentes/Alerta';
import { Boton } from '../componentes/Boton';
import { Cargando } from '../componentes/Cargando';
import { Dialogo } from '../componentes/Dialogo';
import { Insignia } from '../componentes/Insignia';
import { IDIOMAS, PRESUPUESTOS, URGENCIAS, gestionCitas, type Cuestionario, type CitaGestion } from './gestion';

/** Cuestionario de una cita atendida, solo lectura (no se edita: RN-24, RNF-02). Lo ven el administrador y el equipo. */
export function VerCuestionario({ cita, alCerrar, texto }: { cita: CitaGestion | null; alCerrar: () => void; texto: string }) {
  const [datos, setDatos] = useState<Cuestionario | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!cita) return;
    let vigente = true;
    setDatos(null);
    setError(null);
    gestionCitas.cuestionario(cita.citaId)
      .then((q) => vigente && setDatos(q))
      .catch((e: unknown) => vigente && setError(e instanceof ErrorApi ? e.message : 'No pudimos cargar el cuestionario.'));
    return () => {
      vigente = false;
    };
  }, [cita]);

  const urgencia = datos ? URGENCIAS.find(([valor]) => valor === datos.urgencia) : undefined;
  const fila = (titulo: string, valor: React.ReactNode, ancha = false) => (
    <div className={['respuestas__fila', ancha && 'respuestas__fila--ancha'].filter(Boolean).join(' ')} key={titulo}>
      <dt>{titulo}</dt>
      <dd>{valor}</dd>
    </div>
  );

  return (
    <Dialogo
      abierto={cita !== null}
      alCerrar={alCerrar}
      ancho
      clase="dialogo-cuestionario"
      icono="portapapeles"
      titulo="Cuestionario de la cita"
      texto={texto}
      acciones={<Boton onClick={alCerrar}>Listo</Boton>}
    >
      {cita && (
        <div className="resumen-dialogo">
          <strong>{`${cita.cliente.nombre} · ${cita.servicio}`}</strong>
          <span>{`${cita.codigo} · ${cita.modalidad === 'VIRTUAL' ? 'Virtual' : 'Presencial'}`}</span>
        </div>
      )}
      {error && <Alerta tipo="error" titulo={error} />}
      {!datos && !error && <Cargando texto="Cargando el cuestionario…" />}
      {datos && (
        <dl className="respuestas">
          {fila('Resumen de la reunión', datos.resumen, true)}
          {fila('Descripción del cliente', datos.descripcionCliente, true)}
          {fila('Presupuesto aproximado', PRESUPUESTOS.find(([v]) => v === datos.presupuesto)?.[1] ?? datos.presupuesto)}
          {fila('Fecha deseada de entrega', datos.fechaDeseada || 'Sin fecha definida')}
          {fila('Idioma preferido', IDIOMAS.find(([v]) => v === datos.idioma)?.[1] ?? datos.idioma)}
          {fila('Nivel de urgencia', <Insignia variante={`urgencia-${datos.urgencia.toLowerCase()}` as 'urgencia-alta'}>{urgencia?.[1] ?? datos.urgencia}</Insignia>)}
        </dl>
      )}
    </Dialogo>
  );
}
