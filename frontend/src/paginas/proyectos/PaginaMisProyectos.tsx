import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { Alerta } from '../../componentes/Alerta';
import { EnlaceBoton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { EstadoVacio } from '../../componentes/EstadoVacio';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { fechaLegible, pesos } from '../../proyectos/admin';
import { nombrePila } from '../../proyectos/plan';
import { conPendientesPrimero, proyectosPropios, textoDeProyecto, type ProyectoPropio, type Vista } from '../../proyectos/propios';
import { useUsuario } from '../../sesion/ContextoSesion';
import { separarErrores } from '../acceso/erroresFormulario';

const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`;

/**
 * Mis proyectos del cliente y del Product Owner (prototipo/cliente/mis-proyectos.html y desarrollador/proyectos.html),
 * en versión sencilla: una tarjeta por proyecto, lo que espera a quien mira arriba y un solo botón que lleva al plan.
 * El detalle del proyecto y registrar trabajo llegan con P18.
 */
export function PaginaMisProyectos({ vista }: { vista: Vista }) {
  useTituloDocumento('Mis proyectos');
  const usuario = useUsuario();
  const [proyectos, setProyectos] = useState<ProyectoPropio[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let vigente = true;
    proyectosPropios.listar()
      .then((lista) => vigente && setProyectos(conPendientesPrimero(lista)))
      .catch((e) => vigente && setError(separarErrores(e).general ?? 'No pudimos cargar tus proyectos.'));
    return () => { vigente = false; };
  }, []);

  const pendientes = proyectos?.filter((p) => p.porHacer).length ?? 0;
  const resumen = !proyectos || proyectos.length === 0 ? '' : vista === 'cliente'
    ? `Tienes ${plural(proyectos.length, 'proyecto', 'proyectos')}${pendientes > 0 ? ` y ${pendientes === 1 ? 'una propuesta' : `${pendientes} propuestas`} por revisar` : ''}.`
    : `Tienes ${plural(proyectos.length, 'proyecto', 'proyectos')} a tu cargo${pendientes > 0 ? `; en ${pendientes} te toca preparar o ajustar el plan` : ''}.`;

  return (
    <>
      <div className="encabezado-vista aparecer">
        <div>
          <h1 className="encabezado-vista__titulo">Mis proyectos</h1>
          <p className="encabezado-vista__texto">{resumen || `Hola, ${nombrePila(usuario?.nombre ?? '')}.`}</p>
        </div>
        {vista === 'cliente' && (
          <div className="encabezado-vista__acciones">
            <EnlaceBoton a="/cliente/citas" variante="contorno" icono="calendario">Mis citas</EnlaceBoton>
            <EnlaceBoton a="/cliente/agendar" variante="acento" icono="mas">Agendar cita</EnlaceBoton>
          </div>
        )}
      </div>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {!proyectos && !error && <Cargando texto="Cargando tus proyectos…" />}
      {proyectos?.length === 0 && (
        <EstadoVacio
          icono="carpeta"
          titulo="Todavía no tienes proyectos"
          texto={vista === 'cliente'
            ? 'Cuando agendes una cita y el equipo la atienda, tu proyecto aparecerá aquí.'
            : 'La administración te asignará un proyecto después de atender una cita.'}
        />
      )}
      {proyectos && proyectos.length > 0 && (
        <ul className="lista-proyectos" aria-label="Mis proyectos">
          {proyectos.map((p, i) => <Tarjeta key={p.codigo} proyecto={p} vista={vista} retraso={i * 60} />)}
        </ul>
      )}
    </>
  );
}

function Tarjeta({ proyecto: p, vista, retraso }: { proyecto: ProyectoPropio; vista: Vista; retraso: number }) {
  const texto = textoDeProyecto(p, vista);
  const sinEmpezar = p.estadoPlan !== 'APROBADO';
  const aviso = texto.tono === 'accion' ? 'proyecto-resumen__aviso--accion' : texto.tono === 'plan' ? 'proyecto-resumen__aviso--plan' : '';
  return (
    <li className="proyecto-resumen aparecer" style={{ ['--retraso' as string]: `${retraso}ms` }}>
      <div className="proyecto-resumen__cabecera">
        <div className="proyecto-resumen__nombre">
          <h3 className="proyecto-resumen__titulo">
            {texto.boton ? <Link to={texto.boton.a}>{p.nombre}</Link> : p.nombre}
          </h3>
          <p className="proyecto-resumen__codigo">{p.codigo} · {p.servicio}</p>
        </div>
        <span className="proyecto-resumen__porcentaje">
          {Math.round(p.avancePorcentaje)} %<small>{sinEmpezar ? 'sin empezar' : 'del proyecto'}</small>
        </span>
      </div>
      <dl className="proyecto-resumen__datos">
        <div>
          <dt>Plan de trabajo</dt>
          <dd>{plural(p.fases, 'fase', 'fases')}{p.valorCotizado !== null && <small>{pesos(p.valorCotizado)}</small>}</dd>
        </div>
        <div>
          <dt>{vista === 'cliente' ? 'Product Owner' : 'Cliente'}</dt>
          <dd>{vista === 'cliente' ? p.productOwner : p.cliente}</dd>
        </div>
        <div><dt>Entrega estimada</dt><dd>{fechaLegible(p.fechaEstimadaEntrega)}</dd></div>
      </dl>
      <div className="proyecto-resumen__pie">
        <p className="proyecto-resumen__nota"><span className={['proyecto-resumen__aviso', aviso].filter(Boolean).join(' ')}>{texto.nota}</span></p>
        {texto.boton && (
          <div className="proyecto-resumen__acciones">
            <EnlaceBoton a={texto.boton.a} variante={texto.boton.principal ? 'acento' : 'contorno'} tamano="pequeno"
                         iconoFinal={texto.boton.principal ? 'flechaDerecha' : undefined}>
              {texto.boton.texto}
            </EnlaceBoton>
          </div>
        )}
      </div>
    </li>
  );
}
