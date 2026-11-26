package co.coretech.portal.agenda.domain;

import java.time.Instant;
import java.util.UUID;

/** El cuestionario de la reunión, con los nombres de los enums de la base. Una vez guardado no se edita (RN-24, RNF-02). */
public record CuestionarioCita(UUID id, UUID citaId, String resumen, String descripcionCliente, String presupuesto,
        String fechaDeseada, String idioma, String urgencia, UUID registradoPor, Instant creadoEn) {
}
