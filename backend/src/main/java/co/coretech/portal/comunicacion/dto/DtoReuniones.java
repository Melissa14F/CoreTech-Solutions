package co.coretech.portal.comunicacion.dto;

import java.time.OffsetDateTime;

/** Cuerpos de las peticiones y respuestas de {@link ControladorReuniones}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoReuniones {

    private DtoReuniones() {
    }

    /** Las horas llevan su desplazamiento (por ejemplo {@code 2026-11-24T10:00:00-05:00}); {@code opcion2} es opcional. */
    public record SolicitudReunion(String motivo, String razon, String modalidad, OffsetDateTime opcion1, OffsetDateTime opcion2) {
    }

    /**
     * {@code accion}: PROGRAMAR (con {@code opcion} 1 o 2 y, si es virtual, {@code enlace}), RECHAZAR o CANCELAR (con
     * {@code motivo}).
     */
    public record RespuestaSolicitud(String accion, Integer opcion, String enlace, String motivo) {
    }

    public record ResumenReunion(String resumen, String quePidio, String siguientePaso) {
    }
}
