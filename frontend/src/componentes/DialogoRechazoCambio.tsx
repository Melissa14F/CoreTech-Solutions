import { useEffect, useState, type FormEvent } from 'react';

import { separarErrores } from '../paginas/acceso/erroresFormulario';
import { MAX_RECHAZO, MIN_RECHAZO, cambiosPlanApi } from '../proyectos/cambiosPlan';
import { esConflictoDeEstado } from '../seguimiento/api';
import { Alerta } from './Alerta';
import { Boton } from './Boton';
import { Campo } from './Campo';
import { Dialogo } from './Dialogo';

interface Propiedades {
  abierto: boolean;
  cambioId: string;
  /** Quién rechaza: el administrador («No autorizar») o el cliente («No aceptar»). */
  quien: 'administrador' | 'cliente';
  alCerrar: () => void;
  /** Cierra el diálogo, avisa al usuario y vuelve a pedir lo que se veía; también con un 409 (el cambio ya se respondió). */
  alTerminar: (mensaje: string) => Promise<void>;
}

/**
 * El motivo de un rechazo, de 10 a 400 caracteres (HU-30): el administrador no autoriza un cambio propuesto o el cliente no
 * acepta uno autorizado. El plan sigue igual.
 */
export function DialogoRechazoCambio({ abierto, cambioId, quien, alCerrar, alTerminar }: Propiedades) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const administrador = quien === 'administrador';

  useEffect(() => {
    if (abierto) {
      setMotivo('');
      setError(null);
      setGeneral(null);
    }
  }, [abierto]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = motivo.trim();
    if (!limpio) { setError('Escribe el motivo: el equipo lo verá.'); return; }
    if (limpio.length < MIN_RECHAZO) { setError(`Escribe al menos ${MIN_RECHAZO} caracteres.`); return; }
    setError(null);
    setGeneral(null);
    setEnviando(true);
    try {
      await cambiosPlanApi.rechazar(cambioId, limpio);
      setEnviando(false);
      await alTerminar(administrador ? 'Cambio no autorizado. El equipo recibe tu motivo y el plan sigue igual.' : 'Cambio no aceptado. Tu plan sigue igual.');
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        await alTerminar(e.message);
        return;
      }
      const { porCampo, general: otro } = separarErrores(e);
      setError(porCampo.motivo ?? null);
      setGeneral(otro);
    }
  }

  return (
    <Dialogo
      abierto={abierto} alCerrar={() => !enviando && alCerrar()} icono="cerrar" tono="peligro"
      titulo={administrador ? 'No autorizar el cambio' : 'No aceptar el cambio'}
      texto={administrador ? 'El Product Owner verá tu motivo. El plan sigue igual y puede proponer otro cambio.'
        : 'El equipo y la administración verán tu motivo. Tu plan sigue igual.'}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form="formulario-rechazo-cambio" variante="peligro" cargando={enviando}>
            {administrador ? 'No autorizar' : 'No aceptar'}
          </Boton>
        </>
      )}
    >
      <form id="formulario-rechazo-cambio" onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <Campo etiqueta="Motivo" error={error ?? undefined} contador={{ actual: motivo.length, maximo: MAX_RECHAZO }}>
          <textarea className="campo__control--area" rows={3} value={motivo} maxLength={MAX_RECHAZO} onChange={(e) => setMotivo(e.target.value)} />
        </Campo>
      </form>
    </Dialogo>
  );
}
