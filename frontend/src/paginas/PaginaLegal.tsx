import { Link } from 'react-router-dom';

import { useContenido } from '../catalogo/ContenidoSitio';
import { Icono } from '../componentes/Icono';
import { useTituloDocumento } from '../componentes/useTituloDocumento';
import '../styles/vistas/legal.css';

/**
 * Avisos legales (prototipo/legal.html): política de tratamiento de datos (Ley 1581 de 2012) y términos.
 * Es la política que el cliente acepta al registrarse (casilla con enlace a /legal#datos).
 * <p>
 * D-53 (2026-11-20): la política dice que todos los mensajes del servicio llegan al correo con el que la persona se
 * registró y que los correos salen por Gmail (Google, encargado del tratamiento). No hace falta una casilla aparte
 * para esos correos: son necesarios para prestar el servicio y ya son una finalidad aceptada. Una casilla aparte solo
 * sería obligatoria para publicidad, que el portal no envía.
 */
export function PaginaLegal() {
  useTituloDocumento('Avisos legales');
  const { texto } = useContenido();
  const correo = texto('contacto-correo');

  return (
    <>
      <section className="legal-portada superficie-noche" aria-labelledby="titulo-legal">
        <div className="contenedor">
          <span className="pildora aparecer">Avisos legales</span>
          <h1 className="legal-portada__titulo aparecer" id="titulo-legal" style={{ ['--retraso' as string]: '80ms' }}>
            Tus datos y las reglas del servicio
          </h1>
          <p className="legal-portada__texto aparecer" style={{ ['--retraso' as string]: '160ms' }}>
            Aquí explicamos cómo usamos tu información y qué condiciones aplican al agendar citas y hacer seguimiento a tu proyecto.
          </p>
          <p className="legal-portada__fecha aparecer" style={{ ['--retraso' as string]: '220ms' }}>
            Última actualización: 20 de noviembre de 2026
          </p>
        </div>
      </section>

      <div className="contenedor legal">
        <nav className="legal__indice" aria-label="Contenido de la página">
          <p className="legal__indice-titulo">En esta página</p>
          <ol>
            <li><a href="#datos">Tratamiento de datos personales</a></li>
            <li><a href="#terminos">Términos y condiciones</a></li>
          </ol>
          <Link className="boton boton--contorno boton--pequeno" to="/">
            <Icono nombre="chevronIzquierda" />
            Volver al inicio
          </Link>
        </nav>

        <div className="legal__contenido">
          <aside className="alerta alerta--info" role="note">
            <Icono nombre="informacion" />
            <div><strong>Texto de ejemplo para el prototipo.</strong><p>Antes de publicar el sitio, este contenido debe revisarlo un asesor legal.</p></div>
          </aside>

          <section className="legal__seccion" id="datos" aria-labelledby="titulo-datos">
            <h2 id="titulo-datos">Política de tratamiento de datos personales</h2>
            <p>CoreTech Solutions trata los datos personales de sus clientes conforme a la Ley 1581 de 2012 y el Decreto 1377 de 2013.</p>

            <h3>Qué datos recogemos</h3>
            <ul className="legal__lista">
              <li>Nombre completo, correo electrónico, número de celular y, si lo indicas, el nombre de tu empresa.</li>
              <li>Si te registras o ingresas con Google o Microsoft, recibimos de ellos tu nombre y tu correo electrónico; no accedemos a nada más de tu cuenta.</li>
              <li>Las citas que agendas, su modalidad (presencial o virtual) y su estado.</li>
              <li>Los proyectos asociados a tu cuenta, sus avances y las solicitudes de reunión que envías.</li>
            </ul>

            <h3>Para qué los usamos</h3>
            <ul className="legal__lista">
              <li>Crear y administrar tu cuenta de cliente.</li>
              <li>Agendar, confirmar y gestionar tus citas, y enviarte la confirmación por correo.</li>
              <li>Mostrarte el avance de tu proyecto y avisarte cuando haya novedades.</li>
              <li>Escribirte al correo con el que te registraste los mensajes del servicio, descritos abajo.</li>
              <li>Mantener un registro de cambios para garantizar la trazabilidad de la información.</li>
            </ul>

            <h3>Correos que te enviamos</h3>
            <p>
              Al aceptar esta política autorizas que te escribamos al correo electrónico con el que creaste tu cuenta,
              además de avisarte en la campana del portal. Solo te enviamos mensajes del servicio: la bienvenida, el enlace
              para restablecer tu contraseña, la confirmación y los cambios de tus citas, tu plan de trabajo y su
              cotización, el avance de tu proyecto y los mensajes de tu equipo.
            </p>
            <p>
              Estos correos son necesarios para prestarte el servicio, por eso no se pueden desactivar mientras tengas la
              cuenta; si ya no quieres recibirlos, puedes pedir que la cerremos. No usamos tu correo para publicidad: si
              algún día quisiéramos enviarte información comercial, te pediríamos una autorización aparte, que podrás negar.
            </p>

            <h3>Quién puede ver tu información</h3>
            <p>Solo tú, el equipo asignado a tu proyecto y la administración de CoreTech. Ningún otro cliente puede ver tus citas ni tus proyectos.</p>
            <p>
              Para enviar los correos usamos Gmail, de Google, que actúa como encargado del tratamiento: recibe tu nombre,
              tu correo y el contenido del mensaje solo para entregarlo, y puede procesarlos en servidores fuera de Colombia.
            </p>

            <h3>Tus derechos</h3>
            <p>
              Puedes conocer, actualizar, rectificar y solicitar la eliminación de tus datos, así como revocar la autorización
              que nos diste. Para hacerlo, escríbenos a <a className="enlace" href={`mailto:${correo}`}>{correo}</a>.
            </p>
          </section>

          <section className="legal__seccion" id="terminos" aria-labelledby="titulo-terminos">
            <h2 id="titulo-terminos">Términos y condiciones</h2>

            <h3>Cuenta</h3>
            <p>Para agendar una cita necesitas una cuenta de cliente. Eres responsable de mantener tu contraseña en reserva.</p>

            <h3>Citas</h3>
            <ul className="legal__lista">
              <li>Las citas se agendan de lunes a viernes, de 8:00 a. m. a 12:00 m. y de 2:00 a 6:00 p. m., en franjas de una hora.</li>
              <li>Se agendan con mínimo 24 horas de anticipación y máximo 30 días hacia adelante.</li>
              <li>Puedes tener varias citas pendientes, por ejemplo una por cada proyecto que quieras desarrollar, pero no dos a la misma hora. Puedes cancelar cualquiera mientras siga pendiente.</li>
            </ul>

            <h3>Plan de trabajo y cotización</h3>
            <p>Después de la cita, CoreTech crea tu proyecto, le asigna un equipo y te envía un plan de trabajo con sus fases (de 2 a 8) y la cotización: valor en pesos colombianos, fecha estimada de entrega y qué incluye. Los pagos se acuerdan fuera del portal.</p>
            <ul className="legal__lista">
              <li>Tienes 3 días hábiles para aprobar el plan o pedir ajustes. Si no respondes, te lo recordamos; a los 10 días hábiles la propuesta vence y el equipo queda libre para otros proyectos. Puedes pedir que te la envíen de nuevo.</li>
              <li>Al aprobar el plan, queda fijo. Para agregar una fase, quitar una que no ha empezado o cambiar la fecha de entrega, el equipo te envía la propuesta con el motivo, la nueva fecha y el valor adicional, y solo se aplica si la aceptas. Las fases que ya aprobaste no cambian.</li>
            </ul>

            <h3>Seguimiento del proyecto</h3>
            <p>Su Product Owner registra el avance y es tu contacto. Todas las fases valen lo mismo: el avance sube cuando se completa una. Lo publicado no se modifica; si hay una corrección, se registra como un paso nuevo.</p>
            <ul className="legal__lista">
              <li>Tú apruebas las fases que el plan marca para ti; las demás las cierra el equipo y te muestra lo hecho.</li>
              <li>Tienes 3 días hábiles para aprobar una fase o pedir cambios. Si no respondes, te recordamos y la fase se da por aprobada para no detener tu proyecto.</li>
              <li>Cada fase que apruebas incluye 2 rondas de ajustes sin costo. Los ajustes adicionales se cotizan aparte.</li>
              <li>Lo que no cumpla con lo acordado lo corregimos siempre, sin costo. Las funciones nuevas que no estaban acordadas son un cambio de alcance: te enviamos el costo y cuántos días agrega antes de hacerlas.</li>
              <li>Al completar la última fase tienes 30 días de garantía: corregimos sin costo los errores de lo entregado.</li>
            </ul>

            <h3>Reuniones de seguimiento</h3>
            <p>Desde el detalle de tu proyecto puedes escribirle o solicitar una reunión al Product Owner de tu equipo. Él la programa en una de las fechas que sugieras o te responde con el motivo si no es posible.</p>
          </section>
        </div>
      </div>
    </>
  );
}
