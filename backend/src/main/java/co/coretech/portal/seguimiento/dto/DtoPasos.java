package co.coretech.portal.seguimiento.dto;

/** Cuerpos de las peticiones y respuestas de {@link ControladorPasos}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoPasos {

    private DtoPasos() {
    }

    /** {@code estado} solo acepta HECHO (D-39); {@code nota} de 20 a 400 caracteres (20 a 500 en una corrección o un ajuste). */
    public record SolicitudPaso(String estado, String nota) {
    }
}
