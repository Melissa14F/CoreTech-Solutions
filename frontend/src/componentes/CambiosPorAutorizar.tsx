import { useCallback, useEffect, useState } from 'react';

import { separarErrores } from '../paginas/acceso/erroresFormulario';
import { cambiosPlanApi, type Cambio } from '../proyectos/cambiosPlan';
import { esConflictoDeEstado } from '../seguimiento/api';
import { Alerta } from './Alerta';
import { Boton } from './Boton';
import { Dialogo } from './Dialogo';
import { DetalleCambioPlan } from './DetalleCambioPlan';
import { DialogoRechazoCambio } from './DialogoRechazoCambio';
import { useNotificar } from './Notificaciones';

/**
 * «Cambios de plan por autorizar» del administrador (HU-30, paso 3; prototipo: admin/proyectos.html): cada cambio con el plan
 * antes y después. Al autorizarlo el cliente lo ve y lo acepta; quitar un paso de una fase que cierra el equipo se aplica en el
 * acto. No se muestra si no hay nada por autorizar.
 */
export function CambiosPorAutorizar() {
  const notificar = useNotificar();
  const [cambios, setCambios] = useState<Cambio[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [autorizando, setAutorizando] = useState<Cambio | null>(null);
  const [rechazando, setRechazando] = useState<Cambio | null>(null);

  const cargar = useCallback(async () => {
    try {
      setCambios(await cambiosPlanApi.porAutorizar());
      setError(null);
    } catch (e) {
      setError(separarErrores(e).general ?? 'No pudimos cargar los cambios de plan.');
    }
  }, []);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  async function terminar(mensaje: string) {
    setAutorizando(null);
    setRechazando(null);
    if (mensaje) notificar(mensaje);
    await cargar();
  }

  if (error && !cambios) return <Alerta tipo="error">{error}</Alerta>;
  if (!cambios || cambios.length === 0) return null;

  return (
    <section className="cambios-por-autorizar" aria-labelledby="titulo-cambios-por-autorizar">
      <h2 className="grupo-agenda__titulo" id="titulo-cambios-por-autorizar">
        Cambios de plan por autorizar <span className="insignia insignia--pendiente">{cambios.length}</span>
      </h2>
      <ul className="lista-cambios">
        {cambios.map((c) => (
          <li key={c.id}>
            <p className="cambio-plan__datos"><strong>{c.proyecto}</strong><span>{c.proyectoCodigo}</span><span>Cliente: {c.cliente}</span></p>
            <DetalleCambioPlan cambio={c} />
            <div className="cambio-plan__acciones">
              <Boton variante="contorno" onClick={() => setRechazando(c)}>No autorizar</Boton>
              <Boton variante="acento" icono="exito" onClick={() => setAutorizando(c)}>{c.seAplicaAlAutorizar ? 'Autorizar y aplicar' : 'Autorizar y enviar'}</Boton>
            </div>
          </li>
        ))}
      </ul>
      {autorizando && <DialogoAutorizar cambio={autorizando} alCerrar={() => setAutorizando(null)} alTerminar={terminar} />}
      {rechazando && <DialogoRechazoCambio abierto cambioId={rechazando.id} quien="administrador" alCerrar={() => setRechazando(null)} alTerminar={terminar} />}
    </section>
  );
}

function DialogoAutorizar({ cambio, alCerrar, alTerminar }: { cambio: Cambio; alCerrar: () => void; alTerminar: (mensaje: string) => Promise<void> }) {
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function autorizar() {
    setEnviando(true);
    setError(null);
    try {
      await cambiosPlanApi.autorizar(cambio.id);
      setEnviando(false);
      await alTerminar(cambio.seAplicaAlAutorizar
        ? 'Paso quitado. El cliente recibe un aviso; el resto del plan sigue igual.'
        : `Cambio autorizado. ${cambio.cliente} lo recibe para aceptarlo o no.`);
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        await alTerminar(e.message);
        return;
      }
      setError(separarErrores(e).general);
    }
  }

  return (
    <Dialogo
      abierto alCerrar={() => !enviando && alCerrar()} icono="exito" tono="exito"
      titulo={cambio.seAplicaAlAutorizar ? '¿Autorizar y aplicar el cambio?' : '¿Autorizar y enviar al cliente?'}
      texto={cambio.seAplicaAlAutorizar
        ? 'El paso se quita de una vez. Al cliente solo se le avisa; las fases, el avance y la entrega no cambian.'
        : 'El cliente verá el plan antes y después, y lo aceptará o no. Mientras tanto el plan sigue igual.'}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton variante="exito" cargando={enviando} onClick={() => void autorizar()}>Sí, autorizar</Boton>
        </>
      )}
    >
      {error && <Alerta tipo="error">{error}</Alerta>}
    </Dialogo>
  );
}
