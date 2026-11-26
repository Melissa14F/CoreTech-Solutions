package co.coretech.portal.acceso.dto;

import java.util.List;
import java.util.UUID;

import co.coretech.portal.acceso.service.ServicioUsuarios;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpos de las peticiones y respuestas de /api/v1/auth y /api/v1/usuarios (docs/04-api.md, flujo 2).
 * Los mensajes son los del prototipo (registro.html, login.html, cambiar-contrasena.html).
 * La política de contraseña (RN-22) la valida el servicio, no estas anotaciones.
 */
public final class DtoAcceso {

    private DtoAcceso() {
    }

    public static final String CORREO_INVALIDO = "Escribe un correo válido, por ejemplo nombre@empresa.com.";

    public record SolicitudRegistro(
            @NotBlank(message = "Escribe tu nombre completo.")
            @Size(min = 3, max = 60, message = "Tu nombre debe tener entre 3 y 60 caracteres.")
            @Pattern(regexp = "^[\\p{L} ]+$", message = "Escribe solo letras y espacios.")
            String nombre,

            @NotBlank(message = "Escribe tu correo; ahí te enviaremos la confirmación de la cita.")
            @Email(regexp = ".+@.+\\..+", message = CORREO_INVALIDO)
            @Size(max = 120, message = "Escribe máximo 120 caracteres.")
            String email,

            @NotBlank(message = "Escribe tu número de celular.")
            @Pattern(regexp = "^3\\d{9}$", message = "Escribe 10 dígitos que empiecen por 3.")
            String celular,

            @Size(max = 80, message = "Escribe máximo 80 caracteres.")
            String empresa,

            @NotBlank(message = "Crea una contraseña.")
            String password,

            @NotBlank(message = "Escribe de nuevo la contraseña.")
            String confirmacion,

            @NotNull(message = "Debes aceptar la política de datos para crear la cuenta.")
            @AssertTrue(message = "Debes aceptar la política de datos para crear la cuenta.")
            Boolean aceptaDatos) {

        /** Sin contraseñas: Spring escribe el cuerpo de la petición en el log cuando está en DEBUG (RNF-10). */
        @Override
        public String toString() {
            return "SolicitudRegistro[email=" + email + "]";
        }
    }

    public record RespuestaRegistro(UUID usuarioId, String rol, String nombre) {
    }

    public record SolicitudLogin(
            @NotBlank(message = "Escribe el correo con el que creaste tu cuenta.") String email,
            @NotBlank(message = "Escribe tu contraseña.") String password,
            boolean recordarme) {

        @Override
        public String toString() {
            return "SolicitudLogin[email=" + email + "]";
        }
    }

    public record RespuestaIngreso(String rol, String nombre, boolean debeCambiarPassword) {
    }

    public record RespuestaYo(UUID usuarioId, String nombre, String email, String rol, List<String> permisos,
            boolean debeCambiarPassword) {
    }

    public record SolicitudCambioPassword(
            @NotBlank(message = "Escribe tu contraseña actual.") String passwordActual,
            @NotBlank(message = "Crea tu nueva contraseña.") String passwordNueva,
            @NotBlank(message = "Escribe de nuevo la nueva contraseña.") String confirmacion) {

        @Override
        public String toString() {
            return "SolicitudCambioPassword[...]";
        }
    }

    public record SolicitudRecuperacion(
            @NotBlank(message = "Escribe el correo con el que creaste tu cuenta.")
            @Email(regexp = ".+@.+\\..+", message = CORREO_INVALIDO)
            String email) {
    }

    public record SolicitudRestablecer(
            @NotBlank(message = "Este enlace ya no es válido, solicita uno nuevo") String token,
            @NotBlank(message = "Crea tu nueva contraseña.") String passwordNueva,
            @NotBlank(message = "Escribe de nuevo la nueva contraseña.") String confirmacion) {

        /** También oculta el token del enlace: con él se cambia la contraseña de la cuenta. */
        @Override
        public String toString() {
            return "SolicitudRestablecer[...]";
        }
    }

    public record RespuestaRecuperacionAdmin(String enviadoA, long expiraEnMinutos) {
    }

    /** Crear o editar una cuenta (HU-20). Las reglas dependen del rol: las valida ServicioUsuarios. */
    public record SolicitudCuenta(String nombre, String celular, String email) {

        public ServicioUsuarios.DatosCuenta aDatos() {
            return new ServicioUsuarios.DatosCuenta(nombre, celular, email);
        }
    }

    public record SolicitudActivo(@NotNull(message = "Indica si la cuenta queda activa.") Boolean activo) {
    }

    /** Guardar la matriz de Roles y permisos (HU-21). */
    public record SolicitudPermisos(
            @NotNull(message = "No hay cambios por guardar.") List<@Valid @NotNull CambioPermiso> cambios) {
    }

    public record CambioPermiso(
            @NotBlank(message = "Ese rol o ese permiso no existe") String rol,
            @NotBlank(message = "Ese rol o ese permiso no existe") String permiso,
            @NotNull(message = "Indica si el permiso queda activo.") Boolean permitido) {
    }

    public record RespuestaMensaje(String mensaje) {
    }
}
