package co.coretech.portal.compartido.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe la respuesta de error {@code { "errores": [...] }} desde los filtros de seguridad, que
 * actúan antes de los controladores y por eso no pasan por {@link ManejadorGlobalErrores}.
 */
@Component
class EscritorErrores {

    private final ObjectMapper json;

    EscritorErrores(ObjectMapper json) {
        this.json = json;
    }

    void escribir(HttpServletResponse respuesta, HttpStatus estado, String mensaje) throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), RespuestaError.de(mensaje));
    }
}
