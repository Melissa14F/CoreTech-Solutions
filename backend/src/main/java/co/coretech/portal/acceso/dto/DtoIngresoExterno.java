package co.coretech.portal.acceso.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpos de las peticiones y respuestas de {@link ControladorIngresoExterno}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoIngresoExterno {

    private DtoIngresoExterno() {
    }

    public record Proveedor(String codigo, String nombre, String url) {
    }

    public record SolicitudCompletar(
            @NotBlank(message = "Escribe tu nombre completo.")
            @Size(min = 3, max = 60, message = "Tu nombre debe tener entre 3 y 60 caracteres.")
            @Pattern(regexp = "^[\\p{L} ]+$", message = "Escribe solo letras y espacios.")
            String nombre,

            @NotBlank(message = "Escribe tu número de celular.")
            @Pattern(regexp = "^3\\d{9}$", message = "Escribe 10 dígitos que empiecen por 3.")
            String celular,

            @Size(max = 80, message = "Escribe máximo 80 caracteres.")
            String empresa,

            Boolean aceptaDatos) {
    }

    public record RespuestaCompletar(UUID usuarioId, String rol, String nombre) {
    }
}
