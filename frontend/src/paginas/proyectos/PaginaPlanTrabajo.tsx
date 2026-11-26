import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useBlocker, useLocation, useNavigate, useParams } from 'react-router-dom';

import { diaColombia } from '../../administracion/formato';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton, EnlaceBoton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { CampoValor } from '../../componentes/CampoValor';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { EditorPlan } from '../../componentes/EditorPlan';
import { FasesPropuesta } from '../../componentes/FasesPropuesta';
import { Icono } from '../../componentes/Icono';
import { Insignia, type VarianteInsignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { fechaLegible, fechaSinAnio, pesos } from '../../proyectos/admin';
import { planes } from '../../proyectos/api';
import {
  SIN_ERRORES, aEditable, cotizacionDe, fechaConDia, hayErrores, huellaPlan, nombrePila, porcentajeFase, sumarDiasIso, ultimaFechaDeEntrega,
  validarCotizacion, validarPlan,
  type CotizacionEditable, type ErroresPlan, type EstadoPlanProyecto, type FaseEditable, type PlanProyecto,
} from '../../proyectos/plan';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/plan-trabajo.css';

const INSIGNIA: Record<EstadoPlanProyecto, { variante: VarianteInsignia; texto: string }> = {
  BORRADOR: { variante: 'borrador', texto: 'Borrador' },
  AJUSTES_PEDIDOS: { variante: 'borrador', texto: 'Ajustes pedidos' },
  ENVIADO: { variante: 'plan', texto: 'Enviado' },
  APROBADO: { variante: 'activo', texto: 'Aprobado' },
  VENCIDO: { variante: 'vencido', texto: 'Vencido' },
  RECHAZADO: { variante: 'cancelada', texto: 'Rechazado' },
};

const DESCARTAR = 'Tienes cambios sin guardar en el plan. ¿Quieres descartarlos?';
const diaDe = (instante: string) => diaColombia(new Date(instante));

/**
 * Plan de trabajo y cotización de un proyecto, para el Product Owner del equipo y el administrador (HU-28, RN-27 a RN-29;
 * prototipo/admin/plan-de-trabajo-web-duarte.html y prototipo/desarrollador/plan-de-trabajo-inventario-salon.html).
 * En borrador o con ajustes pedidos se edita y se envía al cliente; después queda en lectura.
 */
export function PaginaPlanTrabajo() {
  const { codigo = '' } = useParams();
  const esAdmin = useLocation().pathname.startsWith('/admin');
  const navegar = useNavigate();
  const notificar = useNotificar();

  const [plan, setPlan] = useState<PlanProyecto | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [fases, setFases] = useState<FaseEditable[]>([]);
  const [cotizacion, setCotizacion] = useState<CotizacionEditable>({ valor: '', entrega: '', condiciones: '' });
  const [guardada, setGuardada] = useState('');
  const [verErrores, setVerErrores] = useState(false);
  const [erroresServidor, setErroresServidor] = useState<{ fases: ErroresPlan; cotizacion: Record<string, string> } | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [recordando, setRecordando] = useState(false);
  const [dialogo, setDialogo] = useState(false);
  const [pedidoFoco, setPedidoFoco] = useState(0);
  const zona = useRef<HTMLDivElement>(null);
  const saliendo = useRef(false);

  useTituloDocumento(plan ? `Plan de trabajo · ${plan.nombre}` : 'Plan de trabajo');

  function cargar(recibido: PlanProyecto) {
    const editables = aEditable(recibido.fases);
    const datos = cotizacionDe(recibido);
    setPlan(recibido);
    setFases(editables);
    setCotizacion(datos);
    setGuardada(huellaPlan(editables, datos));
    setVerErrores(false);
    setErroresServidor(null);
  }

  useEffect(() => {
    let vigente = true;
    setPlan(null);
    setErrorCarga(null);
    planes.obtener(codigo)
      .then((recibido) => vigente && cargar(recibido))
      .catch((e) => vigente && setErrorCarga(separarErrores(e).general ?? 'No pudimos cargar el plan.'));
    return () => { vigente = false; };
  }, [codigo]);

  const editable = plan?.editable ?? false;
  const hayCambios = editable && guardada !== '' && huellaPlan(fases, cotizacion) !== guardada;

  // Salir con cambios sin guardar: pregunta si quiere descartarlos (igual que la plantilla).
  const bloqueo = useBlocker(({ currentLocation, nextLocation }) =>
    hayCambios && !saliendo.current && currentLocation.pathname !== nextLocation.pathname);
  useEffect(() => {
    if (bloqueo.state !== 'blocked') return;
    if (window.confirm(DESCARTAR)) bloqueo.proceed();
    else bloqueo.reset();
  }, [bloqueo]);
  useEffect(() => {
    if (!hayCambios) return;
    const avisar = (evento: BeforeUnloadEvent) => { evento.preventDefault(); evento.returnValue = ''; };
    window.addEventListener('beforeunload', avisar);
    return () => window.removeEventListener('beforeunload', avisar);
  }, [hayCambios]);

  useEffect(() => {
    if (pedidoFoco > 0) zona.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [pedidoFoco]);

  if (errorCarga) {
    return (
      <>
        <Alerta tipo="error">{errorCarga}</Alerta>
        <EnlaceBoton a={esAdmin ? '/admin/proyectos' : '/equipo/proyectos'} variante="contorno" icono="lista">Volver a los proyectos</EnlaceBoton>
      </>
    );
  }
  if (!plan) return <Cargando texto="Cargando el plan de trabajo…" />;

  const pila = nombrePila(plan.cliente);
  const encabezado = (
    <div className="encabezado-vista aparecer">
      <div>
        <h1 className="encabezado-vista__titulo">
          Plan de trabajo <Insignia variante={INSIGNIA[plan.estadoPlan].variante}>{INSIGNIA[plan.estadoPlan].texto}</Insignia>
        </h1>
        <p className="encabezado-vista__texto">
          <strong>{plan.nombre}</strong> · {plan.codigo} · {plan.cliente} · {plan.servicio} · Equipo de {plan.productOwner}
        </p>
      </div>
      <div className="encabezado-vista__acciones">
        {editable && <Boton variante="acento" icono="enviar" onClick={revisarYAbrir}>Enviar a {pila}</Boton>}
        {plan.estadoPlan === 'ENVIADO' && (
          <Boton variante="contorno" icono="enviar" cargando={recordando} onClick={() => void recordar()}>Recordar a {pila}</Boton>
        )}
      </div>
    </div>
  );

  const erroresFases: ErroresPlan = erroresServidor?.fases ?? (verErrores ? validarPlan(fases, pila, 'el plan') : SIN_ERRORES);
  const erroresCotizacion: Record<string, string> = erroresServidor?.cotizacion
    ?? (verErrores ? validarCotizacion(cotizacion, plan.fechaInicio, fechaLegible) : {});

  function cambiarFases(nuevas: FaseEditable[]) {
    setFases(nuevas);
    setErroresServidor(null);
  }

  function cambiarCotizacion(cambios: Partial<CotizacionEditable>) {
    setCotizacion((c) => ({ ...c, ...cambios }));
    setErroresServidor(null);
  }

  function ponerErroresDelServidor(e: unknown) {
    if (e instanceof ErrorApi && e.estado === 422) {
      const { fases: generalFases, valorCotizado, condicionesCotizacion, fechaEstimadaEntrega, cotizacion: general, ...porCampo } = e.porCampo;
      setErroresServidor({
        fases: { generales: [generalFases, ...e.generales].filter((m): m is string => Boolean(m)), porCampo },
        cotizacion: Object.fromEntries(Object.entries({ valorCotizado, condicionesCotizacion, fechaEstimadaEntrega, general })
          .filter((par): par is [string, string] => Boolean(par[1]))),
      });
      setPedidoFoco((n) => n + 1);
      notificar('Revisa el plan: hay datos incompletos.', 'error');
    } else {
      notificar(separarErrores(e).general ?? 'No se pudo completar la acción.', 'error');
    }
  }

  /** Valida todo en el navegador (los mismos mensajes que el servidor) y abre el resumen de envío. */
  function revisarYAbrir() {
    const fallasFases = validarPlan(fases, pila, 'el plan');
    const fallasCotizacion = validarCotizacion(cotizacion, plan!.fechaInicio, fechaLegible);
    setVerErrores(true);
    setErroresServidor(null);
    if (hayErrores(fallasFases)) {
      notificar('Revisa las fases: hay datos incompletos.', 'error');
      setPedidoFoco((n) => n + 1);
      return;
    }
    if (Object.keys(fallasCotizacion).length > 0) {
      notificar('Revisa los campos marcados en rojo.', 'error');
      setPedidoFoco((n) => n + 1);
      return;
    }
    setDialogo(true);
  }

  async function guardarBorrador() {
    if (guardando || !plan) return;
    setGuardando(true);
    try {
      cargar(await planes.guardar(plan.codigo, fases, cotizacion));
      notificar('Borrador guardado.');
    } catch (e) {
      ponerErroresDelServidor(e);
    } finally {
      setGuardando(false);
    }
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (enviando || !plan) return;
    setEnviando(true);
    try {
      const enviado = await planes.enviar(plan.codigo, fases, cotizacion);
      notificar(`Plan enviado a ${pila}. Tiene hasta el ${fechaConDia(enviado.plazoRespuesta)} para aprobarlo o pedir ajustes.`);
      saliendo.current = true;
      navegar(esAdmin ? `/admin/proyectos?buscar=${plan.codigo}` : '/equipo/proyectos');
    } catch (e) {
      setDialogo(false);
      ponerErroresDelServidor(e);
    } finally {
      setEnviando(false);
    }
  }

  async function recordar() {
    if (recordando || !plan) return;
    setRecordando(true);
    try {
      await planes.recordar(plan.codigo);
      notificar(`Le recordamos a ${pila} por correo que tiene el plan por responder.`);
    } catch (e) {
      notificar(separarErrores(e).general ?? 'No se pudo enviar el recordatorio.', 'error');
    } finally {
      setRecordando(false);
    }
  }

  const aprueban = fases.filter((f) => f.apruebaCliente).length;
  const estado = plan.estadoPlan;
  const respondeHasta = plan.plazoRespuesta ? fechaConDia(plan.plazoRespuesta) : '';
  const venceElDia = plan.venceEl ? fechaConDia(plan.venceEl) : '';

  const pasos = estado === 'BORRADOR' || estado === 'AJUSTES_PEDIDOS' || estado === 'ENVIADO' || estado === 'APROBADO' ? (
    <ol className="estado-plan aparecer" aria-label="Estado del plan">
      <li className={`estado-plan__paso ${estado === 'BORRADOR' || estado === 'AJUSTES_PEDIDOS' ? 'estado-plan__paso--actual' : 'estado-plan__paso--hecho'}`}
          aria-current={estado === 'BORRADOR' || estado === 'AJUSTES_PEDIDOS' ? 'step' : undefined}>
        <span><strong>Borrador</strong>Ajusta fases, pasos y cotización</span>
      </li>
      <li className={`estado-plan__paso ${estado === 'ENVIADO' ? 'estado-plan__paso--actual' : estado === 'APROBADO' ? 'estado-plan__paso--hecho' : ''}`}
          aria-current={estado === 'ENVIADO' ? 'step' : undefined}>
        <span><strong>Enviado a {pila}</strong>3 días hábiles para responder</span>
      </li>
      <li className={`estado-plan__paso ${estado === 'APROBADO' ? 'estado-plan__paso--actual' : ''}`}
          aria-current={estado === 'APROBADO' ? 'step' : undefined}>
        <span><strong>Aprobado</strong>El plan queda fijo y empieza la fase 1</span>
      </li>
    </ol>
  ) : null;

  // ------------------------------------------------------------------------------------------ Borrador o ajustes pedidos
  if (editable) {
    return (
      <>
        {encabezado}
        {pasos}
        {estado === 'AJUSTES_PEDIDOS' && plan.ajustesPedidos && (
          <Alerta tipo="advertencia" titulo={`${pila} pidió ajustes al plan`}>{plan.ajustesPedidos.texto}</Alerta>
        )}
        <div className="plan" ref={zona}>
          <div className="plan__principal">
            <section className="tarjeta plan-seccion aparecer" style={{ ['--retraso' as string]: '60ms' }} aria-labelledby="titulo-fases">
              <div className="plan-seccion__cabecera">
                <div>
                  <h2 className="plan-seccion__titulo" id="titulo-fases">Fases y pasos</h2>
                  <p className="plan-seccion__texto">
                    Se copió de la plantilla «{plan.servicio}» el {fechaLegible(diaDe(plan.creadoAt))}. Ajústalo a lo que se habló con {pila}
                    {plan.citaCodigo ? ` en la cita ${plan.citaCodigo}` : ''}.
                  </p>
                </div>
              </div>
              <EditorPlan fases={fases} alCambiar={cambiarFases} errores={erroresFases} cliente={pila} prefijo="plan" />
            </section>
          </div>

          <aside className="plan__lateral">
            <section className="tarjeta cotizacion aparecer" style={{ ['--retraso' as string]: '90ms' }} aria-labelledby="titulo-cotizacion">
              <h2 className="cotizacion__titulo" id="titulo-cotizacion">Cotización</h2>
              {erroresCotizacion.general && <Alerta tipo="error">{erroresCotizacion.general}</Alerta>}
              <CampoValor
                etiqueta="Valor del proyecto" valor={cotizacion.valor} alCambiar={(valor) => cambiarCotizacion({ valor })}
                ayuda="En pesos colombianos (COP), sin centavos. Entre $1.000.000 y $200.000.000."
                error={erroresCotizacion.valorCotizado}
              />
              <Campo
                etiqueta="Fecha estimada de entrega" ayuda={`El proyecto empieza el ${fechaLegible(plan.fechaInicio)}.`}
                error={erroresCotizacion.fechaEstimadaEntrega}
              >
                <input
                  type="date" min={sumarDiasIso(plan.fechaInicio, 14)} max={ultimaFechaDeEntrega()} value={cotizacion.entrega}
                  onChange={(e) => cambiarCotizacion({ entrega: e.target.value })}
                />
              </Campo>
              <Campo etiqueta="Qué incluye y qué no" error={erroresCotizacion.condicionesCotizacion}>
                <textarea
                  className="campo__control--area" rows={5} maxLength={600} value={cotizacion.condiciones}
                  onChange={(e) => cambiarCotizacion({ condiciones: e.target.value })}
                />
              </Campo>
              <dl className="cotizacion__resumen">
                <div><dt>Fases</dt><dd>{fases.length}</dd></div>
                <div><dt>Cada fase vale</dt><dd>{fases.length > 0 ? porcentajeFase(fases.length) : '—'}</dd></div>
                <div><dt>Las aprueba {pila}</dt><dd>{aprueban}</dd></div>
                <div><dt>Plazo para responder</dt><dd>3 días hábiles</dd></div>
                <div><dt>La propuesta vence</dt><dd>A los 10 días hábiles</dd></div>
                <div><dt>Garantía</dt><dd>30 días después de la entrega</dd></div>
              </dl>
              <Boton variante="contorno" bloque icono="guardar" cargando={guardando} disabled={!hayCambios} onClick={() => void guardarBorrador()}>
                Guardar borrador
              </Boton>
              <Boton variante="acento" bloque icono="enviar" onClick={revisarYAbrir}>Enviar a {pila}</Boton>
              <p className="cotizacion__nota">
                Los pagos no se gestionan en el portal: la cotización queda registrada para que {pila} la apruebe junto con el plan.
              </p>
            </section>
          </aside>
        </div>

        <Dialogo
          abierto={dialogo} alCerrar={() => !enviando && setDialogo(false)} icono="enviar" titulo={`Enviar el plan a ${pila}`}
          texto={`Le llegará un correo y lo verá en su panel. Tiene 3 días hábiles para aprobarlo o pedir ajustes.`}
          acciones={(
            <>
              <Boton variante="contorno" disabled={enviando} onClick={() => setDialogo(false)}>Volver</Boton>
              <Boton type="submit" form="formulario-enviar-plan" variante="acento" icono="enviar" cargando={enviando}>Enviar plan</Boton>
            </>
          )}
        >
          <form id="formulario-enviar-plan" onSubmit={(e) => void enviar(e)}>
            <dl className="cotizacion__resumen">
              <div><dt>Fases</dt><dd>{fases.length} · {porcentajeFase(fases.length)} cada una</dd></div>
              <div><dt>Las aprueba {pila}</dt><dd>{aprueban}</dd></div>
              <div><dt>Valor</dt><dd>{cotizacion.valor ? pesos(Number(cotizacion.valor)) : '—'}</dd></div>
              <div><dt>Entrega estimada</dt><dd>{cotizacion.entrega ? fechaLegible(cotizacion.entrega) : '—'}</dd></div>
            </dl>
            <p className="dialogo__texto">
              Si {pila} no responde, le recordamos. A los 10 días hábiles sin respuesta la propuesta vence: el proyecto pasa a Pausado y el equipo libera su cupo.
            </p>
            <p className="dialogo__texto">
              Cuando {pila} lo apruebe, el plan queda fijo y el proyecto empieza por la fase 1. Después, para agregar o quitar una fase o
              cambiar la fecha de entrega hará falta la autorización del administrador y la aceptación de {pila}.
            </p>
          </form>
        </Dialogo>
      </>
    );
  }

  // ------------------------------------------------------------------------------------------ Lectura (enviado, aprobado, rechazado, vencido)
  return (
    <>
      {encabezado}
      {pasos}
      {estado === 'ENVIADO' && (
        <div className="alerta alerta--info plan-aviso aparecer">
          <Icono nombre="informacion" />
          <p>
            Enviaste el plan y la cotización a {pila}{plan.enviadoAt ? ` el ${fechaSinAnio(diaDe(plan.enviadoAt))}` : ''}.
            Tiene hasta el <strong>{respondeHasta}</strong> para aprobarlos o pedir ajustes.
            Si no responde antes del <strong>{venceElDia}</strong>, la propuesta vence, el proyecto pasa a Pausado y tu equipo libera el cupo.
            Mientras {pila} responde, el plan no se edita; si pide ajustes, vuelve a ti con sus comentarios para que lo ajustes y lo reenvíes.
          </p>
        </div>
      )}
      {estado === 'APROBADO' && (
        <Alerta tipo="exito" titulo={`${pila} aprobó el plan y la cotización${plan.aprobadoAt ? ` el ${fechaSinAnio(diaDe(plan.aprobadoAt))}` : ''}`}>
          El plan quedó fijo. Para agregar o quitar una fase o cambiar la fecha de entrega hace falta la autorización del administrador
          y la aceptación de {pila}.
        </Alerta>
      )}
      {estado === 'RECHAZADO' && (
        <Alerta tipo="advertencia" titulo={`${pila} rechazó la propuesta`}>
          {plan.rechazo ? `«${plan.rechazo.texto}» ` : ''}El proyecto está en pausa; el administrador lo reanuda para preparar un plan nuevo.
        </Alerta>
      )}
      {estado === 'VENCIDO' && (
        <Alerta tipo="advertencia" titulo="La propuesta venció sin respuesta">
          El proyecto está en pausa y el equipo liberó su cupo. El administrador lo reanuda para preparar un plan nuevo.
        </Alerta>
      )}

      <div className="plan">
        <div className="plan__principal">
          <section className="tarjeta plan-seccion aparecer" style={{ ['--retraso' as string]: '60ms' }} aria-labelledby="titulo-fases">
            <div className="plan-seccion__cabecera">
              <div>
                <h2 className="plan-seccion__titulo" id="titulo-fases">Fases y pasos que recibió {pila}</h2>
                <p className="plan-seccion__texto">
                  {plan.fases.length} fases; cada una vale {porcentajeFase(plan.fases.length)}.
                  {' '}{pila} aprueba {plan.fases.filter((f) => f.apruebaCliente).length} y el equipo cierra las demás.
                </p>
              </div>
            </div>
            <FasesPropuesta fases={plan.fases} vista={{ tipo: 'equipo', cliente: pila }} />
          </section>
        </div>

        <aside className="plan__lateral">
          <section className="tarjeta propuesta-valor aparecer" style={{ ['--retraso' as string]: '90ms' }} aria-labelledby="titulo-cotizacion-enviada">
            <h2 className="propuesta-valor__etiqueta" id="titulo-cotizacion-enviada">Cotización enviada</h2>
            <p className="propuesta-valor__cifra">{plan.valorCotizado === null ? '—' : pesos(plan.valorCotizado).replace(' COP', '')} <small>COP</small></p>
            <dl>
              <div><dt>Entrega estimada</dt><dd>{fechaLegible(plan.fechaEstimadaEntrega)}</dd></div>
              <div><dt>Fases</dt><dd>{plan.fases.length}</dd></div>
              <div><dt>Las aprueba {pila}</dt><dd>{plan.fases.filter((f) => f.apruebaCliente).length}</dd></div>
              {estado === 'ENVIADO' && <div><dt>Responde hasta</dt><dd>{respondeHasta}</dd></div>}
              {estado === 'ENVIADO' && <div><dt>Vence</dt><dd>{venceElDia}</dd></div>}
            </dl>
            {plan.condicionesCotizacion && <p className="cotizacion__nota">{plan.condicionesCotizacion}</p>}
          </section>
        </aside>
      </div>
    </>
  );
}
