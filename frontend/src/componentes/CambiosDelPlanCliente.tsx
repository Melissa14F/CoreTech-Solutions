import { useCallback, useEffect, useState } from 'react';

import { separarErrores } from '../paginas/acceso/erroresFormulario';
import { fechaLegible, pesos } from '../proyectos/admin';
import { cambiosPlanApi, type Cambio, type CambiosDelProyecto } from '../proyectos/cambiosPlan';
import { esConflictoDeEstado } from '../seguimiento/api';
import { Alerta } from './Alerta';
import { Boton } from './Boton';
import { Dialogo } from './Dialogo';
import { DetalleCambioPlan } from './DetalleCambioPlan';
import { DialogoRechazoCambio } from './DialogoRechazoCambio';
import { useNotificar } from './Notificaciones';

interface Propiedades {
  codigo: string;
  /** Después de aceptar el cambio el plan ya es otro: el padre vuelve a pedir el proyecto. */
  alAplicar: () => Promise<void>;
}

/**
 * Los cambios del plan que el administrador autorizó y el cliente debe responder (HU-30, paso 4; prototipo: la tarjeta de cambio
 * de cliente/proyecto-tienda-en-linea.html): el plan antes y después, con «Aceptar cambio» o «No aceptar». Debajo, lo ya
 * respondido y el valor total del proyecto (cotización inicial más los adicionales aceptados). No se muestra si no hay nada.
 */
export function CambiosDelPlanCliente({ codigo, alAplicar }: Propiedades) {
  const notificar = useNotificar();
  const [datos, setDatos] = useState<CambiosDelProyecto | null>(null);
  const [aceptando, setAceptando] = useState<Cambio | null>(null);
  const [rechazando, setRechazando] = useState<Cambio | null>(null);

  const cargar = useCallback(async () => {
    try {
      setDatos(await cambiosPlanApi.deProyecto(codigo));
    } catch {
      // Sin cambios que mostrar: la pantalla sigue con el resto del proyecto
      setDatos(null);
    }
  }, [codigo]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  async function terminar(mensaje: string, cambioElPlan: boolean) {
    setAceptando(null);
    setRechazando(null);
    if (mensaje) notificar(mensaje);
    await cargar();
    if (cambioElPlan) await alAplicar();
  }

  if (!datos || datos.cambios.length === 0) return null;
  const pendientes = datos.cambios.filter((c) => c.puedeAceptar);
  const respondidos = datos.cambios.filter((c) => !c.puedeAceptar);

  return (
    <section className="tarjeta aparecer cambios-del-plan" aria-labelledby="titulo-cambios-plan">
      <h2 className="tarjeta__titulo" id="titulo-cambios-plan">Cambios en tu plan</h2>
      {pendientes.map((c) => (
        <div key={c.id} className="cambio-plan__pendiente">
          <Alerta tipo="info">El equipo propone un cambio y la administración ya lo autorizó. Tu plan sigue igual hasta que lo aceptes.</Alerta>
          <DetalleCambioPlan cambio={c} />
          <div className="cambio-plan__acciones">
            <Boton variante="contorno" onClick={() => setRechazando(c)}>No aceptar</Boton>
            <Boton variante="acento" icono="exito" onClick={() => setAceptando(c)}>Aceptar cambio</Boton>
          </div>
        </div>
      ))}
      {respondidos.length > 0 && (
        <details className="cambios-del-plan__historial">
          <summary>Cambios anteriores ({respondidos.length})</summary>
          <ul className="lista-cambios">
            {respondidos.map((c) => <li key={c.id}><DetalleCambioPlan cambio={c} conComparacion={false} /></li>)}
          </ul>
        </details>
      )}
      {datos.valorAdicionalAceptado > 0 && (
        <p className="cambio-plan__nota">
          Valor total del proyecto: <strong>{pesos(datos.valorTotal)}</strong> (cotización inicial {pesos(datos.valorCotizado)} más {pesos(datos.valorAdicionalAceptado)} de cambios aceptados).
        </p>
      )}
      {aceptando && <DialogoAceptar cambio={aceptando} alCerrar={() => setAceptando(null)} alTerminar={terminar} />}
      {rechazando && (
        <DialogoRechazoCambio
          abierto cambioId={rechazando.id} quien="cliente" alCerrar={() => setRechazando(null)}
          alTerminar={(mensaje) => terminar(mensaje, false)}
        />
      )}
    </section>
  );
}

function DialogoAceptar({ cambio, alCerrar, alTerminar }: { cambio: Cambio; alCerrar: () => void; alTerminar: (mensaje: string, cambioElPlan: boolean) => Promise<void> }) {
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function aceptar() {
    setEnviando(true);
    setError(null);
    try {
      const r = await cambiosPlanApi.aceptar(cambio.id);
      setEnviando(false);
      await alTerminar(`Cambio aceptado. Tu plan ya está actualizado: entrega el ${fechaLegible(r.fechaEstimadaEntrega)} y valor total ${pesos(r.valorTotal)}.`, true);
    } catch (e) {
      setEnviando(false);
      if (esConflictoDeEstado(e)) {
        // «El plan cambió desde que se propuso…» o el cambio ya se respondió: se muestra y se vuelve a pedir lo último
        await alTerminar(e.message, true);
        return;
      }
      setError(separarErrores(e).general);
    }
  }

  return (
    <Dialogo
      abierto alCerrar={() => !enviando && alCerrar()} icono="exito" tono="exito" titulo="¿Aceptar este cambio?"
      texto="Al aceptarlo el cambio se aplica a tu plan y queda registrado. Lo que ya está aprobado no cambia."
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton variante="exito" cargando={enviando} onClick={() => void aceptar()}>Sí, aceptar</Boton>
        </>
      )}
    >
      {error && <Alerta tipo="error">{error}</Alerta>}
    </Dialogo>
  );
}
