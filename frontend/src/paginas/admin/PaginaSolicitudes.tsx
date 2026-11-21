import { Alerta } from '../../componentes/Alerta';
import { GestionSolicitudes } from '../../componentes/GestionSolicitudes';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import '../../styles/vistas/admin-solicitudes.css';

/**
 * Solicitudes de reunión de seguimiento (HU-16; prototipo: admin/solicitudes.html). El administrador ve todas, y puede
 * programarlas, rechazarlas o cancelarlas en nombre del responsable; el resumen de la reunión lo llena el Product Owner.
 */
export function PaginaSolicitudes() {
  useTituloDocumento('Solicitudes de reunión');
  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Solicitudes de reunión</h1>
          <p className="encabezado-vista__texto">
            Las reuniones que piden los clientes desde sus proyectos. Normalmente las responde el Product Owner del equipo; aquí puedes hacerlo tú si hace falta.
          </p>
        </div>
      </div>
      <Alerta tipo="info">Solo el dueño de un proyecto activo puede pedir una reunión, y solo una a la vez por proyecto (RN-14 y RN-15).</Alerta>
      <GestionSolicitudes modo="admin" />
    </>
  );
}
