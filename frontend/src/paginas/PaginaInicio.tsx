import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { catalogo, type ServicioPublico } from '../catalogo/api';
import { Carrusel } from '../catalogo/Carrusel';
import { enlaceTelefono, useContenido } from '../catalogo/ContenidoSitio';
import { TarjetaServicio } from '../catalogo/TarjetaServicio';
import { Cargando } from '../componentes/Cargando';
import { Icono, type NombreIcono } from '../componentes/Icono';
import { useTituloDocumento } from '../componentes/useTituloDocumento';
import { useSesion } from '../sesion/ContextoSesion';
import '../styles/vistas/inicio.css';

/** Adónde lleva "Agendar": el cliente con sesión va directo al agendamiento; los demás, a crear su cuenta. */
function useDestinoAgendar() {
  const { sesion } = useSesion();
  const esCliente = sesion.estado === 'conectado' && sesion.usuario.rol === 'CLIENTE';
  return (slug?: string) => {
    const consulta = slug ? `?servicio=${encodeURIComponent(slug)}` : '';
    return esCliente ? `/cliente/agendar${consulta}` : `/registro${consulta}`;
  };
}

const PASOS: { clave: string; icono: NombreIcono }[] = [
  { clave: 'proceso-paso-1', icono: 'agendar' },
  { clave: 'proceso-paso-2', icono: 'persona' },
  { clave: 'proceso-paso-3', icono: 'tendencia' },
];

/** Página principal (HU-07, prototipo/index.html): portada, catálogo, proceso, contacto y llamado final. */
export function PaginaInicio() {
  useTituloDocumento('');
  const { texto } = useContenido();
  const destinoAgendar = useDestinoAgendar();
  const [servicios, setServicios] = useState<ServicioPublico[] | null>(null);

  useEffect(() => {
    let vigente = true;
    catalogo.visibles()
      .then((lista) => vigente && setServicios(lista))
      .catch(() => vigente && setServicios([]));
    return () => {
      vigente = false;
    };
  }, []);

  const telefono = texto('contacto-telefono');
  const correo = texto('contacto-correo');

  return (
    <>
      {/* ===================== Portada ===================== */}
      <section className="portada superficie-noche" aria-labelledby="titulo-portada">
        <div className="contenedor portada__grilla">
          <div>
            <span className="pildora portada__pildora">{texto('portada-etiqueta')}</span>
            <h1 className="portada__titulo" id="titulo-portada">
              {texto('portada-titulo-1')}{' '}
              <span className="portada__titulo-acento">{texto('portada-titulo-2')}</span>
            </h1>
            <p className="portada__texto">{texto('portada-texto')}</p>
            <div className="portada__acciones">
              <Link className="boton boton--acento boton--grande" to={destinoAgendar()}>
                {texto('portada-boton')}
                <Icono nombre="flechaDerecha" className="icono--mover" />
              </Link>
              <a className="boton boton--transparente boton--grande" href="#servicios">{texto('portada-boton-2')}</a>
            </div>
            <ul className="portada__garantias">
              {['Cita presencial o virtual', 'Un equipo con Product Owner', 'Avance en tiempo real'].map((g) => (
                <li key={g} className="portada__garantia"><Icono nombre="exito" />{g}</li>
              ))}
            </ul>
          </div>
          {/* Así ve el cliente su proyecto (ejemplo genérico, sin datos de clientes) */}
          <div className="portada__producto">
            <figure className="vista-previa vista-previa--vidrio" style={{ margin: 0 }} aria-label="Ejemplo de seguimiento de un proyecto">
              <div className="vista-previa__encabezado">
                <span className="vista-previa__titulo">Tienda en línea</span>
                <span className="vista-previa__fase">40 % del proyecto</span>
              </div>
              <ol className="lista-fases">
                <li className="lista-fases__item lista-fases__item--completa">
                  <span className="lista-fases__punto"><Icono nombre="exito" /></span>Toma de requerimientos <span className="solo-lector">(completa)</span>
                </li>
                <li className="lista-fases__item lista-fases__item--completa">
                  <span className="lista-fases__punto"><Icono nombre="exito" /></span>Análisis y diseño <span className="solo-lector">(completa)</span>
                </li>
                <li className="lista-fases__item lista-fases__item--actual">
                  <span className="lista-fases__punto" />Desarrollo <span className="solo-lector">(fase actual)</span>
                  <span className="lista-fases__porcentaje">3 de 5 pasos</span>
                  <span className="barra-progreso lista-fases__barra esta-lleno" style={{ ['--valor' as string]: '60%' }} role="progressbar"
                        aria-valuenow={3} aria-valuemin={0} aria-valuemax={5} aria-label="Pasos hechos de la fase Desarrollo">
                    <span className="barra-progreso__relleno" />
                  </span>
                </li>
                <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Pruebas <span className="solo-lector">(pendiente)</span></li>
                <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Despliegue <span className="solo-lector">(pendiente)</span></li>
              </ol>
            </figure>
            <div className="portada__aviso" aria-hidden="true">
              <span className="portada__aviso-icono"><Icono nombre="exito" /></span>
              <span><strong>Paso hecho ✓</strong><small>Carrito y pagos en línea</small></span>
            </div>
          </div>
        </div>
        <div className="contenedor">
          <ul className="cifras" aria-label="CoreTech en cifras">
            <li className="cifras__item"><span className="cifras__valor">{servicios?.length ?? '–'}</span><span className="cifras__texto">servicios para crear tu sistema</span></li>
            <li className="cifras__item"><span className="cifras__valor">2–8</span><span className="cifras__texto">fases según tu proyecto, todas visibles para ti</span></li>
            <li className="cifras__item"><span className="cifras__valor">1</span><span className="cifras__texto">equipo por proyecto</span></li>
            <li className="cifras__item"><span className="cifras__valor">2</span><span className="cifras__texto">modalidades: presencial o virtual</span></li>
          </ul>
        </div>
      </section>

      {/* ===================== Servicios ===================== */}
      <section className="seccion" id="servicios" aria-labelledby="titulo-servicios">
        <div className="contenedor">
          <div className="seccion__encabezado">
            <span className="antetitulo">Catálogo</span>
            <h2 id="titulo-servicios">{texto('servicios-titulo')}</h2>
            <p>{texto('servicios-texto')}</p>
          </div>
          {servicios === null && <Cargando texto="Cargando servicios…" />}
          {servicios !== null && servicios.length === 0 && (
            // HU-07: sin servicios visibles se invita a escribir
            <div className="sin-resultados">
              <h3 className="sin-resultados__titulo">Pronto publicaremos nuestros servicios</h3>
              <p>Mientras tanto, cuéntanos qué necesitas: <a className="enlace" href={`mailto:${correo}`}>{correo}</a> o <a className="enlace" href={enlaceTelefono(telefono)}>{telefono}</a>.</p>
            </div>
          )}
          {servicios !== null && servicios.length > 0 && (
            <Carrusel
              etiqueta="Servicios de CoreTech"
              diapositivas={servicios.map((s, i) => (
                <TarjetaServicio
                  key={s.servicioId}
                  nombre={s.nombre}
                  descripcion={s.descripcionCorta}
                  imagenUrl={s.imagenUrl}
                  imagenAlt={s.imagenAlt}
                  carga={i < 3 ? 'eager' : 'lazy'}
                  pie={
                    <Link className="boton boton--contorno boton--bloque" to={destinoAgendar(s.slug)} aria-label={`Agendar cita para ${s.nombre}`}>
                      Agendar cita
                    </Link>
                  }
                />
              ))}
            />
          )}
        </div>
      </section>

      {/* ===================== Proceso ===================== */}
      <section className="seccion seccion--suave" id="proceso" aria-labelledby="titulo-proceso">
        <div className="contenedor">
          <div className="seccion__encabezado">
            <span className="antetitulo">Proceso</span>
            <h2 id="titulo-proceso">{texto('proceso-titulo')}</h2>
            <p>{texto('proceso-texto')}</p>
          </div>
          <div className="pasos-contenedor">
            <ol className="pasos">
              {PASOS.map(({ clave, icono }) => (
                <li key={clave} className="paso">
                  <span className="paso__icono"><Icono nombre={icono} /></span>
                  <h3 className="paso__titulo">{texto(clave)}</h3>
                  <p className="paso__texto">{texto(`${clave}-texto`)}</p>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      {/* ===================== Contacto ===================== */}
      <section className="seccion" id="contacto" aria-labelledby="titulo-contacto">
        <div className="contenedor contacto">
          <div>
            <span className="antetitulo">Contacto</span>
            <h2 id="titulo-contacto">{texto('contacto-titulo')}</h2>
            <p>{texto('contacto-texto')}</p>
          </div>
          <div>
            <address>
              <ul className="lista-contacto">
                <li className="lista-contacto__item">
                  <span className="lista-contacto__icono"><Icono nombre="ubicacion" /></span>
                  <div><span className="lista-contacto__dato">Oficina</span><span className="lista-contacto__valor">{texto('contacto-direccion')}</span></div>
                </li>
                <li className="lista-contacto__item lista-contacto__item--ancho">
                  <span className="lista-contacto__icono"><Icono nombre="reloj" /></span>
                  <div><span className="lista-contacto__dato">Horario de atención</span><span className="lista-contacto__valor">{texto('contacto-horario')}</span></div>
                </li>
                <li className="lista-contacto__item">
                  <span className="lista-contacto__icono"><Icono nombre="telefono" /></span>
                  <div><span className="lista-contacto__dato">Teléfono</span><a className="lista-contacto__valor" href={enlaceTelefono(telefono)}>{telefono}</a></div>
                </li>
                <li className="lista-contacto__item lista-contacto__item--ancho">
                  <span className="lista-contacto__icono"><Icono nombre="correo" /></span>
                  <div><span className="lista-contacto__dato">Correo</span><a className="lista-contacto__valor" href={`mailto:${correo}`}>{correo}</a></div>
                </li>
              </ul>
            </address>
          </div>
        </div>
        <div className="contenedor" style={{ marginTop: 'var(--espacio-12)' }}>
          <div className="franja-llamado superficie-noche">
            <div>
              <h3 className="franja-llamado__titulo">{texto('llamado-titulo')}</h3>
              <p className="franja-llamado__texto">{texto('llamado-texto')}</p>
            </div>
            <div className="franja-llamado__acciones">
              <Link className="boton boton--claro boton--grande" to={destinoAgendar()}>
                Agendar cita
                <Icono nombre="flechaDerecha" className="icono--mover" />
              </Link>
              <Link className="boton boton--transparente boton--grande" to="/ingresar">Ya tengo cuenta</Link>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
