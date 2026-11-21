import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { diaColombia } from '../../administracion/formato';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton, EnlaceBoton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { FasesPropuesta } from '../../componentes/FasesPropuesta';
import { Insignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { fechaLegible, fechaSinAnio, pesos } from '../../proyectos/admin';
import { planes } from '../../proyectos/api';
import { fechaConDia, nombrePila, porcentajeFase, type PlanProyecto } from '../../proyectos/plan';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/plan-trabajo.css';

type Abierto = 'aprobar' | 'ajustes' | 'rechazar' | null;

const diaDe = (instante: string) => diaColombia(new Date(instante));

/** Por qué el plan ya no espera la respuesta del cliente (HU-29: «Este plan no está esperando tu respuesta»). */
const TEXTO_ESTADO: Record<string, string> = {
  AJUSTES_PEDIDOS: 'Pediste ajustes: el equipo los revisa y te enviará el plan de nuevo.',
  APROBADO: 'Ya aprobaste el plan y la cotización: el trabajo está en marcha.',
  RECHAZADO: 'Rechazaste la propuesta. El proyecto está en pausa y el administrador te contactará.',
  VENCIDO: 'La propuesta venció sin respuesta y el proyecto quedó en pausa. Puedes pedir que te la envíen de nuevo.',
};

/**
 * Propuesta de plan y cotización para el cliente (HU-29, RN-29; prototipo/cliente/plan-inventario-salon.html): revisa las
 * fases y el valor, y aprueba, pide ajustes o rechaza. Un proyecto ajeno responde 404 y se ve como «Proyecto no encontrado».
 */
export function PaginaPropuesta() {
  const { codigo = '' } = useParams();
  const navegar = useNavigate();
  const notificar = useNotificar();
  const [plan, setPlan] = useState<PlanProyecto | null>(null);
  const [error, setError] = useState<{ estado: number; mensaje: string } | null>(null);
  const [dialogo, setDialogo] = useState<Abierto>(null);

  useTituloDocumento(plan ? `${plan.nombre} · Propuesta` : 'Propuesta');

  useEffect(() => {
    let vigente = true;
    setPlan(null);
    setError(null);
    planes.obtener(codigo)
      .then((recibido) => vigente && setPlan(recibido))
      .catch((e) => vigente && setError({
        estado: e instanceof ErrorApi ? e.estado : 0,
        mensaje: separarErrores(e).general ?? 'No pudimos cargar la propuesta.',
      }));
    return () => { vigente = false; };
  }, [codigo]);

  if (error?.estado === 404) {
    return (
      <EstadoVacio titulo="Proyecto no encontrado" texto="No encontramos este proyecto en tu cuenta. Revisa que el enlace esté completo."
        accion={<EnlaceBoton a="/cliente/proyectos" variante="contorno" icono="lista">Ir a mis proyectos</EnlaceBoton>} />
    );
  }
  if (error) {
    return (
      <>
        <Alerta tipo={error.estado === 409 ? 'info' : 'error'}>{error.mensaje}</Alerta>
        <EnlaceBoton a="/cliente/proyectos" variante="contorno" icono="lista">Ir a mis proyectos</EnlaceBoton>
      </>
    );
  }
  if (!plan) return <Cargando texto="Cargando la propuesta…" />;

  const pila = nombrePila(plan.productOwner);
  const espera = plan.estadoPlan === 'ENVIADO';
  const aprueba = plan.fases.filter((f) => f.apruebaCliente);
  const primera = plan.fases[0];
  const lista = aprueba.map((f) => f.nombre);
  const textoAprueba = lista.length <= 1 ? (lista[0] ?? '') : `${lista.slice(0, -1).join(', ')} y ${lista[lista.length - 1]}`;
  const respondeHasta = plan.plazoRespuesta ? fechaConDia(plan.plazoRespuesta) : '';
  const venceElDia = plan.venceEl ? fechaConDia(plan.venceEl) : '';

  function terminar(mensaje: string) {
    notificar(mensaje);
    navegar('/cliente/proyectos');
  }

  const acciones = (clase?: string) => (
    <>
      <Boton variante="exito" bloque icono="exito" className={clase} onClick={() => setDialogo('aprobar')}>
        {clase ? 'Aprobar' : 'Aprobar plan y cotización'}
      </Boton>
      <Boton variante="contorno" bloque onClick={() => setDialogo('ajustes')}>Pedir ajustes</Boton>
      <Boton variante="fantasma" bloque icono="rechazar" className="boton--rechazar" onClick={() => setDialogo('rechazar')}>
        {clase ? 'Rechazar' : 'Rechazar propuesta'}
      </Boton>
    </>
  );

  return (
    <>
      <div className="encabezado-vista aparecer">
        <div>
          <h1 className="encabezado-vista__titulo">
            {plan.nombre} {espera && <Insignia variante="plan">Propuesta por aprobar</Insignia>}
          </h1>
          <p className="encabezado-vista__texto">
            {plan.codigo} · {plan.servicio} · {plan.productOwner}{' '}
            {plan.enviadoAt ? `te envió el plan de trabajo y la cotización el ${fechaSinAnio(diaDe(plan.enviadoAt))}` : 'preparó tu plan de trabajo'}.
            {espera && <> Revísalos y apruébalos o pide ajustes antes del <strong>{respondeHasta}</strong>.</>}
          </p>
        </div>
      </div>

      {!espera && <Alerta tipo="info" titulo="Este plan no está esperando tu respuesta">{TEXTO_ESTADO[plan.estadoPlan] ?? ''}</Alerta>}

      <div className="propuesta">
        <div className="propuesta__principal">
          <section className="tarjeta plan-seccion aparecer" aria-labelledby="titulo-plan-cliente">
            <div className="plan-seccion__cabecera">
              <div>
                <h2 className="plan-seccion__titulo" id="titulo-plan-cliente">Plan de trabajo: {plan.fases.length} fases</h2>
                <p className="plan-seccion__texto">
                  Así vamos a trabajar. Cada fase vale lo mismo ({porcentajeFase(plan.fases.length)}) y el avance de tu proyecto sube cuando se completa.
                  {textoAprueba && ` Tú apruebas ${textoAprueba}; las demás las cierra el equipo.`}
                </p>
              </div>
            </div>
            <FasesPropuesta fases={plan.fases} vista={{ tipo: 'cliente' }} />
          </section>
        </div>

        <aside className="propuesta__lateral">
          <section className="tarjeta propuesta-valor aparecer" style={{ ['--retraso' as string]: '90ms' }} aria-labelledby="titulo-cotizacion-cliente">
            <h2 className="propuesta-valor__etiqueta" id="titulo-cotizacion-cliente">Cotización</h2>
            <p className="propuesta-valor__cifra">
              {plan.valorCotizado === null ? '—' : pesos(plan.valorCotizado).replace(' COP', '')} <small>COP</small>
            </p>
            <dl>
              <div><dt>Entrega estimada</dt><dd>{fechaLegible(plan.fechaEstimadaEntrega)}</dd></div>
              <div><dt>Fases</dt><dd>{plan.fases.length}</dd></div>
              <div><dt>Tú apruebas</dt><dd>{aprueba.length} {aprueba.length === 1 ? 'fase' : 'fases'}</dd></div>
              <div><dt>Garantía</dt><dd>30 días</dd></div>
            </dl>
            {plan.condicionesCotizacion && <p className="cotizacion__nota">{plan.condicionesCotizacion}</p>}
            {espera && (
              <>
                <div className="propuesta-valor__acciones">{acciones()}</div>
                <p className="propuesta-valor__plazo">
                  Tienes hasta el {respondeHasta}. Si no respondes, te lo recordamos; el {venceElDia} la propuesta vence, el proyecto queda
                  en pausa y el equipo queda libre para otros proyectos. Si después de eso no respondes en 30 días, el proyecto se cancela.
                  Podrás pedir que te la envíen de nuevo.
                </p>
              </>
            )}
          </section>
        </aside>
      </div>

      {espera && (
        <div className="barra-accion-movil barra-accion-movil--tres" role="group" aria-label="Responder la propuesta">
          {acciones('barra-accion-movil__principal')}
        </div>
      )}

      <DialogoAprobar
        abierto={dialogo === 'aprobar'} plan={plan} primeraFase={primera?.nombre ?? ''} alCerrar={() => setDialogo(null)}
        alTerminar={() => terminar(`¡Plan aprobado! ${pila} empieza la fase 1 y verás el avance en tu panel.`)}
      />
      <DialogoRespuesta
        tipo="ajustes" abierto={dialogo === 'ajustes'} plan={plan} alCerrar={() => setDialogo(null)}
        alTerminar={() => terminar(`Enviamos tus comentarios a ${pila}. Te avisaremos cuando el plan esté ajustado.`)}
      />
      <DialogoRespuesta
        tipo="rechazar" abierto={dialogo === 'rechazar'} plan={plan} alCerrar={() => setDialogo(null)}
        alTerminar={() => terminar('Rechazaste la propuesta. El proyecto quedó en pausa; si no respondes en 30 días, se cancelará.')}
      />
    </>
  );
}

interface PropiedadesDialogo {
  abierto: boolean;
  plan: PlanProyecto;
  alCerrar: () => void;
  alTerminar: () => void;
}

function DialogoAprobar({ abierto, plan, primeraFase, alCerrar, alTerminar }: PropiedadesDialogo & { primeraFase: string }) {
  const [acepta, setAcepta] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const pila = nombrePila(plan.productOwner);

  useEffect(() => {
    if (abierto) {
      setAcepta(false);
      setError(null);
      setGeneral(null);
    }
  }, [abierto]);

  async function aprobar(evento: FormEvent) {
    evento.preventDefault();
    if (!acepta) {
      setError('Confirma que leíste el plan y la cotización.');
      return;
    }
    setEnviando(true);
    try {
      await planes.aprobar(plan.codigo);
      alTerminar();
    } catch (e) {
      setGeneral(separarErrores(e).general);
      setEnviando(false);
    }
  }

  return (
    <Dialogo
      abierto={abierto} alCerrar={() => !enviando && alCerrar()} icono="exito" tono="exito" titulo="Aprobar plan y cotización"
      texto={`Al aprobar, el plan queda fijo y ${plan.productOwner} empieza la fase 1: ${primeraFase}.`}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form="formulario-aprobar" variante="exito" icono="exito" cargando={enviando}>Aprobar</Boton>
        </>
      )}
    >
      <form id="formulario-aprobar" onSubmit={(e) => void aprobar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <dl className="cotizacion__resumen">
          <div><dt>Valor</dt><dd>{plan.valorCotizado === null ? '—' : pesos(plan.valorCotizado)}</dd></div>
          <div><dt>Entrega estimada</dt><dd>{fechaLegible(plan.fechaEstimadaEntrega)}</dd></div>
          <div><dt>Fases</dt><dd>{plan.fases.length} · {porcentajeFase(plan.fases.length)} cada una</dd></div>
        </dl>
        <div className={['campo', error && 'campo--invalido'].filter(Boolean).join(' ')}>
          <label className="casilla">
            <input type="checkbox" checked={acepta} onChange={(e) => { setAcepta(e.target.checked); setError(null); }}
                   aria-invalid={error ? true : undefined} aria-describedby={error ? 'aprobar-mensaje' : undefined} />
            <span>Leí el plan de trabajo y la cotización, y estoy de acuerdo.</span>
          </label>
          {error && <p className="campo__mensaje" id="aprobar-mensaje" role="alert"><span className="campo__mensaje-texto">{error}</span></p>}
        </div>
        <p className="dialogo__texto">Con {pila} podrás ver el avance de tu proyecto fase por fase.</p>
      </form>
    </Dialogo>
  );
}

function DialogoRespuesta({ tipo, abierto, plan, alCerrar, alTerminar }: PropiedadesDialogo & { tipo: 'ajustes' | 'rechazar' }) {
  const ajustes = tipo === 'ajustes';
  const [texto, setTexto] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const pila = nombrePila(plan.productOwner);
  const id = `formulario-${tipo}`;

  useEffect(() => {
    if (abierto) {
      setTexto('');
      setError(undefined);
      setGeneral(null);
    }
  }, [abierto]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = texto.trim();
    if (!limpio) return setError(ajustes ? 'Cuéntanos qué quieres cambiar.' : 'Cuéntanos por qué rechazas la propuesta.');
    if (limpio.length < 20) {
      return setError(ajustes ? `Escribe al menos 20 caracteres para que ${pila} te entienda.` : 'Cuéntanos por qué la rechazas (mínimo 20 caracteres)');
    }
    setEnviando(true);
    try {
      if (ajustes) await planes.pedirAjustes(plan.codigo, limpio);
      else await planes.rechazar(plan.codigo, limpio);
      alTerminar();
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setError(porCampo.comentario ?? porCampo.motivo);
      setGeneral(mensaje);
      setEnviando(false);
    }
  }

  return (
    <Dialogo
      abierto={abierto} alCerrar={() => !enviando && alCerrar()} icono={ajustes ? 'enviar' : 'rechazar'}
      tono={ajustes ? 'acento' : 'peligro'} titulo={ajustes ? 'Pedir ajustes' : 'Rechazar la propuesta'}
      texto={ajustes
        ? `${plan.productOwner} ajustará el plan y te lo enviará de nuevo.`
        : 'El proyecto quedará en pausa y el administrador te contactará. Si no respondes en 30 días, el proyecto se cancelará. Si solo quieres cambiar algo del plan, mejor pide ajustes.'}
      acciones={(
        <>
          <Boton variante="contorno" disabled={enviando} onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form={id} variante={ajustes ? 'acento' : 'peligro'} icono={ajustes ? 'enviar' : 'rechazar'} cargando={enviando}>
            {ajustes ? 'Enviar comentarios' : 'Sí, rechazar'}
          </Boton>
        </>
      )}
    >
      <form id={id} onSubmit={(e) => void enviar(e)} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <Campo
          etiqueta={ajustes ? 'Qué quieres cambiar' : 'Por qué la rechazas'} error={error}
          ayuda={ajustes
            ? 'Por ejemplo: una fase que falta, un paso que no necesitas, la fecha o el valor.'
            : 'Por ejemplo: el valor supera tu presupuesto o ya no necesitas el sistema.'}
          contador={{ actual: texto.length, maximo: 600 }}
        >
          <textarea
            className="campo__control--area" rows={3} maxLength={600} value={texto}
            placeholder={ajustes ? `Escribe tus comentarios para ${pila}` : 'Cuéntanos el motivo'}
            onChange={(e) => { setTexto(e.target.value); setError(undefined); }}
          />
        </Campo>
      </form>
    </Dialogo>
  );
}
