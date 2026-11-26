package co.coretech.portal.seguimiento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.coretech.portal.seguimiento.service.ServicioFases.Entregada;

/** Cuerpos de las peticiones y respuestas de {@link ControladorFases}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoFases {

    private DtoFases() {
    }

    /** {@code tipo}: AJUSTE o ALCANCE ("algo nuevo"); {@code descripcion} de 20 a 800 caracteres. */
    public record SolicitudCambio(String tipo, String descripcion) {
    }

    /** {@code origen}: «Lo pidió el cliente», «La encontramos al construir» o «La encontramos en pruebas». */
    public record SolicitudCorreccion(String origen, String titulo, String explicacion) {
    }

    public record EntregadaDto(String estado, LocalDate plazoAprobacion, BigDecimal avancePorcentaje, String estadoProyecto,
            LocalDate garantiaHasta) {

        public static EntregadaDto de(Entregada e) {
            return new EntregadaDto(e.estado(), e.plazoAprobacion(), e.avancePorcentaje(), e.estadoProyecto(), e.garantiaHasta());
        }
    }
}
