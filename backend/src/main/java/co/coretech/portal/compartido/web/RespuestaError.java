package co.coretech.portal.compartido.web;

import java.util.List;

/**
 * Cuerpo de toda respuesta de error de la API: {@code { "errores": [{ "campo", "mensaje" }] }}
 * (docs/04-api.md). Los mensajes son los mismos que muestra el prototipo bajo cada campo.
 */
public record RespuestaError(List<ErrorCampo> errores) {

    public RespuestaError {
        errores = List.copyOf(errores);
    }

    public static RespuestaError de(String campo, String mensaje) {
        return new RespuestaError(List.of(new ErrorCampo(campo, mensaje)));
    }

    public static RespuestaError de(String mensaje) {
        return de(null, mensaje);
    }
}
