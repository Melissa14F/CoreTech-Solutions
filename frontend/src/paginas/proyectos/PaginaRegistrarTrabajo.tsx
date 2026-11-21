import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';

import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton, EnlaceBoton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { Icono } from '../../componentes/Icono';
import { LineaTiempoFases } from '../../componentes/LineaTiempoFases';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { fechaLegible } from '../../proyectos/admin';
import { nombrePila } from '../../proyectos/plan';
import {
  MAX_ADJUNTOS, MAX_NOTA, MAX_NOTA_CORRECCION, MIN_NOTA, pasoEnCurso, seguimiento, validarAdjuntos,
  type DetalleProyecto, type FaseDetalle, type PasoDetalle,
} from '../../seguimiento/api';
import { useDetalleProyecto } from '../../seguimiento/useDetalleProyecto';
import { separarErrores } from '../acceso/erroresFormulario';
import { ProyectoNoEncontrado } from './ProyectoNoEncontrado';

const iniciales = (nombre: string) => nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');
const kb = (bytes: number) => (bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);

/**
 * Registrar trabajo del desarrollador (Product Owner) (HU-03; prototipo/desarrollador/registrar-avance-tienda-en-linea.html):
 * marca un paso de la fase actual como hecho con una nota sencilla para el cliente y, si quiere, sus bocetos. Lo publicado no
 * se edita ni se borra. Agregar correcciones y entregar la fase llegan en el paso de fases (HU-11 a HU-13).
 */
export function PaginaRegistrarTrabajo() {
  const { codigo = '' } = useParams();
  const { detalle, error, recargar } = useDetalleProyecto(codigo);
  useTituloDocumento(detalle ? `Registrar trabajo · ${detalle.nombre}` : 'Registrar trabajo');

  if (error?.estado === 404 || error?.estado === 403) return <ProyectoNoEncontrado vista="equipo" />;
  if (error) {
    return (
      <>
        <Alerta tipo="error">{error.mensaje}</Alerta>
        <EnlaceBoton a="/equipo/proyectos" variante="contorno" icono="carpeta">Ir a mis proyectos</EnlaceBoton>
      </>
    );
  }
  if (!detalle) return <Cargando texto="Cargando el proyecto…" />;
  return <Registro d={detalle} recargar={recargar} />;
}

function Registro({ d, recargar }: { d: DetalleProyecto; recargar: () => Promise<void> }) {
  const pila = nombrePila(d.cliente);
  const fase = d.fases.find((f) => f.estado !== 'APROBADA');
  const habilitado = d.puedeRegistrar && d.estadoPlan === 'APROBADO' && fase?.estado === 'EN_CURSO';
  const pendientes = habilitado ? fase.pasos.filter((p) => p.estado !== 'HECHO') : [];

  return (
    <>
      <Link className="volver" to="/equipo/proyectos"><Icono nombre="chevronIzquierda" />Volver a mis proyectos</Link>
      <div className="proyecto-cabecera aparecer">
        <div>
          <h1 className="proyecto-cabecera__titulo">Registrar trabajo</h1>
          <p className="proyecto-cabecera__subtitulo"><strong>{d.nombre}</strong> · {d.codigo} · {d.servicio}</p>
        </div>
      </div>

      <div className="registro-avance">
        <div className="registro-avance__principal">
          <section className="tarjeta formulario-avance aparecer" aria-labelledby="titulo-nuevo">
            <div className="formulario-avance__cabecera">
              <h2 className="tarjeta__titulo" id="titulo-nuevo">Registrar trabajo</h2>
              {fase && (
                <span className="fase-bloqueada" title="Las fases avanzan en orden (RN-08)">
                  <Icono nombre="candado" />
                  <span>Fase {fase.orden} de {d.fases.length} · <strong>{fase.nombre}</strong></span>
                </span>
              )}
            </div>
            {habilitado && fase && pendientes.length > 0
              ? <Formulario d={d} fase={fase} pendientes={pendientes} alPublicar={recargar} />
              : <SinRegistro d={d} fase={fase} habilitado={habilitado} />}
          </section>

          <section aria-labelledby="titulo-linea">
            <h2 className="proyecto-detalle__titulo" id="titulo-linea">Fases del proyecto</h2>
            {d.fases.length === 0
              ? <p className="proyecto-detalle__nota">Este proyecto todavía no tiene un plan de trabajo.</p>
              : <LineaTiempoFases fases={d.fases} mirada="equipo" cliente={d.cliente} productOwner={d.productOwner} />}
          </section>
        </div>

        <aside className="registro-avance__lateral">
          <section className="tarjeta aparecer" aria-labelledby="titulo-cliente">
            <h2 className="tarjeta__titulo" id="titulo-cliente">Cliente</h2>
            <div className="persona-contacto">
              <span className="avatar avatar--acento" aria-hidden="true">{iniciales(d.cliente)}</span>
              <span><span className="persona-contacto__nombre">{d.cliente}</span><span className="persona-contacto__rol">Recibe un aviso con cada avance</span></span>
            </div>
            {d.clienteCorreo && (
              <a className="boton boton--contorno boton--bloque" href={`mailto:${d.clienteCorreo}`}><Icono nombre="correo" />Escribir a {pila}</a>
            )}
          </section>

          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '30ms' }} aria-labelledby="titulo-equipo">
            <h2 className="tarjeta__titulo" id="titulo-equipo">Equipo</h2>
            <div className="persona-contacto">
              <span className="avatar" aria-hidden="true">{iniciales(d.productOwner)}</span>
              <span><span className="persona-contacto__nombre">Tú · {d.productOwner}</span><span className="persona-contacto__rol">Product Owner</span></span>
            </div>
            <p className="equipo__nota">Registras el avance de todo el equipo y eres quien habla con {pila}.</p>
            {d.equipo.length > 0 && (
              <>
                <p className="equipo__titulo">Tu equipo en este proyecto</p>
                <ul className="equipo">
                  {d.equipo.map((p) => (
                    <li className="equipo__miembro" key={p.nombre}>
                      <span className="avatar avatar--pequeno" aria-hidden="true">{iniciales(p.nombre)}</span>
                      <span><span className="equipo__nombre">{p.nombre}</span><span className="equipo__rol">{p.rol}</span></span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>

          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '60ms' }} aria-labelledby="titulo-datos">
            <h2 className="tarjeta__titulo" id="titulo-datos">Estado del proyecto</h2>
            <dl className="lista-datos">
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Avance del proyecto</dt><dd className="lista-datos__valor">{Math.round(d.avancePorcentaje * 10) / 10} %</dd></div>
              <div className="lista-datos__fila">
                <dt className="lista-datos__dato">Fase actual</dt>
                <dd className="lista-datos__valor">{fase ? `${fase.nombre} · ${fase.pasosHechos} de ${fase.pasosTotal} pasos` : '—'}</dd>
              </div>
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Entrega estimada</dt><dd className="lista-datos__valor">{fechaLegible(d.fechaEstimadaEntrega)}</dd></div>
              <div className="lista-datos__fila"><dt className="lista-datos__dato">Pasos hechos</dt><dd className="lista-datos__valor">{d.pasosHechos}</dd></div>
            </dl>
          </section>

          <section className="tarjeta aparecer" style={{ ['--retraso' as string]: '90ms' }} aria-labelledby="titulo-plan">
            <h2 className="tarjeta__titulo" id="titulo-plan">Plan de trabajo</h2>
            <p className="equipo__nota">El plan está aprobado por {pila}: las fases avanzan en orden y cada una vale lo mismo en el avance.</p>
            <EnlaceBoton a={`/equipo/proyectos/${d.codigo}/plan`} variante="contorno" bloque icono="lista">Ver el plan de trabajo</EnlaceBoton>
          </section>

          <section className="tarjeta reglas-avance aparecer" style={{ ['--retraso' as string]: '120ms' }} aria-labelledby="titulo-reglas">
            <h2 className="tarjeta__titulo" id="titulo-reglas">Antes de publicar</h2>
            <ul className="reglas-avance__lista">
              <li>Marca cada paso cuando esté hecho y deja una nota sencilla: {pila} no es programador(a).</li>
              <li>Puedes agregar hasta 5 bocetos o documentos (PNG, JPG, SVG o PDF) para que {pila} los vea.</li>
              <li>Solo el desarrollador (Product Owner) del equipo registra el trabajo del proyecto.</li>
              <li>Lo publicado no se edita ni se borra: queda en la auditoría.</li>
            </ul>
          </section>
        </aside>
      </div>
    </>
  );
}

/** Por qué no hay formulario: el proyecto no admite registros, la fase espera al cliente o no queda nada por hacer. */
function SinRegistro({ d, fase, habilitado }: { d: DetalleProyecto; fase: FaseDetalle | undefined; habilitado: boolean }) {
  const pila = nombrePila(d.cliente);
  if (!d.puedeRegistrar || d.estado !== 'ACTIVO') {
    return <Alerta tipo="info">El proyecto no admite registros en su estado actual.</Alerta>;
  }
  if (d.estadoPlan !== 'APROBADO') {
    return <Alerta tipo="info">El trabajo empieza cuando {pila} apruebe el plan y la cotización.</Alerta>;
  }
  if (!fase) return <Alerta tipo="exito">Todas las fases del proyecto están aprobadas.</Alerta>;
  if (fase.estado === 'EN_REVISION') {
    return <Alerta tipo="advertencia" titulo={`Fase terminada · esperando la aprobación de ${pila}`}>Cuando responda, aquí podrás seguir con la siguiente fase.</Alerta>;
  }
  return habilitado
    ? <Alerta tipo="info">No quedan pasos por marcar en {fase.nombre}.</Alerta>
    : <Alerta tipo="info">La fase {fase.nombre} todavía no está en curso.</Alerta>;
}

interface PropiedadesFormulario {
  d: DetalleProyecto;
  fase: FaseDetalle;
  pendientes: PasoDetalle[];
  alPublicar: () => Promise<void>;
}

function Formulario({ d, fase, pendientes, alPublicar }: PropiedadesFormulario) {
  const notificar = useNotificar();
  const pila = nombrePila(d.cliente);
  const enCurso = pasoEnCurso(fase);
  const [pasoId, setPasoId] = useState(enCurso ?? pendientes[0]?.id ?? '');
  const [nota, setNota] = useState('');
  const [archivos, setArchivos] = useState<File[]>([]);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [confirmar, setConfirmar] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const entrada = useRef<HTMLInputElement>(null);

  // Después de publicar, el paso elegido ya no está pendiente: se pasa al siguiente.
  useEffect(() => {
    if (!pendientes.some((p) => p.id === pasoId)) setPasoId(enCurso ?? pendientes[0]?.id ?? '');
  }, [pendientes, pasoId, enCurso]);

  const paso = pendientes.find((p) => p.id === pasoId);
  const maximo = paso && paso.tipo !== 'PASO' ? MAX_NOTA_CORRECCION : MAX_NOTA;
  const faltan = pendientes.length - 1;

  function revisar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    if (!paso) nuevos.paso = 'Elige el paso que terminaste.';
    const texto = nota.trim();
    if (!texto) nuevos.nota = `Escribe la nota para que ${pila} sepa qué cambió.`;
    else if (texto.length < MIN_NOTA) nuevos.nota = `Escribe al menos ${MIN_NOTA} caracteres.`;
    else if (texto.length > maximo) nuevos.nota = `Escribe máximo ${maximo} caracteres.`;
    const archivosMal = validarAdjuntos(archivos);
    if (archivosMal) nuevos.adjuntos = archivosMal;
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length === 0) setConfirmar(true);
  }

  function elegirArchivos(evento: ChangeEvent<HTMLInputElement>) {
    const elegidos = Array.from(evento.target.files ?? []);
    setArchivos((actuales) => [...actuales, ...elegidos]);
    evento.target.value = '';
    setErrores((e) => { const { adjuntos: _quitado, ...resto } = e; return resto; });
  }

  async function publicar() {
    if (!paso) return;
    setEnviando(true);
    let mensaje = `✓ Paso hecho. ${pila} verá tu nota.`;
    try {
      await seguimiento.marcarHecho(paso.id, nota.trim());
    } catch (e) {
      const { porCampo, general: otro } = separarErrores(e);
      setConfirmar(false);
      setEnviando(false);
      setErrores(porCampo);
      setGeneral(otro ?? null);
      // 409: otra pestaña ya lo registró o la fase cambió; se muestra lo último
      if (e instanceof ErrorApi && e.estado === 409) void alPublicar();
      return;
    }
    if (archivos.length > 0) {
      try {
        await seguimiento.adjuntar(paso.id, archivos);
      } catch (e) {
        const problema = separarErrores(e);
        mensaje = `El paso quedó publicado, pero no se subieron los archivos: ${problema.porCampo.adjuntos ?? problema.general ?? 'intenta de nuevo más tarde'}`;
      }
    }
    setConfirmar(false);
    setNota('');
    setArchivos([]);
    setErrores({});
    setGeneral(null);
    setEnviando(false);
    notificar(mensaje);
    await alPublicar();
  }

  return (
    <>
      <form onSubmit={revisar} noValidate>
        {general && <Alerta tipo="error">{general}</Alerta>}
        <Campo etiqueta="Paso terminado" error={errores.paso ?? errores.estado}>
          <select className="select" value={pasoId} onChange={(e) => { setPasoId(e.target.value); setErrores({}); }}>
            {pendientes.map((p) => (
              <option key={p.id} value={p.id}>
                {p.titulo}{p.tipo !== 'PASO' ? ' (corrección)' : ''}{p.id === enCurso ? ' (en curso)' : ''}
              </option>
            ))}
          </select>
        </Campo>

        <p className="impacto-proyecto" aria-live="polite">
          {faltan > 0
            ? <>Después de este paso {faltan === 1 ? <>quedará <strong>1</strong> paso</> : <>quedarán <strong>{faltan}</strong> pasos</>} por hacer en {fase.nombre}.</>
            : <>Es el último paso por hacer en <strong>{fase.nombre}</strong>.</>}
        </p>

        <Campo etiqueta={`Nota para ${pila}: ¿qué se hizo?`} error={errores.nota} contador={{ actual: nota.length, maximo }}
               ayuda="Escríbelo como se lo dirías en persona, sin términos técnicos.">
          <textarea className="campo__control--area" value={nota} maxLength={maximo} onChange={(e) => setNota(e.target.value)}
                    placeholder="Ejemplo: ya recibes cada pedido en tu panel y tu cliente recibe un correo con el resumen de su compra." />
        </Campo>

        <Campo etiqueta="Bocetos o documentos" opcional error={errores.adjuntos}
               ayuda={`PNG, JPG, SVG o PDF · hasta ${MAX_ADJUNTOS} archivos de 5 MB.`}>
          <input ref={entrada} type="file" multiple accept=".png,.jpg,.jpeg,.svg,.pdf,image/png,image/jpeg,image/svg+xml,application/pdf"
                 onChange={elegirArchivos} />
        </Campo>
        {archivos.length > 0 && (
          <ul className="archivos-elegidos" aria-label="Archivos elegidos">
            {archivos.map((a, i) => (
              <li key={`${a.name}-${i}`}>
                <span>{a.name} <small>{kb(a.size)}</small></span>
                <button type="button" className="boton-icono boton-icono--pequeno" aria-label={`Quitar ${a.name}`}
                        onClick={() => setArchivos((actuales) => actuales.filter((_, j) => j !== i))}>
                  <Icono nombre="cerrar" />
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="formulario-avance__pie">
          <p className="formulario-avance__nota"><Icono nombre="candado" />Lo que publiques no se puede editar ni borrar.</p>
          <Boton type="submit" variante="acento" icono="enviar">Marcar como hecho</Boton>
        </div>
      </form>

      <Dialogo
        abierto={confirmar} alCerrar={() => !enviando && setConfirmar(false)} icono="enviar" titulo="¿Marcar el paso como hecho?"
        texto={`«${paso?.titulo ?? ''}» quedará con ✓ y ${pila} verá tu nota. No se podrá editar ni borrar.`}
        acciones={(
          <>
            <Boton variante="contorno" disabled={enviando} onClick={() => setConfirmar(false)}>Revisar de nuevo</Boton>
            <Boton variante="acento" cargando={enviando} onClick={() => void publicar()}>Sí, publicar</Boton>
          </>
        )}
      />
    </>
  );
}
