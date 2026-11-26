package co.coretech.portal.comunicacion.dto;

/** Cuerpos de las peticiones y respuestas de {@link ControladorMensajes}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoMensajes {

    private DtoMensajes() {
    }

    /** {@code tema} solo lo envía el cliente; el contenido va de 10 a 1000 caracteres. */
    public record SolicitudMensaje(String tema, String contenido) {
    }
}
