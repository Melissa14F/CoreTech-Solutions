import { Fragment, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { contenidoSitio, type ContenidoEdicion, type TextoEditable } from '../../catalogo/api';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Insignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-contenido.css';

interface CampoTexto {
  clave: string;
  etiqueta: string;
  area?: boolean;
  ayuda?: string;
  tipo?: 'tel' | 'email';
}

/** Un campo suelto, dos en una fila o el grupo de un paso del proceso, como en el prototipo. */
type Grupo = CampoTexto | { fila: CampoTexto[] } | { paso: number; campos: CampoTexto[] };

interface Seccion {
  id: string;
  titulo: string;
  indice: string;
  descripcion: ReactNode;
  grupos: Grupo[];
  extra?: ReactNode;
  previa: (t: (clave: string) => string) => ReactNode;
}

const SECCIONES: Seccion[] = [
  {
    id: 'portada', titulo: 'Portada', indice: 'Portada', descripcion: 'Lo primero que ve el visitante al entrar.',
    grupos: [
      { clave: 'portada-etiqueta', etiqueta: 'Etiqueta superior' },
      { fila: [{ clave: 'portada-titulo-1', etiqueta: 'Título (primera línea)' }, { clave: 'portada-titulo-2', etiqueta: 'Título (segunda línea, resaltada)' }] },
      { clave: 'portada-texto', etiqueta: 'Texto de apoyo', area: true },
      { fila: [{ clave: 'portada-boton', etiqueta: 'Botón principal' }, { clave: 'portada-boton-2', etiqueta: 'Botón secundario' }] },
    ],
    previa: (t) => (
      <div className="mini-portada superficie-noche">
        <span className="mini-portada__etiqueta">{t('portada-etiqueta')}</span>
        <p className="mini-portada__titulo"><span>{t('portada-titulo-1')}</span><br /><span className="mini-portada__resalte">{t('portada-titulo-2')}</span></p>
        <p className="mini-portada__texto">{t('portada-texto')}</p>
        <div className="mini-portada__botones"><span className="mini-boton mini-boton--acento">{t('portada-boton')}</span><span className="mini-boton mini-boton--claro">{t('portada-boton-2')}</span></div>
      </div>
    ),
  },
  {
    id: 'servicios', titulo: 'Encabezado de servicios', indice: 'Encabezado de servicios',
    descripcion: <>El título sobre el carrusel. Las tarjetas se editan en <Link to="/admin/servicios">Servicios</Link>.</>,
    grupos: [{ clave: 'servicios-titulo', etiqueta: 'Título' }, { clave: 'servicios-texto', etiqueta: 'Texto', area: true }],
    extra: <Link className="boton boton--contorno boton--pequeno" to="/admin/servicios">Editar las tarjetas de servicios</Link>,
    previa: (t) => (
      <div className="mini-seccion">
        <span className="mini-seccion__etiqueta">Catálogo</span>
        <p className="mini-seccion__titulo">{t('servicios-titulo')}</p>
        <p className="mini-seccion__texto">{t('servicios-texto')}</p>
        <div className="mini-tarjetas" aria-hidden="true"><span /><span /><span /></div>
      </div>
    ),
  },
  {
    id: 'proceso', titulo: 'Cómo funciona', indice: 'Cómo funciona', descripcion: 'Los tres pasos del proceso.',
    grupos: [
      { clave: 'proceso-titulo', etiqueta: 'Título' },
      { clave: 'proceso-texto', etiqueta: 'Texto', area: true },
      ...[1, 2, 3].map((n) => ({ paso: n, campos: [
        { clave: `proceso-paso-${n}`, etiqueta: 'Título' },
        { clave: `proceso-paso-${n}-texto`, etiqueta: 'Texto', area: true },
      ] })),
    ],
    previa: (t) => (
      <div className="mini-seccion">
        <p className="mini-seccion__titulo">{t('proceso-titulo')}</p>
        <p className="mini-seccion__texto">{t('proceso-texto')}</p>
        <ol className="mini-pasos">
          {[1, 2, 3].map((n) => (
            <li key={n} className="mini-pasos__paso"><span className="mini-pasos__numero">{n}</span>
              <span><strong>{t(`proceso-paso-${n}`)}</strong><small>{t(`proceso-paso-${n}-texto`)}</small></span></li>
          ))}
        </ol>
      </div>
    ),
  },
  {
    id: 'contacto', titulo: 'Contacto', indice: 'Contacto', descripcion: 'Se muestran en la sección de contacto y en el pie de página.',
    grupos: [
      { clave: 'contacto-titulo', etiqueta: 'Título' },
      { clave: 'contacto-texto', etiqueta: 'Texto', area: true },
      { clave: 'contacto-direccion', etiqueta: 'Dirección de la oficina' },
      { clave: 'contacto-horario', etiqueta: 'Horario de atención', ayuda: 'Debe coincidir con las franjas de agendamiento (RN-01).' },
      { fila: [{ clave: 'contacto-telefono', etiqueta: 'Teléfono', tipo: 'tel' }, { clave: 'contacto-correo', etiqueta: 'Correo', tipo: 'email' }] },
    ],
    previa: (t) => (
      <div className="mini-seccion">
        <p className="mini-seccion__titulo">{t('contacto-titulo')}</p>
        <p className="mini-seccion__texto">{t('contacto-texto')}</p>
        <ul className="mini-contacto">
          {['contacto-direccion', 'contacto-horario', 'contacto-telefono', 'contacto-correo'].map((c) => <li key={c}><span>{t(c)}</span></li>)}
        </ul>
      </div>
    ),
  },
  {
    id: 'llamado', titulo: 'Llamado final', indice: 'Llamado final', descripcion: 'La franja oscura antes del pie de página.',
    grupos: [{ clave: 'llamado-titulo', etiqueta: 'Título' }, { clave: 'llamado-texto', etiqueta: 'Texto' }],
    previa: (t) => (
      <div className="mini-llamado superficie-noche">
        <p className="mini-llamado__titulo">{t('llamado-titulo')}</p>
        <p className="mini-llamado__texto">{t('llamado-texto')}</p>
        <span className="mini-boton mini-boton--acento">Agendar cita</span>
      </div>
    ),
  },
  {
    id: 'pie', titulo: 'Pie de página', indice: 'Pie de página', descripcion: 'Descripción de la empresa, misión y visión.',
    grupos: [
      { clave: 'pie-descripcion', etiqueta: 'Descripción de CoreTech', area: true },
      { clave: 'pie-mision', etiqueta: 'Misión', area: true },
      { clave: 'pie-vision', etiqueta: 'Visión', area: true },
    ],
    extra: <p className="campo__ayuda">El año del © se actualiza solo. La política de datos y los términos están en <Link to="/legal" target="_blank" rel="noopener">la página legal</Link>.</p>,
    previa: (t) => (
      <div className="mini-pie superficie-noche">
        <p className="mini-pie__marca">CoreTech</p>
        <p className="mini-pie__texto">{t('pie-descripcion')}</p>
        <p className="mini-pie__titulo">Misión</p>
        <p className="mini-pie__texto">{t('pie-mision')}</p>
        <p className="mini-pie__titulo">Visión</p>
        <p className="mini-pie__texto">{t('pie-vision')}</p>
        <p className="mini-pie__legal">© {new Date().getFullYear()} CoreTech Solutions · Política de tratamiento de datos · Términos y condiciones</p>
      </div>
    ),
  },
];

const camposDe = (s: Seccion): CampoTexto[] =>
  s.grupos.flatMap((g) => ('fila' in g ? g.fila : 'paso' in g ? g.campos : [g]));

const TELEFONO = /^[0-9 ]{7,14}$/;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** Mismas reglas y mensajes que el backend (ServicioContenido) y el prototipo (admin/contenido.html). */
function errorDe(texto: TextoEditable, valor: string): string | null {
  const v = valor.trim();
  if (!v) return 'Este texto no puede quedar vacío.';
  if (v.length < texto.minLongitud) return `Escribe al menos ${texto.minLongitud} caracteres.`;
  if (v.length > texto.maxLongitud) return `Escribe máximo ${texto.maxLongitud} caracteres.`;
  if (texto.clave === 'contacto-telefono' && !TELEFONO.test(v)) return 'Escribe solo números y espacios, por ejemplo 300 123 4567.';
  if (texto.clave === 'contacto-correo' && !CORREO.test(v)) return 'Escribe un correo válido, por ejemplo nombre@empresa.com.';
  return null;
}

const PARTES_FECHA = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'America/Bogota' });

/** "8 nov 2026", como el prototipo (sin "de" ni punto en el mes), en la hora de Colombia. */
function fechaCorta(iso: string): string {
  const partes = PARTES_FECHA.formatToParts(new Date(iso));
  const parte = (tipo: string) => partes.find((p) => p.type === tipo)?.value.replace('.', '') ?? '';
  return `${parte('day')} ${parte('month')} ${parte('year')}`;
}

/** Editar los textos de la página principal (HU-19, RNF-06; prototipo/admin/contenido.html). */
export function PaginaContenido() {
  useTituloDocumento('Contenido del sitio');
  const notificar = useNotificar();
  const { formulario, enfocar } = useEnfocarError();
  const [edicion, setEdicion] = useState<ContenidoEdicion | null>(null);
  const [valores, setValores] = useState<Record<string, string>>({});
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [publicando, setPublicando] = useState(false);
  const [actual, setActual] = useState('portada');

  useEffect(() => {
    contenidoSitio.edicion()
      .then((e) => {
        setEdicion(e);
        setValores(Object.fromEntries(e.textos.map((t) => [t.clave, t.valor])));
      })
      .catch((e) => setGeneral(separarErrores(e).general ?? 'No pudimos cargar los textos.'));
  }, []);

  const porClave = useMemo(() => new Map((edicion?.textos ?? []).map((t) => [t.clave, t])), [edicion]);
  const cambiados = useMemo(
    () => [...porClave.values()].filter((t) => valores[t.clave] !== undefined && valores[t.clave] !== t.valor).map((t) => t.clave),
    [porClave, valores],
  );
  const hayCambios = cambiados.length > 0;

  // Avisa antes de salir de la página con cambios sin publicar
  useEffect(() => {
    if (!hayCambios) return;
    const avisar = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', avisar);
    return () => window.removeEventListener('beforeunload', avisar);
  }, [hayCambios]);

  // Índice: marca la sección que se está viendo
  useEffect(() => {
    const marcar = () => {
      const linea = window.innerHeight * 0.3;
      let visible = 'portada';
      SECCIONES.forEach((s) => {
        const elemento = document.getElementById(`seccion-${s.id}`);
        if (elemento && elemento.getBoundingClientRect().top <= linea) visible = s.id;
      });
      setActual(visible);
    };
    window.addEventListener('scroll', marcar, { passive: true });
    return () => window.removeEventListener('scroll', marcar);
  }, []);

  function cambiar(clave: string, valor: string) {
    setValores((v) => ({ ...v, [clave]: valor }));
    if (errores[clave]) {
      setErrores((e) => {
        const copia = { ...e };
        delete copia[clave];
        return copia;
      });
    }
  }

  function descartar() {
    if (!edicion) return;
    setValores(Object.fromEntries(edicion.textos.map((t) => [t.clave, t.valor])));
    setErrores({});
    notificar('Se descartaron los cambios. Los textos publicados siguen igual.');
  }

  async function publicar(evento: FormEvent) {
    evento.preventDefault();
    if (!hayCambios) {
      notificar('No hay cambios por publicar.', 'info');
      return;
    }
    const nuevos: Record<string, string> = {};
    cambiados.forEach((clave) => {
      const texto = porClave.get(clave);
      const error = texto && errorDe(texto, valores[clave] ?? '');
      if (error) nuevos[clave] = error;
    });
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) {
      notificar('Revisa los campos marcados en rojo.', 'error');
      enfocar();
      return;
    }
    setPublicando(true);
    try {
      const respuesta = await contenidoSitio.publicar(Object.fromEntries(cambiados.map((c) => [c, (valores[c] ?? '').trim()])));
      setEdicion(respuesta);
      setValores(Object.fromEntries(respuesta.textos.map((t) => [t.clave, t.valor])));
      notificar(`${cambiados.length} ${cambiados.length === 1 ? 'texto publicado' : 'textos publicados'} en la página principal.`);
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setErrores(porCampo);
      setGeneral(mensaje);
      enfocar();
    } finally {
      setPublicando(false);
    }
  }

  const texto = (clave: string) => valores[clave]?.trim() || 'Sin texto';

  function campo(c: CampoTexto) {
    const editable = porClave.get(c.clave);
    if (!editable) return null;
    const valor = valores[c.clave] ?? '';
    return (
      <Campo key={c.clave} etiqueta={c.etiqueta} error={errores[c.clave]} ayuda={c.ayuda}
             contador={c.area ? { actual: valor.length, maximo: editable.maxLongitud } : undefined}>
        {c.area ? (
          <textarea name={c.clave} className="campo__control--area" rows={2} maxLength={editable.maxLongitud}
                    value={valor} onChange={(e) => cambiar(c.clave, e.target.value)} />
        ) : (
          <input name={c.clave} type={c.tipo ?? 'text'} inputMode={c.tipo === 'tel' ? 'tel' : undefined} maxLength={editable.maxLongitud}
                 value={valor} onChange={(e) => cambiar(c.clave, e.target.value)} />
        )}
      </Campo>
    );
  }

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Contenido del sitio</h1>
          <p className="encabezado-vista__texto">Edita los textos de la página principal. Cada sección muestra cómo quedará; nada se publica hasta que guardes.</p>
        </div>
        <a className="boton boton--contorno" href="/" target="_blank" rel="noopener">Ver el sitio</a>
      </div>

      {general && <Alerta tipo="error" titulo={general} />}
      {!edicion && !general && <Cargando texto="Cargando los textos…" />}

      {edicion && (
        <div className="editor-contenido">
          <nav className="indice-contenido" aria-label="Secciones de la página principal">
            <p className="indice-contenido__titulo">Página principal</p>
            <ol className="indice-contenido__lista">
              {SECCIONES.map((s) => (
                <li key={s.id}>
                  <a className="indice-contenido__enlace" href={`#seccion-${s.id}`} aria-current={actual === s.id ? 'true' : undefined}>
                    {s.indice}
                    <span className="indice-contenido__punto" aria-hidden="true" hidden={!camposDe(s).some((c) => cambiados.includes(c.clave))} />
                  </a>
                </li>
              ))}
            </ol>
            <p className="indice-contenido__nota">
              {edicion.ultimaPublicacion
                ? `Última publicación: ${fechaCorta(edicion.ultimaPublicacion.fecha)} por ${edicion.ultimaPublicacion.nombre}`
                : 'Aún no se ha publicado ningún cambio'}
            </p>
          </nav>

          <form ref={formulario} className="editor-contenido__secciones" onSubmit={publicar} noValidate>
            {SECCIONES.map((s, i) => {
              const conCambios = camposDe(s).some((c) => cambiados.includes(c.clave));
              return (
                <section key={s.id} className="tarjeta seccion-contenido" id={`seccion-${s.id}`} aria-labelledby={`titulo-${s.id}`}>
                  <div className="seccion-contenido__cabecera">
                    <div>
                      <h2 className="seccion-contenido__titulo" id={`titulo-${s.id}`}>
                        <span className="seccion-formulario__numero" aria-hidden="true">{i + 1}</span>{s.titulo}
                      </h2>
                      <p className="seccion-contenido__descripcion">{s.descripcion}</p>
                    </div>
                    {conCambios && <Insignia variante="pendiente">Sin guardar</Insignia>}
                  </div>
                  <div className="seccion-contenido__cuerpo">
                    <div className="seccion-contenido__campos">
                      {s.grupos.map((g, j) => (
                        <Fragment key={j}>
                          {'fila' in g ? <div className="fila-campos">{g.fila.map(campo)}</div>
                            : 'paso' in g ? <fieldset className="paso-campos"><legend>Paso {g.paso}</legend>{g.campos.map(campo)}</fieldset>
                              : campo(g)}
                        </Fragment>
                      ))}
                      {s.extra}
                    </div>
                    <div className="seccion-contenido__previa">
                      <p className="previa-etiqueta">Así se ve</p>
                      {s.previa(texto)}
                    </div>
                  </div>
                </section>
              );
            })}

            {/* Siempre visible; los botones se activan cuando hay cambios */}
            <div className="barra-cambios" role="region" aria-label="Publicar cambios">
              <p className="barra-cambios__texto" aria-live="polite">
                {hayCambios
                  ? <><strong>{cambiados.length} {cambiados.length === 1 ? 'texto' : 'textos'}</strong> sin publicar</>
                  : 'Sin cambios por publicar'}
              </p>
              <Boton variante="transparente" disabled={!hayCambios || publicando} onClick={descartar}>Descartar</Boton>
              <Boton type="submit" disabled={!hayCambios} cargando={publicando}>Publicar cambios</Boton>
            </div>
          </form>
        </div>
      )}
    </>
  );
}
