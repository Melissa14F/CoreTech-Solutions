import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';

import { ErrorApi } from '../api/cliente';
import { Alerta } from '../componentes/Alerta';
import { Boton } from '../componentes/Boton';
import { Campo } from '../componentes/Campo';
import { Cargando } from '../componentes/Cargando';
import { Icono } from '../componentes/Icono';
import { useNotificar } from '../componentes/Notificaciones';
import { citas, type Franja, type Modalidad } from './api';
import {
  DESCRIPCION_MAXIMA, DESCRIPCION_MINIMA, SERVICIO_OCULTO, DETALLE_MODALIDAD, TEXTO_MODALIDAD,
  type Borrador, type CampoPaso2, type ErroresPaso2,
} from './borrador';
import { Calendario } from './Calendario';
import { hoyEn, mesDe, partes, sumarDias, textoCorto, textoDia, textoHora, textoHoraColombia } from './fechas';
import { COLOMBIA, PAISES, PAIS_NEGOCIO } from './paises';

const MENSAJES: Record<CampoPaso2, string> = {
  fecha: 'Elige el día de tu cita.',
  hora: 'Elige la hora de tu cita.',
  modalidad: 'Elige si la cita es presencial o virtual.',
  descripcion: 'Cuéntanos brevemente qué necesitas.',
};
const MENSAJE_CORTO = `Escribe un poco más (mínimo ${DESCRIPCION_MINIMA} caracteres) para entender tu necesidad.`;
const MENSAJE_GENERAL = 'Completa el día, la hora, la modalidad y lo que necesitas para continuar.';

const PERIODOS: [string, number, number][] = [['Madrugada', 0, 5], ['Mañana', 5, 12], ['Tarde', 12, 18], ['Noche', 18, 24]];

/** Errores del paso, con el mismo texto del prototipo (el de la API se muestra tal cual cuando es del servidor). */
export function validarPaso2(b: Borrador): ErroresPaso2 {
  const errores: ErroresPaso2 = {};
  if (!b.fecha) errores.fecha = MENSAJES.fecha;
  if (!b.franja) errores.hora = MENSAJES.hora;
  if (!b.modalidad) errores.modalidad = MENSAJES.modalidad;
  const necesidad = b.descripcion.trim();
  if (!necesidad) errores.descripcion = MENSAJES.descripcion;
  else if (necesidad.length < DESCRIPCION_MINIMA) errores.descripcion = MENSAJE_CORTO;
  return errores;
}

interface Propiedades {
  borrador: Borrador;
  cambiar: (cambios: Partial<Borrador>) => void;
  errores: ErroresPaso2;
  setErrores: Dispatch<SetStateAction<ErroresPaso2>>;
  /** Mensaje de arriba, por ejemplo "La hora que elegiste ya no está disponible, elige otra". */
  aviso: string | null;
  /** Cambia cada vez que hay que volver a pedir la disponibilidad (la franja se ocupó). */
  version: number;
  alServicioNoDisponible: () => void;
  alVolver: () => void;
  alContinuar: () => void;
}

/** Paso 2 de 3: país y zona, calendario, hora, modalidad y descripción (prototipo/cliente/agendar-fecha.html). */
export function PasoFecha({ borrador, cambiar, errores, setErrores, aviso, version, alServicioNoDisponible, alVolver, alContinuar }: Propiedades) {
  const notificar = useNotificar();
  const { servicio, pais, zona, fecha, franja, modalidad, descripcion } = borrador;
  const zonas = PAISES[pais] ?? PAISES[PAIS_NEGOCIO] ?? [];
  const hoy = useMemo(() => hoyEn(zona), [zona]);
  const mesMinimo = mesDe(hoy);
  const mesMaximo = mesDe(sumarDias(hoy, 31));

  const [mes, setMes] = useState(() => (fecha ? mesDe(fecha) : mesMinimo));
  const [franjasPorMes, setFranjasPorMes] = useState<Record<string, Franja[]>>({});
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [intento, setIntento] = useState(0);
  const formulario = useRef<HTMLFormElement>(null);
  const [pedidoFoco, setPedidoFoco] = useState(0);

  const clave = (m: string) => `${zona}|${m}`;
  const franjas = franjasPorMes[clave(mes)] ?? null;

  // Una franja que se ocupó (409): se descarta lo guardado y se vuelve a pedir.
  const primeraVez = useRef(true);
  useEffect(() => {
    if (primeraVez.current) {
      primeraVez.current = false;
      return;
    }
    setFranjasPorMes({});
  }, [version]);

  useEffect(() => {
    if (!servicio || franjasPorMes[clave(mes)]) return;
    let vigente = true;
    setErrorCarga(null);
    citas.disponibilidad(servicio.servicioId, mes, zona)
      .then((lista) => vigente && setFranjasPorMes((previo) => ({ ...previo, [clave(mes)]: lista })))
      .catch((e: unknown) => {
        if (!vigente) return;
        if (e instanceof ErrorApi && e.estado === 404 && e.message === SERVICIO_OCULTO) {
          alServicioNoDisponible();
          return;
        }
        setErrorCarga(e instanceof ErrorApi ? e.message : 'No pudimos cargar los horarios. Intenta de nuevo.');
      });
    return () => {
      vigente = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [servicio, mes, zona, intento, version, franjasPorMes]);

  const limpiar = useCallback((campo: CampoPaso2) => setErrores((previo) => ({ ...previo, [campo]: undefined })), [setErrores]);

  function cambiarPais(nuevoPais: string) {
    const nuevaZona = PAISES[nuevoPais]?.[0]?.id ?? COLOMBIA;
    // Los horarios cambian con la zona: se borran el día y la hora elegidos (HU-01, excepciones)
    cambiar({ pais: nuevoPais, zona: nuevaZona, fecha: null, franja: null });
    setMes(mesDe(hoyEn(nuevaZona)));
    setErrores((previo) => ({ ...previo, fecha: undefined, hora: undefined }));
  }

  function cambiarZona(nuevaZona: string) {
    cambiar({ zona: nuevaZona, fecha: null, franja: null });
    setMes(mesDe(hoyEn(nuevaZona)));
    setErrores((previo) => ({ ...previo, fecha: undefined, hora: undefined }));
  }

  const delDia = useMemo(
    () => (fecha && franjas ? franjas.filter((f) => f.fecha === fecha).sort((a, b) => a.hora.localeCompare(b.hora)) : []),
    [fecha, franjas],
  );
  const visibles = delDia.filter((f) => f.estado !== 'NO_HABIL' || delDia.some((o) => o.estado === 'LIBRE'));
  const libres = delDia.filter((f) => f.estado === 'LIBRE').length;
  const totalDia = delDia.filter((f) => f.estado !== 'NO_HABIL').length;
  const otraZona = zona !== COLOMBIA;

  const desde = hoyEn(zona, new Date(Date.now() + 24 * 3600_000));
  const hasta = hoyEn(zona, new Date(Date.now() + 30 * 24 * 3600_000));

  useEffect(() => {
    if (pedidoFoco === 0) return;
    const raiz = formulario.current;
    if (!raiz) return;
    const selectores: Record<CampoPaso2, string> = {
      fecha: '[data-grupo="fecha"] button[tabindex="0"]',
      hora: '[data-grupo="hora"] input:not(:disabled)',
      modalidad: '[data-grupo="modalidad"] input',
      descripcion: '#cita-descripcion',
    };
    const primero = (['fecha', 'hora', 'modalidad', 'descripcion'] as CampoPaso2[]).find((c) => errores[c]);
    if (primero) raiz.querySelector<HTMLElement>(selectores[primero])?.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pedidoFoco]);

  function enviar() {
    const encontrados = validarPaso2(borrador);
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) {
      notificar(MENSAJE_GENERAL, 'error');
      setPedidoFoco((n) => n + 1);
      return;
    }
    alContinuar();
  }

  const mensajeGrupo = (campo: CampoPaso2, id: string) => (
    <p className="campo__mensaje grupo-opciones__mensaje" id={id} role={errores[campo] ? 'alert' : undefined}>
      {errores[campo] && (
        <>
          <Icono nombre="alerta" />
          <span className="campo__mensaje-texto">{errores[campo]}</span>
        </>
      )}
    </p>
  );

  return (
    <form
      ref={formulario}
      className="agendamiento__principal"
      noValidate
      onSubmit={(evento) => {
        evento.preventDefault();
        enviar();
      }}
    >
      {aviso && <div className="agendamiento__alerta"><Alerta tipo="error">{aviso}</Alerta></div>}

      <section className="tarjeta" aria-labelledby="titulo-fecha">
        <div className="calendario-cita__encabezado">
          <div>
            <h2 className="agendamiento__titulo-bloque" id="titulo-fecha">¿Cuándo quieres la cita?</h2>
            <p className="agendamiento__ayuda">Elige un día con cupos y luego la hora que te sirva.</p>
          </div>
          <p className="calendario-cita__plazo">
            <Icono nombre="calendario" />
            Del {textoCorto(desde)} al {textoCorto(hasta)}
          </p>
        </div>

        <div className="zona-cliente">
          <div className="campo zona-cliente__campo">
            <label className="campo__etiqueta" htmlFor="cita-pais">País donde vives</label>
            <select className="campo__control select" id="cita-pais" value={pais} aria-describedby="cita-pais-ayuda"
              onChange={(e) => cambiarPais(e.target.value)}>
              {Object.keys(PAISES).map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
          {zonas.length > 1 && (
            <div className="campo zona-cliente__campo">
              <label className="campo__etiqueta" htmlFor="cita-zona">Zona horaria</label>
              <select className="campo__control select" id="cita-zona" value={zona} aria-describedby="cita-pais-ayuda"
                onChange={(e) => cambiarZona(e.target.value)}>
                {zonas.map((z) => <option key={z.id} value={z.id}>{z.nombre}</option>)}
              </select>
            </div>
          )}
          <p className="zona-cliente__ayuda" id="cita-pais-ayuda">
            <Icono nombre="informacion" />
            <span>
              {zonas.length > 1
                ? 'Elige la zona de tu ciudad: el calendario y las horas se muestran en tu hora local. '
                : 'Lo usamos para adaptar la cita a tu zona horaria: el calendario y las horas se muestran en tu hora local. '}
              Atendemos de lunes a viernes, de 8:00 a. m. a 12:00 m. y de 2:00 a 6:00 p. m. (hora de Colombia).
            </span>
          </p>
        </div>

        {errorCarga && (
          <div className="agendamiento__alerta aviso-sin-conexion">
            <Alerta tipo="error">{errorCarga}</Alerta>
            <Boton variante="contorno" tamano="pequeno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton>
          </div>
        )}

        <div className="calendario-cita">
          <div>
            <Calendario
              mes={mes}
              hoy={hoy}
              franjas={franjas}
              seleccionado={fecha}
              mesMinimo={mesMinimo}
              mesMaximo={mesMaximo}
              alCambiarMes={setMes}
              alElegir={(dia) => {
                cambiar({ fecha: dia, franja: null });
                setErrores((previo) => ({ ...previo, fecha: undefined }));
              }}
            />
            {mensajeGrupo('fecha', 'fecha-mensaje')}
          </div>

          <fieldset className="grupo-opciones campo horarios" data-grupo="hora">
            <legend className="solo-lector">Hora de la cita</legend>
            {!fecha && (
              <p className="franjas__vacio">
                <Icono nombre="reloj" grande />
                <span><strong>Elige un día</strong>Aquí verás sus horarios libres.</span>
              </p>
            )}
            {fecha && !franjas && !errorCarga && <Cargando texto="Cargando horarios…" />}
            {fecha && franjas && (
              <div className="franjas">
                <div className="franjas__cabecera">
                  <p className="franjas__dia">{textoDia(fecha)}</p>
                  <p className="franjas__conteo">{libres} de {totalDia} libres</p>
                </div>
                {PERIODOS.map(([titulo, desdeHora, hastaHora]) => {
                  const lista = visibles.filter((f) => partes(`${f.fecha}T${f.hora}`).hora >= desdeHora && partes(`${f.fecha}T${f.hora}`).hora < hastaHora);
                  if (lista.length === 0) return null;
                  return (
                    <div key={titulo}>
                      <p className="franjas__grupo-titulo">{titulo}</p>
                      <div className="franjas__lista">
                        {lista.map((f) => (
                          <FranjaHora
                            key={f.fechaHora}
                            franja={f}
                            otraZona={otraZona}
                            elegida={franja?.fechaHora === f.fechaHora}
                            alElegir={() => {
                              cambiar({ franja: f });
                              limpiar('hora');
                            }}
                          />
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
            {mensajeGrupo('hora', 'hora-mensaje')}
          </fieldset>
        </div>
      </section>

      <section className="tarjeta" aria-labelledby="titulo-modalidad">
        <fieldset className={['grupo-opciones', 'campo', errores.modalidad && 'campo--invalido'].filter(Boolean).join(' ')} data-grupo="modalidad">
          <legend><h2 className="agendamiento__titulo-bloque" id="titulo-modalidad">¿Cómo prefieres reunirte?</h2></legend>
          <p className="agendamiento__ayuda">Puedes venir a nuestra oficina o conectarte por videollamada.</p>
          <div className="modalidades">
            {(['PRESENCIAL', 'VIRTUAL'] as Modalidad[]).map((m) => (
              <label className="modalidad" key={m}>
                <input
                  className="control-oculto"
                  type="radio"
                  name="modalidad"
                  value={m}
                  checked={modalidad === m}
                  aria-describedby={errores.modalidad ? 'modalidad-mensaje' : undefined}
                  onChange={() => {
                    cambiar({ modalidad: m });
                    limpiar('modalidad');
                  }}
                />
                <span className="modalidad__icono">
                  {m === 'PRESENCIAL'
                    ? <svg className="icono icono--grande" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6M9 10h.01M15 10h.01" /></svg>
                    : <svg className="icono icono--grande" viewBox="0 0 24 24" aria-hidden="true"><rect x="2" y="6" width="14" height="12" rx="2" /><path d="m16 10 6-3v10l-6-3" /></svg>}
                </span>
                <span>
                  <span className="modalidad__nombre">{TEXTO_MODALIDAD[m]}</span>
                  <span className="modalidad__descripcion">
                    {m === 'PRESENCIAL' ? 'En nuestra oficina en Cúcuta.' : 'Por videollamada, desde donde estés.'}
                  </span>
                  <span className="modalidad__detalle">{DETALLE_MODALIDAD[m]}</span>
                </span>
                <span className="modalidad__marca" aria-hidden="true" />
              </label>
            ))}
          </div>
          {mensajeGrupo('modalidad', 'modalidad-mensaje')}
        </fieldset>
      </section>

      <section className="tarjeta" aria-labelledby="titulo-necesidad">
        <div className="necesidad">
          <h2 className="agendamiento__titulo-bloque" id="titulo-necesidad">Cuéntanos qué necesitas</h2>
          <p className="agendamiento__ayuda" id="cita-descripcion-ayuda">
            Qué quieres lograr, para quién es y si ya tienes algo (página, logo, catálogo). Así el equipo llega preparado.
          </p>
          <Campo
            etiqueta={<span className="solo-lector">Qué necesitas</span>}
            id="cita-descripcion"
            error={errores.descripcion}
            contador={{ actual: descripcion.length, maximo: DESCRIPCION_MAXIMA }}
          >
            <textarea
              className="campo__control--area"
              rows={4}
              maxLength={DESCRIPCION_MAXIMA}
              aria-describedby="cita-descripcion-ayuda"
              placeholder="Ejemplo: tengo una tienda de ropa en Cúcuta y quiero vender por internet, con pagos por PSE y tarjeta. Ya tengo logo y fotos de los productos."
              value={descripcion}
              onChange={(e) => {
                cambiar({ descripcion: e.target.value });
                if (errores.descripcion) limpiar('descripcion');
              }}
            />
          </Campo>
        </div>
      </section>

      <div className="acciones-paso">
        <Boton variante="contorno" icono="chevronIzquierda" onClick={alVolver}>Atrás</Boton>
        <div className="acciones-paso__derecha">
          <Boton type="submit" variante="acento" tamano="grande" iconoFinal="flechaDerecha">Continuar</Boton>
        </div>
      </div>
    </form>
  );
}

/** Una hora del día: libre (se elige), ocupada, "Ya tienes una cita" (RN-03) o fuera de plazo. */
function FranjaHora({ franja, otraZona, elegida, alElegir }: { franja: Franja; otraZona: boolean; elegida: boolean; alElegir: () => void }) {
  const hora = textoHora(franja.fechaHora);
  const colombia = textoHoraColombia(franja.fechaHora, franja.horaColombia);
  const completo = `${textoHoraColombia(franja.fechaHora, franja.horaColombia, true)} en Colombia`;

  if (franja.tuya) {
    return (
      <label className="franja franja--ocupada franja--tuya" title="Ya tienes una cita a esta hora">
        <input className="control-oculto" type="radio" name="hora" disabled />
        {hora}<span className="franja__estado">Ya tienes una cita</span>
      </label>
    );
  }
  if (franja.estado !== 'LIBRE') {
    return (
      <label className="franja franja--ocupada">
        <input className="control-oculto" type="radio" name="hora" disabled />
        {hora}
        {otraZona && <small className="franja__colombia" aria-hidden="true">{colombia} COL</small>}
        <span className="solo-lector">, {franja.estado === 'OCUPADO' ? 'ocupada' : 'no disponible'}</span>
      </label>
    );
  }
  return (
    <label className={['franja', otraZona && 'franja--doble'].filter(Boolean).join(' ')}>
      <input className="control-oculto" type="radio" name="hora" value={franja.fechaHora} checked={elegida} onChange={alElegir} />
      {hora}
      {otraZona && (
        <>
          <small className="franja__colombia" title={completo} aria-hidden="true">{colombia} COL</small>
          <span className="solo-lector">, {completo}</span>
        </>
      )}
    </label>
  );
}
