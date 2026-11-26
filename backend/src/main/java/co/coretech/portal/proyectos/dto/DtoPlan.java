package co.coretech.portal.proyectos.dto;

import java.math.BigDecimal;
import java.util.List;

/** Cuerpos de las peticiones y respuestas de {@link ControladorPlan}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoPlan {

    private DtoPlan() {
    }

    public record PasoDto(Integer orden, String titulo, String explicacion) {
    }

    public record FaseDto(Integer orden, String nombre, String queEs, Boolean apruebaCliente, List<PasoDto> pasos) {
    }

    /** Todo es opcional al guardar un borrador; al enviar se exige completo. La fecha es AAAA-MM-DD. */
    public record SolicitudPlan(List<FaseDto> fases, BigDecimal valorCotizado, String condicionesCotizacion, String fechaEstimadaEntrega) {
    }

    public record SolicitudAprobar(Boolean aceptaPlanYCotizacion) {
    }

    public record SolicitudAjustes(String comentario) {
    }

    public record SolicitudRechazo(String motivo) {
    }
}
