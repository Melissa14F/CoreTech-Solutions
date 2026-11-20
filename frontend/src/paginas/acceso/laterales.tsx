import { Icono } from '../../componentes/Icono';

/** Columna oscura de cada pantalla de acceso, con los textos del prototipo. */

export const LATERAL_INGRESAR = {
  etiqueta: 'Lo que encuentras en tu panel',
  pildora: 'Tu panel',
  titulo: 'Todo tu proyecto en un solo lugar',
  texto: 'Citas, avances y reuniones con tu equipo, actualizados en tiempo real.',
  contenido: (
    // Actividad de ejemplo (genérica, sin datos de clientes reales), como en prototipo/login.html
    <ul className="actividad" aria-label="Ejemplo de actividad reciente">
      <li className="actividad__item">
        <span className="actividad__icono actividad__icono--exito"><Icono nombre="exito" /></span>
        <span className="actividad__texto">Paso hecho ✓<span className="actividad__detalle">Desarrollo · Carrito y pagos en línea</span></span>
        <span className="actividad__hora">Hoy</span>
      </li>
      <li className="actividad__item">
        <span className="actividad__icono actividad__icono--acento"><Icono nombre="calendario" /></span>
        <span className="actividad__texto">Cita confirmada<span className="actividad__detalle">Martes 17/11 · 9:00 a. m. · Virtual</span></span>
        <span className="actividad__hora">Ayer</span>
      </li>
      <li className="actividad__item">
        <span className="actividad__icono actividad__icono--aviso"><Icono nombre="mensaje" /></span>
        <span className="actividad__texto">Reunión solicitada<span className="actividad__detalle">Revisar el flujo de pago</span></span>
        <span className="actividad__hora">Lun</span>
      </li>
    </ul>
  ),
};

export const LATERAL_REGISTRO = {
  etiqueta: 'Cómo funciona el agendamiento',
  pildora: 'Agendamiento',
  titulo: 'Así agendas tu cita',
  texto: 'Después de crear la cuenta pasas directo al formulario de agendamiento.',
  contenido: (
    <>
      <ol className="lista-fases vista-previa vista-previa--vidrio">
        <li className="lista-fases__item lista-fases__item--actual">
          <span className="lista-fases__punto" />Crear tu cuenta <span className="solo-lector">(paso actual)</span>
        </li>
        <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Elegir el servicio</li>
        <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Fecha, hora y modalidad</li>
        <li className="lista-fases__item lista-fases__item--pendiente"><span className="lista-fases__punto" />Confirmar la cita</li>
      </ol>
      <p className="acceso__nota">Tus datos solo los ven tú, el equipo que te asignemos y la administración de CoreTech.</p>
    </>
  ),
};

export const LATERAL_CAMBIAR = {
  etiqueta: 'Por qué cambiar la contraseña',
  pildora: 'Tu cuenta',
  titulo: 'Una contraseña que solo tú conozcas',
  texto: 'La contraseña temporal la generó el sistema y viajó por correo. Cámbiala ahora: después entrarás siempre con la tuya.',
};

/** Restablecer no tiene prototipo (D-37): usa el mismo lateral de "Cambiar contraseña" con su propio texto. */
export const LATERAL_RESTABLECER = {
  etiqueta: 'Cómo funciona el enlace',
  pildora: 'Tu cuenta',
  titulo: 'Una contraseña que solo tú conozcas',
  texto: 'El enlace del correo vale 30 minutos y sirve una sola vez. Al guardar la nueva contraseña, entras con ella.',
};

/** Correo con formato válido (mismo criterio que el prototipo). */
export const CORREO_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
export const MENSAJE_CORREO_INVALIDO = 'Escribe un correo válido, por ejemplo nombre@empresa.com.';
