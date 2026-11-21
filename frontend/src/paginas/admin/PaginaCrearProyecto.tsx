import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { VerCuestionario } from '../../agenda/VerCuestionario';
import { IDIOMAS, PRESUPUESTOS, URGENCIAS, gestionCitas, type Cuestionario } from '../../agenda/gestion';
import { iniciales } from '../../administracion/formato';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Icono } from '../../componentes/Icono';
import { Insignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { plantillas } from '../../proyectos/api';
import type { FaseApi } from '../../proyectos/plan';
import {
  fechaLegible, hoyColombia, proyectosAdmin, type CitaAtendida, type EquipoParaProyecto,
} from '../../proyectos/admin';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-crear-proyecto.css';

const sumarDias = (iso: string, dias: number) => new Date(Date.parse(iso) + dias * 86_400_000).toISOString().slice(0, 10);

const fechaDeCita = (iso: string) =>
  new Date(iso).toLocaleDateString('es-CO', { timeZone: 'America/Bogota', weekday: 'long', day: 'numeric', month: 'long' })
    .replace(/^./, (c) => c.toUpperCase());

/** Crear un proyecto desde una cita atendida (HU-02; prototipo/admin/crear-proyecto.html). */
export function PaginaCrearProyecto() {
  useTituloDocumento('Crear proyecto');
  const notificar = useNotificar();
  const navegar = useNavigate();
  const [parametros] = useSearchParams();
  const hoy = hoyColombia();

  const [citas, setCitas] = useState<CitaAtendida[] | null>(null);
  const [equipos, setEquipos] = useState<EquipoParaProyecto[]>([]);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [citaId, setCitaId] = useState('');
  const [nombre, setNombre] = useState('');
  const [alcance, setAlcance] = useState('');
  const [inicio, setInicio] = useState(hoy);
  const [entrega, setEntrega] = useState('');
  const [equipoId, setEquipoId] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [cuestionario, setCuestionario] = useState<Cuestionario | null>(null);
  const [plantilla, setPlantilla] = useState<FaseApi[] | null>(null);
  const [verCompleto, setVerCompleto] = useState(false);
  const { formulario, enfocar } = useEnfocarError();

  useEffect(() => {
    Promise.all([proyectosAdmin.citasAtendidas(), proyectosAdmin.equipos()])
      .then(([c, e]) => {
        setCitas(c);
        setEquipos(e);
        // Llegar desde Citas con ?cita=C-0057 deja la cita elegida
        const pedida = c.find((x) => x.codigo === parametros.get('cita') && !x.proyecto);
        if (pedida) {
          setCitaId(pedida.citaId);
          setEntrega(sumarDias(hoy, 28));
        }
      })
      .catch((e: unknown) => setErrorCarga(e instanceof ErrorApi ? e.message : 'No pudimos cargar las citas atendidas.'));
  }, [parametros, hoy]);

  const cita = useMemo(() => citas?.find((c) => c.citaId === citaId) ?? null, [citas, citaId]);
  const equipo = equipos.find((e) => e.equipoId === equipoId) ?? null;

  // Al elegir la cita: su cuestionario y la plantilla de fases de su servicio
  useEffect(() => {
    setCuestionario(null);
    setPlantilla(null);
    if (!cita) return;
    let vigente = true;
    gestionCitas.cuestionario(cita.citaId).then((q) => vigente && setCuestionario(q)).catch(() => undefined);
    plantillas.obtener(cita.servicioId).then((p) => vigente && setPlantilla(p.fases)).catch(() => vigente && setPlantilla([]));
    return () => {
      vigente = false;
    };
  }, [cita]);

  // Al elegir una cita se propone la entrega 4 semanas después del inicio, para no dejar la fecha vacía
  const elegirCita = (id: string) => {
    setCitaId(id);
    setEntrega(id ? sumarDias(inicio || hoy, 28) : '');
  };

  const plantillaInvalida = plantilla !== null && (plantilla.length < 2 || !plantilla.some((f) => f.apruebaCliente));

  async function crear(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const n = nombre.trim();
    const a = alcance.trim();
    const nuevos: Record<string, string> = {};
    if (!citaId) nuevos.citaId = 'Elige la cita de la que nace el proyecto.';
    if (!n) nuevos.nombre = 'Escribe el nombre que verá el cliente.';
    else if (n.length < 5) nuevos.nombre = 'Escribe al menos 5 caracteres.';
    if (!a) nuevos.alcance = 'Describe lo que se acordó en la cita.';
    else if (a.length < 20) nuevos.alcance = 'Escribe al menos 20 caracteres.';
    if (!inicio) nuevos.fechaInicio = 'Elige la fecha de inicio.';
    else if (inicio < hoy) nuevos.fechaInicio = 'El inicio no puede ser una fecha pasada.';
    if (!entrega) nuevos.fechaEstimadaEntrega = 'Elige la fecha estimada de entrega.';
    else if (inicio && entrega < sumarDias(inicio, 14)) nuevos.fechaEstimadaEntrega = 'Debe ser al menos 2 semanas después del inicio.';
    if (!equipoId) nuevos.equipoId = 'Elige el equipo responsable.';
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) {
      enfocar();
      return;
    }
    setGuardando(true);
    try {
      const creado = await proyectosAdmin.crear({ citaId, nombre: n, alcance: a, fechaInicio: inicio, fechaEstimadaEntrega: entrega, equipoId });
      notificar(`Proyecto ${creado.codigo} creado con su plan en borrador. Avisamos al Product Owner para que lo ajuste y lo envíe al cliente.`);
      navegar(`/admin/proyectos?buscar=${creado.codigo}`);
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setErrores(porCampo);
      setGeneral(mensaje);
      setGuardando(false);
      enfocar();
    }
  }

  const sinProyecto = citas?.filter((c) => !c.proyecto) ?? [];
  const conProyecto = citas?.filter((c) => c.proyecto) ?? [];
  const urgencia = cuestionario ? URGENCIAS.find(([v]) => v === cuestionario.urgencia) : undefined;

  return (
    <>
      <Link className="volver" to="/admin/proyectos"><Icono nombre="chevronIzquierda" />Volver a proyectos</Link>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Crear proyecto</h1>
          <p className="encabezado-vista__texto">
            Un proyecto nace de una cita atendida. Elige la cita, el equipo responsable y la fecha estimada de entrega. Cada equipo se identifica por su Product Owner.
          </p>
        </div>
      </div>

      {errorCarga ? (
        <Alerta tipo="error" titulo="No pudimos cargar los datos">{errorCarga}</Alerta>
      ) : !citas ? (
        <Cargando texto="Cargando las citas atendidas…" />
      ) : (
        <form className="crear-proyecto" ref={formulario} onSubmit={(e) => void crear(e)} noValidate>
          <div className="crear-proyecto__principal">
            <section className="tarjeta seccion-formulario" aria-labelledby="titulo-origen">
              <h2 className="seccion-formulario__titulo" id="titulo-origen"><span className="seccion-formulario__numero" aria-hidden="true">1</span>Cita de origen</h2>
              <Campo etiqueta="Cita atendida" error={errores.citaId} ayuda="Solo aparecen las citas atendidas. Cada cita puede generar un único proyecto.">
                <select className="select" name="citaId" value={citaId} onChange={(e) => elegirCita(e.target.value)}>
                  <option value="">Elige una cita</option>
                  {sinProyecto.length > 0 && (
                    <optgroup label="Atendidas sin proyecto">
                      {sinProyecto.map((c) => <option key={c.citaId} value={c.citaId}>{`${c.codigo} · ${c.cliente} · ${c.servicio}`}</option>)}
                    </optgroup>
                  )}
                  {conProyecto.length > 0 && (
                    <optgroup label="Ya generaron un proyecto (RN-04)">
                      {conProyecto.map((c) => <option key={c.citaId} value={c.citaId} disabled>{`${c.codigo} · ${c.cliente} · ya es ${c.proyecto}`}</option>)}
                    </optgroup>
                  )}
                </select>
              </Campo>
              {cita && (
                <dl className="datos-cita">
                  <div><dt>Cliente</dt><dd>{cita.cliente}</dd></div>
                  <div><dt>Servicio</dt><dd>{cita.servicio}</dd></div>
                  <div><dt>Fecha de la cita</dt><dd>{fechaDeCita(cita.fechaHora)}</dd></div>
                  <div><dt>La atendió</dt><dd>{cita.productOwner ?? 'Sin equipo asignado'}</dd></div>
                </dl>
              )}
              {cita && cuestionario && (
                <div className="cuestionario-cita">
                  <div className="cuestionario-cita__cabecera">
                    <p className="cuestionario-cita__titulo">Cuestionario del Product Owner</p>
                    <Boton variante="contorno" tamano="pequeno" onClick={() => setVerCompleto(true)}>Ver completo</Boton>
                  </div>
                  <p className="cuestionario-cita__resumen">{cuestionario.resumen}</p>
                  <dl className="cuestionario-cita__datos">
                    <div><dt>Urgencia</dt><dd><Insignia variante={`urgencia-${cuestionario.urgencia.toLowerCase()}` as 'urgencia-alta'}>{urgencia?.[1] ?? cuestionario.urgencia}</Insignia></dd></div>
                    <div><dt>Presupuesto</dt><dd>{PRESUPUESTOS.find(([v]) => v === cuestionario.presupuesto)?.[1] ?? cuestionario.presupuesto}</dd></div>
                    <div><dt>Entrega deseada</dt><dd>{cuestionario.fechaDeseada || 'Sin fecha definida'}</dd></div>
                    <div><dt>Idioma</dt><dd>{IDIOMAS.find(([v]) => v === cuestionario.idioma)?.[1] ?? cuestionario.idioma}</dd></div>
                  </dl>
                </div>
              )}
            </section>

            <section className="tarjeta seccion-formulario" aria-labelledby="titulo-datos">
              <h2 className="seccion-formulario__titulo" id="titulo-datos"><span className="seccion-formulario__numero" aria-hidden="true">2</span>Datos del proyecto</h2>
              <Campo etiqueta="Nombre del proyecto" error={errores.nombre} ayuda="Así lo verán el cliente y el desarrollador en su panel.">
                <input type="text" name="nombre" maxLength={60} value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ejemplo: Web corporativa Duarte Asesores" />
              </Campo>
              <Campo etiqueta="Alcance acordado" error={errores.alcance} contador={{ actual: alcance.length, maximo: 600 }}>
                <textarea className="campo__control--area" name="alcance" maxLength={600} value={alcance} onChange={(e) => setAlcance(e.target.value)}
                          placeholder="Ejemplo: sitio de 5 páginas (inicio, servicios, equipo, blog y contacto) con formulario y botón de WhatsApp." />
              </Campo>
              <div className="fechas-proyecto">
                <Campo etiqueta="Fecha de inicio" error={errores.fechaInicio}>
                  <input type="date" name="fechaInicio" min={hoy} value={inicio} onChange={(e) => setInicio(e.target.value)} />
                </Campo>
                <Campo etiqueta="Entrega estimada inicial" error={errores.fechaEstimadaEntrega} ayuda="Mínimo 2 semanas después del inicio. Se confirma en la cotización del plan.">
                  <input type="date" name="fechaEstimadaEntrega" min={inicio ? sumarDias(inicio, 14) : hoy} value={entrega} onChange={(e) => setEntrega(e.target.value)} />
                </Campo>
              </div>
              <div className="plantilla-proyecto">
                <div className="plantilla-proyecto__cabecera">
                  <p className="plantilla-proyecto__titulo"><Icono nombre="lista" /><span>{cita ? `Plan de trabajo inicial · ${cita.servicio}` : 'Plan de trabajo inicial'}</span></p>
                  <Link className="enlace" to="/admin/plantillas">Ver plantillas</Link>
                </div>
                {!cita && <p className="plantilla-proyecto__texto">Elige la cita para ver la plantilla de fases de su servicio.</p>}
                {cita && plantilla === null && <p className="plantilla-proyecto__texto">Cargando la plantilla…</p>}
                {cita && plantillaInvalida && (
                  <Alerta tipo="error" titulo="Completa la plantilla de fases de este servicio antes de crear el proyecto" />
                )}
                {cita && plantilla && !plantillaInvalida && (
                  <>
                    <p className="plantilla-proyecto__texto">{`Se copiarán ${plantilla.length} fases como plan en borrador; el equipo las ajusta antes de enviarlas al cliente.`}</p>
                    <ol className="plantilla-proyecto__fases">
                      {plantilla.map((f) => <li key={f.orden}>{f.nombre}{f.apruebaCliente ? ' · la aprueba el cliente' : ''}</li>)}
                    </ol>
                  </>
                )}
              </div>
            </section>

            <section className="tarjeta seccion-formulario" aria-labelledby="titulo-responsable">
              <h2 className="seccion-formulario__titulo" id="titulo-responsable"><span className="seccion-formulario__numero" aria-hidden="true">3</span>Equipo responsable</h2>
              <fieldset className={['grupo-opciones campo', errores.equipoId && 'campo--invalido'].filter(Boolean).join(' ')}>
                <legend className="solo-lector">Equipo responsable</legend>
                <p className="campo__ayuda campo__ayuda--arriba">Cada equipo puede tener máximo 3 proyectos activos (RN-05). Solo su Product Owner registra el avance.</p>
                <div className="opciones-persona">
                  {equipos.map((e) => {
                    const sinCupo = e.cupoLibre <= 0;
                    return (
                      <label key={e.equipoId} className={['opcion-persona', sinCupo && 'opcion-persona--sin-cupo'].filter(Boolean).join(' ')}>
                        <input type="radio" name="equipoId" value={e.equipoId} checked={equipoId === e.equipoId} disabled={sinCupo} onChange={() => setEquipoId(e.equipoId)} />
                        <span className="avatar" aria-hidden="true">{iniciales(e.productOwner)}</span>
                        <span className="opcion-persona__nombre">
                          {`Equipo de ${e.productOwner}`}
                          <span className="opcion-persona__detalle">
                            {sinCupo
                              ? `Product Owner: ${e.productOwner} · Sin cupo: ya tiene ${e.maxProyectos} proyectos activos`
                              : `Product Owner: ${e.productOwner} · ${e.proyectosActivos} de ${e.maxProyectos} proyectos activos · ${e.cupoLibre} ${e.cupoLibre === 1 ? 'cupo libre' : 'cupos libres'}`}
                          </span>
                        </span>
                        <span className="carga" role="img" aria-label={`${e.proyectosActivos} de ${e.maxProyectos} proyectos activos`}>
                          {Array.from({ length: e.maxProyectos }, (_, i) => (
                            <span key={i} className={['carga__unidad', i < e.proyectosActivos && 'carga__unidad--ocupada'].filter(Boolean).join(' ')} />
                          ))}
                        </span>
                      </label>
                    );
                  })}
                </div>
                {errores.equipoId && <p className="campo__mensaje" role="alert"><Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.equipoId}</span></p>}
              </fieldset>
            </section>
          </div>

          <aside className="crear-proyecto__resumen" aria-labelledby="titulo-resumen">
            <div className="tarjeta resumen-proyecto">
              <h2 className="tarjeta__titulo" id="titulo-resumen">Así se creará</h2>
              <p className="resumen-proyecto__nombre">{nombre.trim() || 'Proyecto sin nombre'}</p>
              <dl className="lista-datos">
                <div className="lista-datos__fila"><dt className="lista-datos__dato">Código</dt><dd className="lista-datos__valor">Se asigna al crearlo</dd></div>
                <div className="lista-datos__fila"><dt className="lista-datos__dato">Cliente</dt><dd className="lista-datos__valor">{cita?.cliente ?? '—'}</dd></div>
                <div className="lista-datos__fila"><dt className="lista-datos__dato">Servicio</dt><dd className="lista-datos__valor">{cita?.servicio ?? '—'}</dd></div>
                <div className="lista-datos__fila"><dt className="lista-datos__dato">Equipo (Product Owner)</dt><dd className="lista-datos__valor">{equipo?.productOwner ?? '—'}</dd></div>
                <div className="lista-datos__fila"><dt className="lista-datos__dato">Entrega estimada</dt><dd className="lista-datos__valor">{entrega ? fechaLegible(entrega) : '—'}</dd></div>
              </dl>
              <p className="resumen-proyecto__fase"><Icono nombre="candado" /><span>Empieza con un <strong>plan en borrador</strong> copiado de la plantilla. El equipo lo ajusta y lo envía al cliente con la cotización (RN-06).</span></p>
              <p className="resumen-proyecto__aviso"><Icono nombre="correo" /><span>El Product Owner recibe un correo para preparar el plan. El cliente lo verá cuando se lo envíen.</span></p>
              {general && <Alerta tipo="error" titulo={general} />}
              <Boton type="submit" bloque cargando={guardando} icono="exito" disabled={plantillaInvalida}>Crear proyecto</Boton>
              <Link className="boton boton--fantasma boton--bloque" to="/admin/citas">Cancelar</Link>
            </div>
          </aside>
        </form>
      )}

      <VerCuestionario
        cita={verCompleto && cita ? { citaId: cita.citaId, codigo: cita.codigo, servicio: cita.servicio, cliente: { nombre: cita.cliente, email: cita.correoCliente } } : null}
        alCerrar={() => setVerCompleto(false)}
        texto="Lo que registró el Product Owner después de la reunión."
      />
    </>
  );
}
