package co.coretech.portal.especialidad.dto;

/** Cuerpos de las peticiones y respuestas de {@link ControladorDesarrolladores}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoDesarrolladores {

    private DtoDesarrolladores() {
    }

    /** Cuerpo de PATCH …/activo. */
    public record SolicitudActivo(Boolean activo) {
    }
}
