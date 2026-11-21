package co.coretech.portal.compartido.web;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import co.coretech.portal.compartido.aplicacion.ConflictoException;
import co.coretech.portal.compartido.aplicacion.CuentaBloqueadaException;
import co.coretech.portal.compartido.aplicacion.ExcepcionNegocio;
import co.coretech.portal.compartido.aplicacion.NoAutenticadoException;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;
import co.coretech.portal.compartido.aplicacion.ProhibidoException;
import co.coretech.portal.compartido.aplicacion.ReglaNegocioException;
import co.coretech.portal.compartido.aplicacion.ValidacionException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

/**
 * Convierte cualquier error en la respuesta de docs/04-api.md: {@code { "errores": [{ "campo", "mensaje" }] }}.
 * <ul>
 *   <li>Validación de datos → 422 con un error por campo.</li>
 *   <li>Excepciones de negocio → 404, 403, 409, 422 o 423 según su tipo.</li>
 *   <li>Restricciones y disparadores de PostgreSQL → la tabla de docs/04-api.md ({@link TraductorErroresBaseDatos}).</li>
 *   <li>Cualquier otro error → 500 con un mensaje genérico; el detalle va solo al log.</li>
 * </ul>
 * Nunca se devuelve 500 por una regla de negocio ni se muestra al usuario el texto técnico de la base.
 */
@RestControllerAdvice
class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    static final String MENSAJE_INESPERADO = "Ocurrió un error inesperado. Intenta de nuevo en unos minutos.";
    static final String MENSAJE_FORMATO = "El valor no tiene el formato esperado";

    private final TraductorErroresBaseDatos traductor;

    ManejadorGlobalErrores(TraductorErroresBaseDatos traductor) {
        this.traductor = traductor;
    }

    // ---- Validación de datos → 422

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespuestaError> cuerpoInvalido(MethodArgumentNotValidException ex) {
        List<ErrorCampo> errores = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.add(new ErrorCampo(e.getField(), mensajeDe(e))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errores.add(ErrorCampo.general(e.getDefaultMessage())));
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, new RespuestaError(errores));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<RespuestaError> parametrosInvalidos(HandlerMethodValidationException ex) {
        List<ErrorCampo> errores = new ArrayList<>();
        ex.getParameterValidationResults().forEach(resultado -> {
            String campo = resultado.getMethodParameter().getParameterName();
            resultado.getResolvableErrors().forEach(e -> errores.add(new ErrorCampo(campo, e.getDefaultMessage())));
        });
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, new RespuestaError(errores));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<RespuestaError> restriccionesInvalidas(ConstraintViolationException ex) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> new ErrorCampo(ultimoNodo(v.getPropertyPath()), v.getMessage()))
                .toList();
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, new RespuestaError(errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<RespuestaError> cuerpoIlegible(HttpMessageNotReadableException ex) {
        log.debug("Cuerpo de la petición ilegible", ex);
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, RespuestaError.de("Los datos enviados no tienen el formato esperado"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<RespuestaError> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, RespuestaError.de(ex.getName(), MENSAJE_FORMATO));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<RespuestaError> parametroFaltante(MissingServletRequestParameterException ex) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, RespuestaError.de(ex.getParameterName(), "Este dato es obligatorio"));
    }

    /**
     * Un archivo más grande que el tope de spring.servlet.multipart (6 MB): mismo mensaje que el límite de 2 MB.
     * En los adjuntos de un paso el límite es de 5 MB por archivo.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<RespuestaError> archivoDemasiadoGrande(MaxUploadSizeExceededException ex,
            jakarta.servlet.http.HttpServletRequest peticion) {
        if (peticion.getRequestURI().endsWith("/adjuntos")) {
            return responder(HttpStatus.UNPROCESSABLE_ENTITY,
                    RespuestaError.de("adjuntos", "Cada archivo pesa máximo 5 MB, y son máximo 5 archivos."));
        }
        return responder(HttpStatus.UNPROCESSABLE_ENTITY,
                RespuestaError.de("imagen", "La imagen pesa más de 2 MB. Comprímela o usa formato WebP."));
    }

    // ---- Excepciones de negocio que lanzan los servicios

    @ExceptionHandler(ExcepcionNegocio.class)
    ResponseEntity<RespuestaError> negocio(ExcepcionNegocio ex) {
        if (ex instanceof ReglaNegocioException regla) {
            return responder(HttpStatus.UNPROCESSABLE_ENTITY, RespuestaError.de(regla.campo().orElse(null), ex.getMessage()));
        }
        if (ex instanceof ValidacionException validacion) {
            List<ErrorCampo> errores = validacion.fallas().stream().map(f -> new ErrorCampo(f.campo(), f.mensaje())).toList();
            return responder(HttpStatus.UNPROCESSABLE_ENTITY, new RespuestaError(errores));
        }
        if (ex instanceof ConflictoException conflicto) {
            return responder(HttpStatus.CONFLICT, RespuestaError.de(conflicto.campo().orElse(null), ex.getMessage()));
        }
        return responder(estadoDe(ex), RespuestaError.de(ex.getMessage()));
    }

    private static HttpStatus estadoDe(ExcepcionNegocio ex) {
        if (ex instanceof NoAutenticadoException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof NoEncontradoException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof ProhibidoException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof CuentaBloqueadaException) {
            return HttpStatus.LOCKED;
        }
        if (ex instanceof ConflictoException) {
            return HttpStatus.CONFLICT;
        }
        // Una subclase nueva sin código propio: es una regla del negocio, nunca un 500.
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    // ---- Seguridad (dentro de los controladores; los filtros se manejan en la configuración de seguridad)

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<RespuestaError> accesoDenegado(AccessDeniedException ex) {
        return responder(HttpStatus.FORBIDDEN, RespuestaError.de("No tienes permiso para hacer esta acción"));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<RespuestaError> sinSesion(AuthenticationException ex) {
        return responder(HttpStatus.UNAUTHORIZED, RespuestaError.de("Tu sesión venció, ingresa de nuevo"));
    }

    // ---- Concurrencia: dos usuarios cambiaron lo mismo (columna version)

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<RespuestaError> versionDesactualizada(ObjectOptimisticLockingFailureException ex) {
        return responder(HttpStatus.CONFLICT, RespuestaError.de("Estos datos cambiaron mientras los editabas. Recarga para ver lo último."));
    }

    // ---- Todo lo demás: primero la base de datos; después los errores propios de Spring MVC; si no, 500

    @ExceptionHandler(Exception.class)
    ResponseEntity<RespuestaError> otros(Exception ex) {
        var traducido = traductor.traducir(ex);
        if (traducido.isPresent()) {
            ErrorTraducido e = traducido.get();
            log.debug("Error de la base de datos traducido a {}: {}", e.estado().value(), ex.getMessage());
            return responder(e.estado(), RespuestaError.de(e.campo(), e.mensaje()));
        }
        if (ex instanceof ErrorResponse respuestaSpring) {
            HttpStatusCode estado = respuestaSpring.getStatusCode();
            return responder(estado, RespuestaError.de(mensajeGenerico(estado)));
        }
        log.error("Error no controlado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, RespuestaError.de(MENSAJE_INESPERADO));
    }

    private static String mensajeGenerico(HttpStatusCode estado) {
        return switch (estado.value()) {
            case 404 -> "No encontramos lo que buscas";
            case 405 -> "Esta acción no está disponible";
            case 406, 415 -> "El formato de la petición no es compatible";
            default -> estado.is4xxClientError() ? "La petición no es válida" : MENSAJE_INESPERADO;
        };
    }

    private static String mensajeDe(FieldError error) {
        // Un valor que no se puede convertir (texto en un número, fecha mal escrita) no trae mensaje propio.
        return error.isBindingFailure() ? MENSAJE_FORMATO : error.getDefaultMessage();
    }

    private static String ultimoNodo(Path ruta) {
        String nombre = null;
        for (Path.Node nodo : ruta) {
            nombre = nodo.getName();
        }
        return nombre;
    }

    private static ResponseEntity<RespuestaError> responder(HttpStatusCode estado, RespuestaError cuerpo) {
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
