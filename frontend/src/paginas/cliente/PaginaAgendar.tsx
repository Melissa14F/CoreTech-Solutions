import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { citas, type CitaCreada } from '../../agenda/api';
import { BORRADOR_INICIAL, ResumenCita, SERVICIO_OCULTO, type Borrador, type ErroresPaso2 } from '../../agenda/borrador';
import { CitaRegistrada } from '../../agenda/CitaRegistrada';
import { PasoConfirmar } from '../../agenda/PasoConfirmar';
import { PasoFecha } from '../../agenda/PasoFecha';
import { PasoServicio } from '../../agenda/PasoServicio';
import { ErrorApi } from '../../api/cliente';
import { catalogo, type ServicioPublico } from '../../catalogo/api';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import '../../styles/vistas/agendar-servicio.css';
import '../../styles/vistas/agendar-fecha.css';
import '../../styles/vistas/agendar-confirmar.css';
import '../../styles/vistas/cita-registrada.css';
import '../../styles/vistas/agenda.css';

type Paso = 1 | 2 | 3 | 4;

const PASOS = ['Servicio', 'Fecha y detalles', 'Confirmación'];
const TEXTOS: Record<1 | 2 | 3, { titulo: string; texto: string }> = {
  1: { titulo: 'Agendar una cita', texto: 'Cuéntanos qué necesitas y elige cuándo reunirte con nuestro equipo.' },
  2: { titulo: 'Agendar una cita', texto: 'Elige el día y la hora, cómo prefieres reunirte y cuéntanos qué necesitas.' },
  3: { titulo: 'Confirma tu cita', texto: 'Revisa que todo esté bien antes de enviarla. Puedes cambiar cualquier dato.' },
};

/** Campos de la API que pertenecen al paso 2 (para volver ahí cuando el servidor los rechaza). */
const CAMPO_DEL_PASO_2: Record<string, keyof ErroresPaso2> = {
  fechaHora: 'hora', modalidad: 'modalidad', descripcionNecesidad: 'descripcion', pais: 'fecha', zonaHoraria: 'fecha',
};

/**
 * Asistente para agendar una cita en 3 pasos: servicio → fecha y detalles → confirmación (HU-01).
 * Lo elegido vive aquí, así volver atrás no pierde nada. Prototipo: cliente/agendar-servicio.html,
 * agendar-fecha.html, agendar-confirmar.html y cita-registrada.html.
 */
export function PaginaAgendar() {
  useTituloDocumento('Agendar cita');
  const notificar = useNotificar();
  const [parametros] = useSearchParams();
  const slug = parametros.get('servicio');

  const [servicios, setServicios] = useState<ServicioPublico[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [intento, setIntento] = useState(0);
  const [paso, setPaso] = useState<Paso>(1);
  const [borrador, setBorrador] = useState<Borrador>(BORRADOR_INICIAL);
  const [errores, setErrores] = useState<ErroresPaso2>({});
  const [aviso, setAviso] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [enviando, setEnviando] = useState(false);
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null);
  const [registrada, setRegistrada] = useState<CitaCreada | null>(null);
  const encabezado = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let vigente = true;
    setErrorCarga(null);
    catalogo.visibles()
      .then((lista) => {
        if (!vigente) return;
        setServicios(lista);
        // "Agendar cita" desde una tarjeta del catálogo llega con ?servicio=slug
        const elegido = slug ? lista.find((s) => s.slug === slug) : undefined;
        if (elegido) setBorrador((previo) => (previo.servicio ? previo : { ...previo, servicio: elegido }));
      })
      .catch((e: unknown) => vigente && setErrorCarga(e instanceof ErrorApi ? e.message : 'No pudimos cargar los servicios.'));
    return () => {
      vigente = false;
    };
  }, [slug, intento]);

  // Cada paso abre desde arriba y el foco va a su título (quien usa lector de pantalla sabe dónde está)
  const primeraVez = useRef(true);
  useEffect(() => {
    if (primeraVez.current) {
      primeraVez.current = false;
      return;
    }
    window.scrollTo(0, 0);
    encabezado.current?.focus();
  }, [paso]);

  const cambiar = useCallback((cambios: Partial<Borrador>) => setBorrador((previo) => ({ ...previo, ...cambios })), []);

  function elegirServicio(servicio: ServicioPublico) {
    // Otro servicio, otra disponibilidad: se borran el día y la hora, no lo demás
    setBorrador((previo) => (previo.servicio?.servicioId === servicio.servicioId
      ? previo
      : { ...previo, servicio, fecha: null, franja: null }));
  }

  /** 404 del servicio (lo ocultaron mientras agendaba): vuelve al paso 1 con el mensaje de la HU. */
  const servicioNoDisponible = useCallback(() => {
    setBorrador((previo) => ({ ...previo, servicio: null, fecha: null, franja: null }));
    setAviso(null);
    setErrorEnvio(null);
    setIntento((n) => n + 1);
    setPaso(1);
    notificar('Este servicio ya no está disponible', 'error');
  }, [notificar]);

  async function confirmar() {
    const { servicio, franja, modalidad, descripcion, pais, zona } = borrador;
    if (!servicio || !franja || !modalidad) return;
    setEnviando(true);
    setErrorEnvio(null);
    try {
      const cita = await citas.crear({
        servicioId: servicio.servicioId,
        fechaHora: franja.fechaHora,
        modalidad,
        descripcionNecesidad: descripcion.trim(),
        pais,
        zonaHoraria: zona,
      });
      setRegistrada(cita);
      setPaso(4);
      notificar('Cita registrada. Te enviamos la confirmación a tu correo.');
    } catch (e) {
      if (!(e instanceof ErrorApi)) {
        setErrorEnvio('Ocurrió un error inesperado. Intenta de nuevo en unos minutos.');
      } else if (e.estado === 404 && e.message === SERVICIO_OCULTO) {
        servicioNoDisponible();
      } else if (e.estado === 409 || (e.estado === 422 && 'fechaHora' in e.porCampo)) {
        // La franja se ocupó (o ya no vale): vuelve al paso 2 conservando el servicio y la descripción
        cambiar({ fecha: null, franja: null });
        setErrores({});
        setAviso(e.porCampo.fechaHora ?? e.message);
        setVersion((n) => n + 1);
        setPaso(2);
      } else if (e.estado === 422 && Object.keys(e.porCampo).length > 0) {
        const delServidor: ErroresPaso2 = {};
        for (const [campo, mensaje] of Object.entries(e.porCampo)) {
          const propio = CAMPO_DEL_PASO_2[campo];
          if (propio) delServidor[propio] = mensaje;
        }
        setErrores(delServidor);
        setAviso(null);
        setPaso(2);
      } else {
        setErrorEnvio(e.message);
      }
    } finally {
      setEnviando(false);
    }
  }

  const cabecera = (
    <div className="encabezado-vista">
      <div>
        <h1 className="encabezado-vista__titulo">{TEXTOS[1].titulo}</h1>
        <p className="encabezado-vista__texto">{TEXTOS[1].texto}</p>
      </div>
    </div>
  );

  if (errorCarga && !servicios) {
    return (
      <>
        {cabecera}
        <Alerta tipo="error" titulo="No pudimos cargar los servicios">{errorCarga}</Alerta>
        <p><Boton variante="contorno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton></p>
      </>
    );
  }
  if (!servicios) return <>{cabecera}<Cargando texto="Cargando servicios…" /></>;

  if (paso === 4 && registrada) return <CitaRegistrada cita={registrada} borrador={borrador} />;

  const actual = paso as 1 | 2 | 3;
  const volver = actual === 1
    ? <Link className="volver" to="/cliente/citas"><FlechaAtras /> Volver a mis citas</Link>
    : <button type="button" className="volver" onClick={() => setPaso((actual - 1) as Paso)}><FlechaAtras /> Volver al paso {actual - 1}</button>;

  return (
    <>
      {volver}
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo" ref={encabezado} tabIndex={-1}>{TEXTOS[actual].titulo}</h1>
          <p className="encabezado-vista__texto">{TEXTOS[actual].texto}</p>
        </div>
      </div>

      <ol className="pasos-cita" aria-label="Pasos para agendar">
        {PASOS.map((nombre, i) => (
          <li
            key={nombre}
            className={['pasos-cita__paso', i + 1 < actual && 'pasos-cita__paso--hecho', i + 1 === actual && 'pasos-cita__paso--actual'].filter(Boolean).join(' ')}
            aria-current={i + 1 === actual ? 'step' : undefined}
          >
            <span className="pasos-cita__texto">{nombre}<small>Paso {i + 1} de 3</small></span>
          </li>
        ))}
        <li className="pasos-cita__progreso" aria-hidden="true"><span style={{ '--avance': `${(actual * 100) / 3}%` } as CSSProperties} /></li>
      </ol>

      {actual === 1 && (
        <div className="agendamiento">
          <PasoServicio
            servicios={servicios}
            elegido={borrador.servicio}
            alElegir={elegirServicio}
            alContinuar={() => setPaso(2)}
          />
          {servicios.length > 0 && <ResumenCita borrador={borrador} paso={1} />}
        </div>
      )}

      {actual === 2 && (
        <div className="agendamiento">
          <PasoFecha
            borrador={borrador}
            cambiar={cambiar}
            errores={errores}
            setErrores={setErrores}
            aviso={aviso}
            version={version}
            alServicioNoDisponible={servicioNoDisponible}
            alVolver={() => setPaso(1)}
            alContinuar={() => {
              setAviso(null);
              setPaso(3);
            }}
          />
          <ResumenCita borrador={borrador} paso={2} />
        </div>
      )}

      {actual === 3 && (
        <PasoConfirmar
          borrador={borrador}
          enviando={enviando}
          error={errorEnvio}
          alCambiar={(destino) => setPaso(destino)}
          alVolver={() => setPaso(2)}
          alConfirmar={confirmar}
        />
      )}
    </>
  );
}

function FlechaAtras() {
  return <svg className="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5M11 19l-7-7 7-7" /></svg>;
}
