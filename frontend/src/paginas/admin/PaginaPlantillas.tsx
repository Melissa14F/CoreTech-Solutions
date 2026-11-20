import { useEffect, useRef, useState } from 'react';
import { useBlocker, useSearchParams } from 'react-router-dom';

import { ErrorApi } from '../../api/cliente';
import { srcImagen, catalogo, type ServicioGestion } from '../../catalogo/api';
import { Alerta } from '../../componentes/Alerta';
import { Boton, EnlaceBoton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { EditorPlan } from '../../componentes/EditorPlan';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { Icono } from '../../componentes/Icono';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { plantillas } from '../../proyectos/api';
import {
  SIN_ERRORES, aEditable, hayErrores, huella, validarPlan,
  type ErroresPlan, type FaseEditable,
} from '../../proyectos/plan';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/plan-trabajo.css';

const textoFases = (n: number | undefined) => (n === undefined ? '' : `${n} ${n === 1 ? 'fase' : 'fases'}`);
const DESCARTAR = (servicio: string) => `Tienes cambios sin guardar en «${servicio}». ¿Quieres descartarlos?`;

/** Plantillas de fases de cada servicio (HU-27, RN-27, RN-28; prototipo/admin/plantillas-fases.html). */
export function PaginaPlantillas() {
  useTituloDocumento('Plantillas de fases');
  const notificar = useNotificar();
  const [parametros, setParametros] = useSearchParams();
  const [servicios, setServicios] = useState<ServicioGestion[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  /** Cuántas fases tiene la plantilla guardada de cada servicio (la lista las muestra). */
  const [conteo, setConteo] = useState<Record<string, number>>({});

  const [guardada, setGuardada] = useState<FaseEditable[] | null>(null);
  const [fases, setFases] = useState<FaseEditable[]>([]);
  const [errorPlantilla, setErrorPlantilla] = useState<string | null>(null);
  const [verErrores, setVerErrores] = useState(false);
  const [erroresServidor, setErroresServidor] = useState<ErroresPlan | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [pedidoFoco, setPedidoFoco] = useState(0);
  const zona = useRef<HTMLDivElement>(null);

  useEffect(() => {
    catalogo.todos()
      .then((lista) => {
        setServicios(lista);
        setConteo(Object.fromEntries(lista.map((s) => [s.servicioId, s.fasesPlantilla])));
      })
      .catch((e) => setErrorCarga(separarErrores(e).general ?? 'No pudimos cargar los servicios.'));
  }, []);

  const actual = servicios?.find((s) => s.slug === parametros.get('servicio')) ?? servicios?.[0] ?? null;
  const actualId = actual?.servicioId ?? null;

  // Carga la plantilla del servicio elegido.
  useEffect(() => {
    if (!actualId) return;
    let vigente = true;
    setGuardada(null);
    setErrorPlantilla(null);
    setVerErrores(false);
    setErroresServidor(null);
    plantillas.obtener(actualId)
      .then(({ fases: recibidas }) => {
        if (!vigente) return;
        const editables = aEditable(recibidas);
        setGuardada(editables);
        setFases(editables);
        setConteo((c) => ({ ...c, [actualId]: recibidas.length }));
      })
      .catch((e) => vigente && setErrorPlantilla(separarErrores(e).general ?? 'No pudimos cargar la plantilla.'));
    return () => { vigente = false; };
  }, [actualId]);

  const hayCambios = guardada !== null && huella(fases) !== huella(guardada);

  // Salir de la página con cambios sin guardar: pregunta si quiere descartarlos.
  const bloqueo = useBlocker(({ currentLocation, nextLocation }) => hayCambios && currentLocation.pathname !== nextLocation.pathname);
  useEffect(() => {
    if (bloqueo.state !== 'blocked') return;
    if (window.confirm(DESCARTAR(actual?.nombre ?? 'esta plantilla'))) bloqueo.proceed();
    else bloqueo.reset();
  }, [bloqueo, actual?.nombre]);
  useEffect(() => {
    if (!hayCambios) return;
    const avisar = (evento: BeforeUnloadEvent) => { evento.preventDefault(); evento.returnValue = ''; };
    window.addEventListener('beforeunload', avisar);
    return () => window.removeEventListener('beforeunload', avisar);
  }, [hayCambios]);

  // Después de un intento de guardar con errores, el foco va al primer campo marcado.
  useEffect(() => {
    if (pedidoFoco > 0) zona.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [pedidoFoco]);

  const errores = erroresServidor ?? (verErrores ? validarPlan(fases) : SIN_ERRORES);

  function elegir(servicio: ServicioGestion) {
    if (servicio.servicioId === actualId) return;
    if (hayCambios && !window.confirm(DESCARTAR(actual?.nombre ?? 'esta plantilla'))) return;
    setParametros({ servicio: servicio.slug }, { replace: true });
  }

  function cambiar(nuevas: FaseEditable[]) {
    setFases(nuevas);
    setErroresServidor(null);
  }

  function deshacer() {
    if (!guardada) return;
    setFases(guardada);
    setVerErrores(false);
    setErroresServidor(null);
    notificar('Se restableció la última versión guardada.', 'info');
  }

  async function guardar() {
    if (!actual || guardando) return;
    const encontrados = validarPlan(fases);
    if (hayErrores(encontrados)) {
      setVerErrores(true);
      setPedidoFoco((n) => n + 1);
      notificar('Revisa la plantilla: hay fases o pasos incompletos.', 'error');
      return;
    }
    setGuardando(true);
    try {
      const { fases: recibidas } = await plantillas.guardar(actual.servicioId, fases);
      const editables = aEditable(recibidas);
      setGuardada(editables);
      setFases(editables);
      setVerErrores(false);
      setErroresServidor(null);
      setConteo((c) => ({ ...c, [actual.servicioId]: recibidas.length }));
      notificar(`Plantilla de «${actual.nombre}» guardada. Se usará en los proyectos nuevos.`);
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 422) {
        const { fases: generalFases, ...porCampo } = e.porCampo;
        setErroresServidor({ generales: [generalFases, ...e.generales].filter((m): m is string => Boolean(m)), porCampo });
        setPedidoFoco((n) => n + 1);
        notificar('Revisa la plantilla: hay fases o pasos incompletos.', 'error');
      } else {
        notificar(separarErrores(e).general ?? 'No se pudo guardar la plantilla.', 'error');
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <>
      <div className="encabezado-vista aparecer">
        <div>
          <h1 className="encabezado-vista__titulo">Plantillas de fases</h1>
          <p className="encabezado-vista__texto">
            Cada servicio tiene un plan de partida. Al crear un proyecto se copia la plantilla de su servicio como plan borrador,
            y el equipo la ajusta a lo que necesita ese cliente.
          </p>
        </div>
        <div className="encabezado-vista__acciones">
          <EnlaceBoton a="/admin/servicios" variante="contorno" icono="lista">Ver servicios</EnlaceBoton>
        </div>
      </div>

      {errorCarga && <Alerta tipo="error">{errorCarga}</Alerta>}
      {!servicios && !errorCarga && <Cargando texto="Cargando los servicios…" />}
      {servicios && servicios.length === 0 && (
        <EstadoVacio titulo="Todavía no hay servicios" texto="Crea un servicio para armar su plantilla de fases." />
      )}

      {servicios && servicios.length > 0 && (
        <div className="plantillas">
          <ul className="tarjeta lista-servicios-plantilla aparecer" aria-label="Servicios">
            {servicios.map((s) => (
              <li key={s.servicioId}>
                <button type="button" className="servicio-plantilla" aria-current={s.servicioId === actualId} onClick={() => elegir(s)}>
                  {s.imagenUrl
                    ? <img src={srcImagen(s.imagenUrl)} alt="" width={48} height={32} />
                    : <span className="servicio-plantilla__sin-imagen"><Icono nombre="plantilla" /></span>}
                  <span>
                    <strong>{s.nombre}</strong>
                    <small>{textoFases(conteo[s.servicioId])}</small>
                  </span>
                </button>
              </li>
            ))}
          </ul>

          <section className="tarjeta plan-seccion aparecer" style={{ ['--retraso' as string]: '60ms' }} aria-labelledby="titulo-plantilla">
            <div className="plan-seccion__cabecera">
              <div>
                <h2 className="plan-seccion__titulo" id="titulo-plantilla">{actual?.nombre}</h2>
                <p className="plan-seccion__texto">
                  Agrega, renombra, reordena o quita fases y pasos, y marca cuáles aprueba el cliente. Usa palabras sencillas:
                  el cliente verá estos nombres en su panel.
                </p>
              </div>
            </div>
            <div className="alerta alerta--info plantilla-aviso">
              <Icono nombre="informacion" />
              <p>Los cambios se aplican a los proyectos que se creen desde ahora. Los proyectos que ya existen conservan su propio plan.</p>
            </div>

            {errorPlantilla && <Alerta tipo="error">{errorPlantilla}</Alerta>}
            {!guardada && !errorPlantilla && <Cargando texto="Cargando la plantilla…" />}
            {guardada && (
              <div ref={zona}>
                <EditorPlan key={actualId} fases={fases} alCambiar={cambiar} errores={errores} prefijo="plantilla" />
                <div className="plantilla-pie">
                  <Boton variante="contorno" icono="deshacer" disabled={!hayCambios || guardando} onClick={deshacer}>Deshacer cambios</Boton>
                  <Boton variante="acento" icono="guardar" cargando={guardando} onClick={guardar}>Guardar plantilla</Boton>
                </div>
              </div>
            )}
          </section>
        </div>
      )}
    </>
  );
}
