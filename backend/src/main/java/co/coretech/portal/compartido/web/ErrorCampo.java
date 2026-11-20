package co.coretech.portal.compartido.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Un error de la respuesta: el campo del formulario (si lo hay) y el mensaje para el usuario.
 * Sin campo, el JSON no lleva la propiedad {@code campo}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorCampo(String campo, String mensaje) {

    public static ErrorCampo general(String mensaje) {
        return new ErrorCampo(null, mensaje);
    }
}
