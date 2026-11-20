import { useEffect, useRef } from 'react';

import { EnlaceBoton } from '../componentes/Boton';
import { Icono } from '../componentes/Icono';
import { Insignia } from '../componentes/Insignia';
import { useUsuario } from '../sesion/ContextoSesion';
import type { CitaCreada } from './api';
import { DETALLE_MODALIDAD, TEXTO_MODALIDAD, type Borrador } from './borrador';
import { soloFecha, textoDiaCompleto, textoHoraColombia, textoRango } from './fechas';
import { COLOMBIA, textoZona } from './paises';

/** Pantalla final: "Tu cita quedó registrada" (prototipo/cliente/cita-registrada.html). */
export function CitaRegistrada({ cita, borrador }: { cita: CitaCreada; borrador: Borrador }) {
  const usuario = useUsuario();
  const titulo = useRef<HTMLHeadingElement>(null);
  const { servicio, modalidad, pais, zona } = borrador;
  const fueraDeColombia = zona !== COLOMBIA;

  useEffect(() => {
    titulo.current?.focus();
  }, []);

  return (
    <div className="exito">
      <div className="exito__cabecera">
        <div className="exito__marca" aria-hidden="true"><Icono nombre="exito" /></div>
        <h1 className="exito__titulo" ref={titulo} tabIndex={-1}>Tu cita quedó registrada</h1>
        <p className="exito__texto">
          Te enviamos la confirmación a <strong>{usuario?.email}</strong>. Te avisaremos cuando el Product Owner de tu equipo confirme la cita.
        </p>
      </div>

      <section className="tarjeta comprobante" aria-labelledby="titulo-comprobante">
        <div className="comprobante__cabecera">
          <h2 className="comprobante__codigo" id="titulo-comprobante"><small>Código de la cita</small>{cita.codigo}</h2>
          <Insignia variante="pendiente">Pendiente</Insignia>
        </div>
        <dl className="comprobante__datos">
          <div><dt className="comprobante__dato">Servicio</dt><dd className="comprobante__valor">{servicio?.nombre}</dd></div>
          <div><dt className="comprobante__dato">Modalidad</dt><dd className="comprobante__valor">{modalidad && TEXTO_MODALIDAD[modalidad]}</dd></div>
          <div><dt className="comprobante__dato">Fecha</dt><dd className="comprobante__valor">{textoDiaCompleto(soloFecha(cita.fechaHoraLocal))}</dd></div>
          <div><dt className="comprobante__dato">Hora</dt><dd className="comprobante__valor">{textoRango(cita.fechaHoraLocal)}</dd></div>
          {fueraDeColombia && (
            <div className="comprobante__ancho">
              <dt className="comprobante__dato">Tu zona horaria</dt>
              <dd className="comprobante__valor">{textoZona(pais, zona)} · En Colombia: {textoHoraColombia(cita.fechaHoraLocal, cita.fechaHoraColombia, true)}</dd>
            </div>
          )}
        </dl>
        <p className="comprobante__pie">
          <Icono nombre="informacion" />
          <span>{modalidad ? DETALLE_MODALIDAD[modalidad] : ''}</span>
        </p>
      </section>

      <section className="siguientes" style={{ ['--retraso' as string]: '120ms' }} aria-labelledby="titulo-siguientes">
        <h2 className="tarjeta__titulo" id="titulo-siguientes">Qué sigue</h2>
        <ol className="siguientes__lista">
          <li className="siguientes__item"><h3 className="siguientes__titulo">Asignamos un equipo</h3><p className="siguientes__texto">La administración elige el equipo; su Product Owner atenderá tu cita.</p></li>
          <li className="siguientes__item"><h3 className="siguientes__titulo">Él confirma la cita</h3><p className="siguientes__texto">Si es virtual, te envía el enlace de la videollamada.</p></li>
          <li className="siguientes__item"><h3 className="siguientes__titulo">Se reúnen</h3><p className="siguientes__texto">Después de la reunión creamos tu proyecto y lo sigues desde tu panel.</p></li>
        </ol>
      </section>

      <div className="exito__acciones">
        <EnlaceBoton a="/cliente/citas" variante="acento" tamano="grande" iconoFinal="flechaDerecha">Ver mis citas</EnlaceBoton>
        <EnlaceBoton a="/cliente/proyectos" variante="contorno" tamano="grande">Ir a mis proyectos</EnlaceBoton>
      </div>
    </div>
  );
}
