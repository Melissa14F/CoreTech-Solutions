package co.coretech.portal.proyectos.dto;

/** Cuerpos de las peticiones y respuestas de {@link ControladorCambiosPlan}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoCambiosPlan {

    private DtoCambiosPlan() {
    }

    /** Solo el rechazo lleva cuerpo; el motivo va de 10 a 400 caracteres. */
    public record Rechazo(String motivo) {
    }
}
