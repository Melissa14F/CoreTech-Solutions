import type { ReactNode } from 'react';

import { Alerta } from '../componentes/Alerta';
import { Boton } from '../componentes/Boton';
import { Icono, type NombreIcono } from '../componentes/Icono';
import { srcImagen } from '../catalogo/api';
import { useUsuario } from '../sesion/ContextoSesion';
import { DETALLE_MODALIDAD, TEXTO_MODALIDAD, type Borrador } from './borrador';
import { textoDiaCompleto, textoHoraColombia, textoRango } from './fechas';
import { COLOMBIA, textoZona } from './paises';

interface Propiedades {
  borrador: Borrador;
  enviando: boolean;
  /** Error que no es de un campo (sin permiso, sin conexión…). */
  error: string | null;
  alCambiar: (paso: 1 | 2) => void;
  alVolver: () => void;
  alConfirmar: () => void;
}

function Fila({ icono, dato, valor, nota, cambiar, etiquetaCambiar, imagen }:
  { icono?: NombreIcono; dato: string; valor: ReactNode; nota?: ReactNode; cambiar?: () => void; etiquetaCambiar?: string; imagen?: string }) {
  return (
    <div className="detalle-cita__fila">
      {imagen
        ? <img className="detalle-cita__imagen" src={imagen} alt="" width={44} height={44} />
        : <span className="detalle-cita__icono">{icono && <Icono nombre={icono} grande />}</span>}
      <dl className="detalle-cita__par">
        <dt className="detalle-cita__dato">{dato}</dt>
        <dd className={['detalle-cita__valor', dato === 'Lo que necesitas' && 'detalle-cita__valor--texto'].filter(Boolean).join(' ')}>{valor}</dd>
        {nota && <dd className="detalle-cita__nota">{nota}</dd>}
      </dl>
      {cambiar && (
        <button type="button" className="enlace detalle-cita__cambiar" onClick={cambiar} aria-label={`Cambiar ${etiquetaCambiar ?? dato.toLowerCase()}`}>
          Cambiar
        </button>
      )}
    </div>
  );
}

/** Paso 3 de 3: revisar y confirmar (prototipo/cliente/agendar-confirmar.html). */
export function PasoConfirmar({ borrador, enviando, error, alCambiar, alVolver, alConfirmar }: Propiedades) {
  const usuario = useUsuario();
  const { servicio, fecha, franja, modalidad, descripcion, pais, zona } = borrador;
  if (!servicio || !fecha || !franja || !modalidad) return null;
  const fueraDeColombia = zona !== COLOMBIA;

  return (
    <div className="agendamiento">
      <form
        className="agendamiento__principal"
        noValidate
        onSubmit={(evento) => {
          evento.preventDefault();
          alConfirmar();
        }}
      >
        {error && <div className="agendamiento__alerta"><Alerta tipo="error">{error}</Alerta></div>}

        <section className="tarjeta" aria-labelledby="titulo-detalle">
          <h2 className="agendamiento__titulo-bloque" id="titulo-detalle">Tu cita</h2>
          <p className="agendamiento__ayuda">Esto es lo que vamos a registrar.</p>
          <div className="detalle-cita">
            <Fila imagen={srcImagen(servicio.imagenUrl)} dato="Servicio" valor={servicio.nombre} cambiar={() => alCambiar(1)} />
            <Fila icono="agendar" dato="Fecha" valor={textoDiaCompleto(fecha)} cambiar={() => alCambiar(2)} />
            <Fila icono="reloj" dato="Hora" valor={textoRango(franja.fechaHora)} cambiar={() => alCambiar(2)} />
            <Fila
              icono="ubicacion"
              dato="País y zona horaria"
              valor={textoZona(pais, zona)}
              nota={fueraDeColombia ? `En Colombia: ${textoHoraColombia(franja.fechaHora, franja.horaColombia, true)}` : undefined}
              cambiar={() => alCambiar(2)}
            />
            <Fila
              icono={modalidad === 'VIRTUAL' ? 'informacion' : 'ubicacion'}
              dato="Modalidad"
              valor={TEXTO_MODALIDAD[modalidad]}
              nota={modalidad === 'VIRTUAL' ? 'Por videollamada. Te enviaremos el enlace cuando tu cita sea confirmada.' : DETALLE_MODALIDAD.PRESENCIAL}
              cambiar={() => alCambiar(2)}
            />
            <Fila icono="mensaje" dato="Lo que necesitas" valor={descripcion.trim()} cambiar={() => alCambiar(2)} />
            <Fila icono="persona" dato="A nombre de" valor={usuario?.nombre ?? ''} nota={usuario?.email} />
          </div>
        </section>

        <div className="alerta alerta--info aviso-estado" role="note">
          <Icono nombre="informacion" />
          <div>
            <strong>Tu cita quedará pendiente.</strong>
            <p>Te asignaremos un equipo y su Product Owner la confirmará. Mientras siga pendiente, puedes cancelarla desde Mis citas.</p>
          </div>
        </div>

        <div className="acciones-paso">
          <Boton variante="contorno" icono="chevronIzquierda" onClick={alVolver} disabled={enviando}>Atrás</Boton>
          <div className="acciones-paso__derecha">
            <Boton type="submit" variante="acento" tamano="grande" icono="exito" cargando={enviando}>Confirmar cita</Boton>
          </div>
        </div>

        {/* Aviso informativo: el cobro de la cita será una pasarela de pagos (alcance futuro, módulo pagos) */}
        <p className="aviso-pago">
          <svg className="icono" viewBox="0 0 24 24" aria-hidden="true"><rect x="2" y="5" width="20" height="14" rx="2" /><path d="M2 10h20M6 15h4" /></svg>
          <span>Para agendar la cita deberás realizar su pago antes de confirmarla.</span>
        </p>
      </form>

      <aside className="tarjeta resumen-cita" style={{ ['--retraso' as string]: '120ms' }} aria-labelledby="titulo-despues">
        <h2 className="tarjeta__titulo" id="titulo-despues">Qué pasa después</h2>
        <ol className="lista-fases">
          <li className="lista-fases__item lista-fases__item--actual"><span className="lista-fases__punto" />Registras la cita</li>
          <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Te asignamos un equipo</li>
          <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Él confirma la cita</li>
          <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Se reúnen y crean tu proyecto</li>
        </ol>
      </aside>
    </div>
  );
}
