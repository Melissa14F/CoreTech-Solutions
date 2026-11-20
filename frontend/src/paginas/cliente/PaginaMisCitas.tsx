import { useEffect, useMemo, useState } from 'react';

import { citas, type CitaPropia } from '../../agenda/api';
import { bloqueFecha, soloFecha, textoDia, textoHoraColombia, textoRango } from '../../agenda/fechas';
import { COLOMBIA } from '../../agenda/paises';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton, EnlaceBoton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { Icono } from '../../componentes/Icono';
import { Insignia, type VarianteInsignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import '../../styles/vistas/mis-citas.css';
import '../../styles/vistas/agenda.css';

type Filtro = 'todas' | 'proximas' | 'pasadas' | 'canceladas';
type Grupo = Exclude<Filtro, 'todas'>;

const GRUPO_DE: Record<CitaPropia['estado'], Grupo> = {
  PENDIENTE: 'proximas', CONFIRMADA: 'proximas', ATENDIDA: 'pasadas', CANCELADA: 'canceladas',
};
const TITULO_GRUPO: Record<Grupo, string> = { proximas: 'Próximas', pasadas: 'Atendidas', canceladas: 'Canceladas' };
const FILTROS: [Filtro, string][] = [['todas', 'Todas'], ['proximas', 'Próximas'], ['pasadas', 'Atendidas'], ['canceladas', 'Canceladas']];
const VACIO: Record<Grupo, { titulo: string; texto: string }> = {
  proximas: { titulo: 'No tienes citas próximas', texto: 'Agenda una cita para reunirte con nuestro equipo.' },
  pasadas: { titulo: 'No tienes citas atendidas', texto: 'Cuando se atienda una cita aparecerá aquí.' },
  canceladas: { titulo: 'No tienes citas canceladas', texto: 'Cuando canceles una cita aparecerá aquí.' },
};
const INSIGNIA: Record<CitaPropia['estado'], [VarianteInsignia, string]> = {
  PENDIENTE: ['pendiente', 'Pendiente'], CONFIRMADA: ['confirmada', 'Confirmada'],
  ATENDIDA: ['atendida', 'Atendida'], CANCELADA: ['cancelada', 'Cancelada'],
};

function Dato({ icono, children }: { icono: 'reloj' | 'ubicacion' | 'persona' | 'informacion'; children: string }) {
  return <span className="tarjeta-cita__dato"><Icono nombre={icono} />{children}</span>;
}

function TarjetaCita({ cita, alCancelar }: { cita: CitaPropia; alCancelar: () => void }) {
  const dia = soloFecha(cita.fechaHoraLocal);
  const fecha = bloqueFecha(dia);
  const fueraDeColombia = cita.zonaHoraria !== COLOMBIA;
  const [variante, etiqueta] = INSIGNIA[cita.estado];
  const virtual = cita.modalidad === 'VIRTUAL';

  let nota: string | null = null;
  if (cita.estado === 'PENDIENTE') {
    nota = virtual
      ? 'Te avisaremos cuando el Product Owner de tu equipo confirme la cita y te envíe el enlace de la videollamada.'
      : 'Te avisaremos cuando el Product Owner de tu equipo confirme la cita.';
  } else if (cita.estado === 'CONFIRMADA') {
    nota = virtual && !cita.enlaceReunion
      ? 'Tu cita está confirmada. Te enviaremos el enlace de la videollamada.'
      : 'Tu cita está confirmada. Si necesitas cambiarla, escríbenos para reprogramarla.';
  } else if (cita.estado === 'CANCELADA' && cita.motivoCancelacion) {
    nota = `Motivo: ${cita.motivoCancelacion}`;
  }

  return (
    <li
      className={['tarjeta-cita', cita.estado === 'ATENDIDA' && 'tarjeta-cita--pasada', cita.estado === 'CANCELADA' && 'tarjeta-cita--pasada'].filter(Boolean).join(' ')}
      id={`cita-${cita.codigo}`}
    >
      <div className="tarjeta-cita__fecha" aria-hidden="true">
        <span className="tarjeta-cita__dia-semana">{fecha.diaSemana}</span>
        <span className="tarjeta-cita__dia">{fecha.dia}</span>
        <span className="tarjeta-cita__mes">{fecha.mes}</span>
      </div>
      <div>
        <div className="tarjeta-cita__cabecera">
          <h3 className="tarjeta-cita__titulo">{cita.servicio}</h3>
          <Insignia variante={variante}>{etiqueta}</Insignia>
          <span className="tarjeta-cita__codigo">{cita.codigo}</span>
        </div>
        <div className="tarjeta-cita__datos">
          <Dato icono="reloj">{`${textoDia(dia)} · ${textoRango(cita.fechaHoraLocal)}`}</Dato>
          {fueraDeColombia && (
            <Dato icono="informacion">{`En Colombia: ${textoHoraColombia(cita.fechaHoraLocal, cita.fechaHoraColombia, true)}`}</Dato>
          )}
          <Dato icono="ubicacion">{virtual ? 'Virtual' : 'Presencial'}</Dato>
          {cita.estado === 'PENDIENTE' && <Dato icono="persona">Equipo por asignar</Dato>}
        </div>
        {nota && <p className={['tarjeta-cita__nota', cita.estado === 'CANCELADA' && 'tarjeta-cita__nota--motivo'].filter(Boolean).join(' ')}>{nota}</p>}
      </div>
      <div className="tarjeta-cita__acciones">
        {cita.estado === 'CONFIRMADA' && virtual && cita.enlaceReunion && (
          <a className="boton boton--acento boton--pequeno" href={cita.enlaceReunion} target="_blank" rel="noopener noreferrer">
            Unirse a la reunión
          </a>
        )}
        {cita.puedeCancelar && (
          <Boton variante="contorno" tamano="pequeno" icono="cerrar" onClick={alCancelar} aria-label={`Cancelar la cita ${cita.codigo}`}>
            Cancelar cita
          </Boton>
        )}
      </div>
    </li>
  );
}

/** Consultar y cancelar mis citas (HU-08, RN-11; prototipo/cliente/mis-citas.html). */
export function PaginaMisCitas() {
  useTituloDocumento('Mis citas');
  const notificar = useNotificar();
  const [lista, setLista] = useState<CitaPropia[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [intento, setIntento] = useState(0);
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [porCancelar, setPorCancelar] = useState<CitaPropia | null>(null);
  const [cancelando, setCancelando] = useState(false);

  useEffect(() => {
    let vigente = true;
    setErrorCarga(null);
    citas.mias()
      .then((datos) => vigente && setLista(datos))
      .catch((e: unknown) => vigente && setErrorCarga(e instanceof ErrorApi ? e.message : 'No pudimos cargar tus citas.'));
    return () => {
      vigente = false;
    };
  }, [intento]);

  const grupos = useMemo(() => {
    const resultado: Record<Grupo, CitaPropia[]> = { proximas: [], pasadas: [], canceladas: [] };
    for (const c of lista ?? []) resultado[GRUPO_DE[c.estado]].push(c);
    // Las próximas, de la más cercana a la más lejana; las demás, de la más reciente a la más antigua
    resultado.proximas.sort((a, b) => new Date(a.fechaHoraLocal).getTime() - new Date(b.fechaHoraLocal).getTime());
    resultado.pasadas.sort((a, b) => new Date(b.fechaHoraLocal).getTime() - new Date(a.fechaHoraLocal).getTime());
    resultado.canceladas.sort((a, b) => new Date(b.fechaHoraLocal).getTime() - new Date(a.fechaHoraLocal).getTime());
    return resultado;
  }, [lista]);

  async function cancelar() {
    if (!porCancelar) return;
    const cita = porCancelar;
    setCancelando(true);
    try {
      await citas.cancelar(cita.citaId);
      setLista((previo) => previo?.map((c) => (c.citaId === cita.citaId ? { ...c, estado: 'CANCELADA', puedeCancelar: false } : c)) ?? previo);
      notificar(`Cancelaste la cita ${cita.codigo}. La franja quedó libre.`);
    } catch (e) {
      // Ya la confirmaron, ya no existe…: se avisa con el texto de la API y se recarga lo último
      notificar(e instanceof ErrorApi ? e.message : 'No pudimos cancelar la cita. Intenta de nuevo.', 'error');
      setIntento((n) => n + 1);
    } finally {
      setCancelando(false);
      setPorCancelar(null);
    }
  }

  const botonAgendar = <EnlaceBoton a="/cliente/agendar" variante="acento" icono="mas">Agendar cita</EnlaceBoton>;

  const encabezado = (
    <div className="encabezado-vista">
      <div>
        <h1 className="encabezado-vista__titulo">Mis citas</h1>
        <p className="encabezado-vista__texto">Tus citas con CoreTech y las reuniones de seguimiento de tus proyectos.</p>
      </div>
      {botonAgendar}
    </div>
  );

  if (errorCarga && !lista) {
    return (
      <>
        {encabezado}
        <Alerta tipo="error" titulo="No pudimos cargar tus citas">{errorCarga}</Alerta>
        <p><Boton variante="contorno" onClick={() => setIntento((n) => n + 1)}>Reintentar</Boton></p>
      </>
    );
  }
  if (!lista) return <>{encabezado}<Cargando texto="Cargando tus citas…" /></>;

  const visibles: Grupo[] = filtro === 'todas' ? (['proximas', 'pasadas', 'canceladas'] as Grupo[]).filter((g) => grupos[g].length > 0) : [filtro];

  return (
    <>
      {encabezado}

      {lista.length === 0 ? (
        <EstadoVacio icono="calendario" titulo="Aún no tienes citas" texto="Agenda tu primera cita para contarnos qué necesitas." accion={botonAgendar} />
      ) : (
        <>
          <div className="filtros" role="group" aria-label="Filtrar citas">
            {FILTROS.map(([id, texto]) => {
              const cantidad = id === 'todas' ? lista.length : grupos[id].length;
              return (
                <button key={id} type="button" className="filtro" aria-pressed={filtro === id} onClick={() => setFiltro(id)}>
                  {texto} <span className="filtro__cantidad">{cantidad}</span>
                </button>
              );
            })}
          </div>

          {visibles.map((grupo) => (
            <section className="grupo-citas" key={grupo} aria-labelledby={`titulo-${grupo}`}>
              <h2 className="grupo-citas__titulo" id={`titulo-${grupo}`}>{TITULO_GRUPO[grupo]}</h2>
              {grupos[grupo].length === 0 ? (
                <EstadoVacio icono="calendario" titulo={VACIO[grupo].titulo} texto={VACIO[grupo].texto} />
              ) : (
                <ul className="lista-citas">
                  {grupos[grupo].map((c) => <TarjetaCita key={c.citaId} cita={c} alCancelar={() => setPorCancelar(c)} />)}
                </ul>
              )}
            </section>
          ))}
        </>
      )}

      <Dialogo
        abierto={porCancelar !== null}
        alCerrar={() => !cancelando && setPorCancelar(null)}
        icono="papelera"
        tono="peligro"
        titulo={`¿Cancelar la cita ${porCancelar?.codigo ?? ''}?`}
        texto={porCancelar
          ? `${textoDia(soloFecha(porCancelar.fechaHoraLocal))}, ${textoRango(porCancelar.fechaHoraLocal).split(' a ')[0]} La franja quedará libre para otros clientes y podrás agendar una cita nueva.`
          : ''}
        acciones={(
          <>
            <Boton variante="contorno" onClick={() => setPorCancelar(null)} disabled={cancelando}>Mantener cita</Boton>
            <Boton variante="peligro" onClick={cancelar} cargando={cancelando}>Sí, cancelar</Boton>
          </>
        )}
      />
    </>
  );
}
