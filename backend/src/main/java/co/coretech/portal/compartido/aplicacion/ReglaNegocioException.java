package co.coretech.portal.compartido.aplicacion;

import java.util.Optional;

/**
 * Los datos son inválidos o violan una regla fija (por ejemplo, la entrega a menos de 14 días del
 * inicio). → 422, con el campo del formulario cuando la regla se refiere a uno.
 */
public class ReglaNegocioException extends ExcepcionNegocio {

    private final String campo;

    public ReglaNegocioException(String mensaje) {
        this(null, mensaje);
    }

    public ReglaNegocioException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    /** Nombre del campo como lo envía la API (camelCase), si la regla se refiere a uno. */
    public Optional<String> campo() {
        return Optional.ofNullable(campo);
    }
}
