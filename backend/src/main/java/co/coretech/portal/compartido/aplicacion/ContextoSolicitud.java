package co.coretech.portal.compartido.aplicacion;

import java.util.Optional;
import java.util.UUID;

/**
 * Quién hace la acción y desde dónde, para la auditoría (RN-20). Fuera de una petición HTTP (por
 * ejemplo, en una tarea programada) no hay usuario, IP ni navegador: todo viene vacío.
 */
public interface ContextoSolicitud {

    /** Id del usuario con sesión; vacío si no hay sesión o la acción la hace el sistema. */
    Optional<UUID> usuarioId();

    Optional<String> ip();

    /** Navegador y sistema (encabezado User-Agent). */
    Optional<String> agente();
}
