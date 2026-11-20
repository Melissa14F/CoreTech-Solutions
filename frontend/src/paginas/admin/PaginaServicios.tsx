import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { ErrorApi } from '../../api/cliente';
import { GALERIA, catalogo, srcImagen, type DatosServicio, type ServicioGestion } from '../../catalogo/api';
import { TarjetaServicio } from '../../catalogo/TarjetaServicio';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { Icono } from '../../componentes/Icono';
import { Insignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-servicios.css';

const VACIO: DatosServicio = { nombre: '', descripcionCorta: '', imagenUrl: '', imagenAlt: '', visible: true };
const TIPOS_IMAGEN = ['image/webp', 'image/png', 'image/jpeg'];
const MAX_IMAGEN = 2 * 1024 * 1024;

type Errores = Partial<Record<keyof DatosServicio | 'imagen', string>>;

/** Mismos límites y mensajes que el backend (ServicioCatalogo) y el prototipo (admin/servicios.html). */
function validar(d: DatosServicio): Errores {
  const e: Errores = {};
  const largo = (campo: keyof DatosServicio, valor: string, min: number, max: number, vacio: string) => {
    const v = valor.trim();
    if (!v) e[campo] = vacio;
    else if (v.length < min) e[campo] = `Escribe al menos ${min} caracteres.`;
    else if (v.length > max) e[campo] = `Escribe máximo ${max} caracteres.`;
  };
  largo('nombre', d.nombre, 4, 40, 'Escribe el nombre del servicio.');
  largo('descripcionCorta', d.descripcionCorta, 20, 90, 'Escribe la descripción.');
  if (!d.imagenUrl) e.imagenUrl = 'Elige o sube una imagen.';
  largo('imagenAlt', d.imagenAlt, 10, 120, 'Describe la imagen: lo leen las personas con lector de pantalla.');
  return e;
}

const textoCitas = (n: number) => (n === 0 ? 'Sin citas todavía' : `${n} ${n === 1 ? 'cita' : 'citas'} en 6 meses`);

/** Administrar los servicios del catálogo (HU-18, RN-21; prototipo/admin/servicios.html). */
export function PaginaServicios() {
  useTituloDocumento('Servicios');
  const notificar = useNotificar();
  const [servicios, setServicios] = useState<ServicioGestion[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [editando, setEditando] = useState<ServicioGestion | 'nuevo' | null>(null);
  const [porEliminar, setPorEliminar] = useState<ServicioGestion | null>(null);
  const [movido, setMovido] = useState<string | null>(null);

  useEffect(() => {
    catalogo.todos().then(setServicios).catch((e) => setErrorCarga(separarErrores(e).general ?? 'No pudimos cargar los servicios.'));
  }, []);

  const reemplazar = (s: ServicioGestion) =>
    setServicios((lista) => (lista ?? []).some((x) => x.servicioId === s.servicioId)
      ? (lista ?? []).map((x) => (x.servicioId === s.servicioId ? s : x))
      : [...(lista ?? []), s]);

  async function mover(s: ServicioGestion, direccion: -1 | 1) {
    try {
      setServicios(await catalogo.mover(s.servicioId, direccion));
      setMovido(s.servicioId);
      notificar(`Orden actualizado: "${s.nombre}" cambió de lugar en el carrusel.`);
    } catch (e) {
      notificar(separarErrores(e).general ?? 'No se pudo cambiar el orden.', 'error');
    }
  }

  async function alternar(s: ServicioGestion) {
    try {
      const actualizado = await catalogo.cambiarVisibilidad(s.servicioId, !s.visible);
      reemplazar(actualizado);
      notificar(`"${s.nombre}" ${actualizado.visible ? 'ya se muestra en el catálogo.' : 'quedó oculto: no se muestra ni se puede agendar.'}`);
    } catch (e) {
      notificar(separarErrores(e).general ?? 'No se pudo cambiar la visibilidad.', 'error');
    }
  }

  const visibles = servicios?.filter((s) => s.visible).length ?? 0;
  const ocultos = (servicios?.length ?? 0) - visibles;
  let posicionVisible = 0;

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Servicios</h1>
          <p className="encabezado-vista__texto">
            Los servicios del catálogo de la página principal. El orden de esta lista es el orden del carrusel; los ocultos no se muestran ni se pueden agendar.
          </p>
        </div>
        <div className="encabezado-vista__acciones">
          <a className="boton boton--contorno" href="/#servicios" target="_blank" rel="noopener">Ver en el sitio</a>
          <Boton icono="mas" onClick={() => setEditando('nuevo')}>Nuevo servicio</Boton>
        </div>
      </div>

      {errorCarga && <Alerta tipo="error" titulo={errorCarga} />}
      {!servicios && !errorCarga && <Cargando texto="Cargando servicios…" />}

      {servicios && (
        <>
          <p className="resumen-servicios" aria-live="polite">
            <strong>{visibles}</strong> visibles en el catálogo · <span>{ocultos} {ocultos === 1 ? 'oculto' : 'ocultos'}</span>
          </p>
          {servicios.length === 0 ? (
            <EstadoVacio titulo="Todavía no hay servicios" texto="Crea el primero con «Nuevo servicio»." />
          ) : (
            <ol className="lista-servicios" aria-label="Servicios en el orden del carrusel">
              {servicios.map((s, indice) => {
                if (s.visible) posicionVisible += 1;
                return (
                  <li key={s.servicioId}
                      className={['servicio-item', !s.visible && 'servicio-item--oculto', movido === s.servicioId && 'esta-movido'].filter(Boolean).join(' ')}>
                    <span className="servicio-item__posicion" aria-hidden="true">{s.visible ? posicionVisible : '–'}</span>
                    <div className="servicio-item__orden" role="group" aria-label={`Cambiar el orden de ${s.nombre}`}>
                      <button type="button" className="boton-icono boton-icono--mini" aria-label={`Subir ${s.nombre}`}
                              disabled={indice === 0} onClick={() => void mover(s, -1)}>
                        <Icono nombre="chevronArriba" />
                      </button>
                      <button type="button" className="boton-icono boton-icono--mini" aria-label={`Bajar ${s.nombre}`}
                              disabled={indice === servicios.length - 1} onClick={() => void mover(s, 1)}>
                        <Icono nombre="chevronAbajo" />
                      </button>
                    </div>
                    <span className="servicio-item__miniatura"><img src={srcImagen(s.imagenUrl)} alt="" width={96} height={64} /></span>
                    <div className="servicio-item__datos">
                      <h2 className="servicio-item__nombre">{s.nombre}</h2>
                      <p className="servicio-item__descripcion">{s.descripcionCorta}</p>
                    </div>
                    <span className="servicio-item__citas">{textoCitas(s.citasUltimos6Meses)}</span>
                    <Insignia variante={s.visible ? 'atendida' : 'pausado'}>{s.visible ? 'Visible' : 'Oculto'}</Insignia>
                    <div className="servicio-item__acciones">
                      <Link className="boton boton--contorno boton--pequeno" to={`/admin/plantillas?servicio=${s.slug}`}
                            aria-label={`Fases de ${s.nombre}: ${s.fasesPlantilla} fases`}>
                        {s.fasesPlantilla} {s.fasesPlantilla === 1 ? 'fase' : 'fases'}
                      </Link>
                      <Boton variante="contorno" tamano="pequeno" onClick={() => setEditando(s)}>Editar</Boton>
                      <button type="button" className="boton-icono boton-icono--pequeno"
                              aria-label={s.visible ? `Ocultar ${s.nombre} del catálogo` : `Mostrar ${s.nombre} en el catálogo`}
                              title={s.visible ? 'Ocultar del catálogo' : 'Mostrar en el catálogo'} onClick={() => void alternar(s)}>
                        <Icono nombre={s.visible ? 'ojoTachado' : 'ojo'} />
                      </button>
                      <button type="button" className="boton-icono boton-icono--pequeno boton-icono--peligro"
                              aria-label={`Eliminar ${s.nombre}`} title="Eliminar" onClick={() => setPorEliminar(s)}>
                        <Icono nombre="papelera" />
                      </button>
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </>
      )}

      {editando && (
        <EditorServicio
          servicio={editando === 'nuevo' ? null : editando}
          alCerrar={() => setEditando(null)}
          alGuardar={(s, nuevo) => {
            reemplazar(s);
            setEditando(null);
            notificar(nuevo
              ? `Servicio "${s.nombre}" creado${s.visible ? ' y publicado en el catálogo.' : ' como oculto.'}`
              : `Cambios guardados en "${s.nombre}".`);
          }}
        />
      )}

      {porEliminar && (
        <DialogoEliminar
          servicio={porEliminar}
          alCerrar={() => setPorEliminar(null)}
          alEliminar={(s) => {
            setPorEliminar(null);
            catalogo.todos().then(setServicios).catch(() => setServicios((l) => (l ?? []).filter((x) => x.servicioId !== s.servicioId)));
            notificar(`"${s.nombre}" se eliminó del catálogo.`);
          }}
          alOcultar={(s) => {
            setPorEliminar(null);
            reemplazar(s);
            notificar(`"${s.nombre}" quedó oculto del catálogo.`);
          }}
        />
      )}
    </>
  );
}

// ---------------------------------------------------------------- Editor (crear y editar con vista previa)

function EditorServicio({ servicio, alCerrar, alGuardar }: {
  servicio: ServicioGestion | null;
  alCerrar: () => void;
  alGuardar: (s: ServicioGestion, nuevo: boolean) => void;
}) {
  const idFormulario = useId();
  const archivo = useRef<HTMLInputElement>(null);
  const { formulario, enfocar } = useEnfocarError();
  const [datos, setDatos] = useState<DatosServicio>(servicio
    ? { nombre: servicio.nombre, descripcionCorta: servicio.descripcionCorta, imagenUrl: servicio.imagenUrl, imagenAlt: servicio.imagenAlt, visible: servicio.visible }
    : VACIO);
  // Imagen propia: la de este servicio (si no es de la galería) o la que se acaba de subir
  const [propia, setPropia] = useState<string | null>(servicio && !GALERIA.some((g) => g.url === servicio.imagenUrl) ? servicio.imagenUrl : null);
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [guardando, setGuardando] = useState(false);

  const cambiar = <K extends keyof DatosServicio>(campo: K, valor: DatosServicio[K]) => setDatos((d) => ({ ...d, [campo]: valor }));

  async function alElegirArchivo(evento: ChangeEvent<HTMLInputElement>) {
    const elegido = evento.target.files?.[0];
    evento.target.value = '';
    if (!elegido) return;
    if (!TIPOS_IMAGEN.includes(elegido.type)) return setErrores((e) => ({ ...e, imagenUrl: 'Sube una imagen en formato WebP, PNG o JPG.' }));
    if (elegido.size > MAX_IMAGEN) return setErrores((e) => ({ ...e, imagenUrl: 'La imagen pesa más de 2 MB. Comprímela o usa formato WebP.' }));
    setSubiendo(true);
    try {
      const { imagenUrl } = await catalogo.subirImagen(elegido);
      setPropia(imagenUrl);
      cambiar('imagenUrl', imagenUrl);
      setErrores((actual) => {
        const sinImagen = { ...actual };
        delete sinImagen.imagenUrl;
        return sinImagen;
      });
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setErrores((actual) => ({ ...actual, imagenUrl: porCampo.imagen ?? mensaje ?? 'No se pudo subir la imagen.' }));
    } finally {
      setSubiendo(false);
    }
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos = validar(datos);
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) return enfocar();
    setGuardando(true);
    try {
      const guardado = servicio ? await catalogo.editar(servicio.servicioId, datos) : await catalogo.crear(datos);
      alGuardar(guardado, !servicio);
    } catch (e) {
      const { porCampo, general: mensaje } = separarErrores(e);
      setErrores(porCampo);
      setGeneral(e instanceof ErrorApi && e.estado === 404 ? 'Este servicio ya no existe. Recarga la página.' : mensaje);
      setGuardando(false);
      enfocar();
    }
  }

  return (
    <Dialogo
      abierto
      alCerrar={alCerrar}
      ancho
      clase="dialogo--editor"
      icono="capas"
      titulo={servicio ? 'Editar servicio' : 'Nuevo servicio'}
      texto="A la derecha ves cómo quedará la tarjeta en el catálogo."
      acciones={
        <>
          <Boton variante="contorno" onClick={alCerrar}>Volver</Boton>
          <Boton type="submit" form={idFormulario} cargando={guardando} disabled={subiendo}>{servicio ? 'Guardar cambios' : 'Crear servicio'}</Boton>
        </>
      }
    >
      <form id={idFormulario} ref={formulario} onSubmit={guardar} noValidate className="editor-servicio">
        <div className="editor-servicio__campos">
          {general && <Alerta tipo="error" titulo={general} />}
          <Campo etiqueta="Nombre" error={errores.nombre} contador={{ actual: datos.nombre.length, maximo: 40 }}>
            <input name="nombre" type="text" maxLength={40} placeholder="Ejemplo: Sistema de facturación"
                   value={datos.nombre} onChange={(e) => cambiar('nombre', e.target.value)} />
          </Campo>
          <Campo etiqueta="Descripción corta" error={errores.descripcionCorta} contador={{ actual: datos.descripcionCorta.length, maximo: 90 }}>
            <textarea name="descripcionCorta" className="campo__control--area campo__control--corta" maxLength={90}
                      placeholder="Una frase que explique el beneficio para el cliente."
                      value={datos.descripcionCorta} onChange={(e) => cambiar('descripcionCorta', e.target.value)} />
          </Campo>

          <fieldset className={['grupo-opciones', 'campo', errores.imagenUrl && 'campo--invalido'].filter(Boolean).join(' ')}
                    aria-describedby={errores.imagenUrl ? `${idFormulario}-imagen` : undefined}>
            <legend className="campo__etiqueta">Imagen</legend>
            <div className="galeria">
              {GALERIA.map((g) => (
                <label key={g.url} className="galeria__opcion">
                  <input type="radio" name="imagen" value={g.url} checked={datos.imagenUrl === g.url}
                         aria-invalid={errores.imagenUrl ? true : undefined}
                         onChange={() => cambiar('imagenUrl', g.url)} />
                  <img src={srcImagen(g.url)} alt={g.nombre} width={96} height={64} />
                </label>
              ))}
              <label className={['galeria__opcion', 'galeria__opcion--subir', propia && 'tiene-imagen'].filter(Boolean).join(' ')}>
                <input type="radio" name="imagen" value="propia" checked={propia !== null && datos.imagenUrl === propia}
                       aria-invalid={errores.imagenUrl ? true : undefined}
                       onChange={() => propia && cambiar('imagenUrl', propia)}
                       onClick={() => {
                         // Sin imagen propia, o si ya está elegida ("Cambiar"), abre el selector de archivos
                         if (!propia || datos.imagenUrl === propia) archivo.current?.click();
                       }} />
                <span style={propia ? { backgroundImage: `url("${srcImagen(propia)}")` } : undefined}>
                  <Icono nombre="subirArchivo" />{subiendo ? 'Subiendo…' : propia ? 'Cambiar' : 'Subir'}
                </span>
              </label>
            </div>
            <p className="campo__ayuda">Proporción 3:2, mínimo 1200 × 800 px, en WebP, PNG o JPG.</p>
            {errores.imagenUrl && (
              <p className="campo__mensaje" id={`${idFormulario}-imagen`} role="alert">
                <Icono nombre="alerta" /><span className="campo__mensaje-texto">{errores.imagenUrl}</span>
              </p>
            )}
          </fieldset>
          <input ref={archivo} className="solo-lector" type="file" accept="image/webp,image/png,image/jpeg" tabIndex={-1}
                 aria-hidden="true" data-testid="archivo-imagen" onChange={(e) => void alElegirArchivo(e)} />

          <Campo etiqueta="Texto alternativo de la imagen" error={errores.imagenAlt}>
            <input name="imagenAlt" type="text" maxLength={120} placeholder="Describe la imagen para quien usa lector de pantalla"
                   value={datos.imagenAlt} onChange={(e) => cambiar('imagenAlt', e.target.value)} />
          </Campo>
          <label className="casilla">
            <input type="checkbox" name="visible" checked={datos.visible} onChange={(e) => cambiar('visible', e.target.checked)} />
            <span>Mostrar en el catálogo</span>
          </label>
        </div>

        <div className="editor-servicio__previa" aria-label="Vista previa de la tarjeta">
          <p className="editor-servicio__etiqueta">Vista previa</p>
          <TarjetaServicio
            nombre={datos.nombre.trim() || 'Nombre del servicio'}
            descripcion={datos.descripcionCorta.trim() || 'La descripción aparecerá aquí.'}
            imagenUrl={datos.imagenUrl}
            imagenAlt={datos.imagenAlt}
            pie={<span className="boton boton--contorno boton--bloque" aria-hidden="true">Agendar cita</span>}
          />
          {!datos.visible && (
            <p className="editor-servicio__oculto"><Icono nombre="ojoTachado" />No se mostrará en el catálogo.</p>
          )}
        </div>
      </form>
    </Dialogo>
  );
}

// ---------------------------------------------------------------- Eliminar (RN-21)

function DialogoEliminar({ servicio, alCerrar, alEliminar, alOcultar }: {
  servicio: ServicioGestion;
  alCerrar: () => void;
  alEliminar: (s: ServicioGestion) => void;
  alOcultar: (s: ServicioGestion) => void;
}) {
  // Con citas o proyectos no se elimina: lo dice el backend (409) y se ofrece ocultarlo
  const [enUso, setEnUso] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [trabajando, setTrabajando] = useState(false);

  async function eliminar() {
    setTrabajando(true);
    try {
      await catalogo.eliminar(servicio.servicioId);
      alEliminar(servicio);
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 409) setEnUso(true);
      else setError(separarErrores(e).general);
      setTrabajando(false);
    }
  }

  async function ocultar() {
    setTrabajando(true);
    try {
      alOcultar(await catalogo.cambiarVisibilidad(servicio.servicioId, false));
    } catch (e) {
      setError(separarErrores(e).general);
      setTrabajando(false);
    }
  }

  return (
    <Dialogo
      abierto
      alCerrar={alCerrar}
      icono="alerta"
      tono="peligro"
      titulo={enUso ? 'No se puede eliminar' : `¿Eliminar "${servicio.nombre}"?`}
      texto={enUso
        ? `"${servicio.nombre}" tiene citas o proyectos asociados que deben conservarse. Puedes ocultarlo para que deje de aparecer en el catálogo.`
        : 'Se borrará del catálogo de forma definitiva, junto con su plantilla de fases. Esta acción queda registrada en la auditoría.'}
      acciones={
        <>
          <Boton variante="contorno" onClick={alCerrar}>Volver</Boton>
          {enUso
            ? servicio.visible && <Boton variante="oscuro" cargando={trabajando} onClick={() => void ocultar()}>Ocultarlo</Boton>
            : <Boton variante="peligro" cargando={trabajando} onClick={() => void eliminar()}>Eliminar</Boton>}
        </>
      }
    >
      {error && <Alerta tipo="error" titulo={error} />}
    </Dialogo>
  );
}
