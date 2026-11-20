package co.coretech.portal.compartido.aplicacion;

import java.util.Optional;

/**
 * El estado actual del recurso impide la acción, aunque podría hacerse en otro momento (cita no atendida,
 * fase no en revisión, correcciones abiertas, cupo lleno). → 409, con el campo del formulario cuando el
 * conflicto se refiere a uno (por ejemplo, el correo repetido va bajo el campo {@code email}).
 */
public class ConflictoException extends ExcepcionNegocio {

    private final String campo;

    public ConflictoException(String mensaje) {
        this(null, mensaje);
    }

    public ConflictoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    /** Nombre del campo como lo envía la API (camelCase), si el conflicto se refiere a uno. */
    public Optional<String> campo() {
        return Optional.ofNullable(campo);
    }
}
