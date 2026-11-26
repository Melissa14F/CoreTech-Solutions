package co.coretech.portal.proyectos.dto;

import java.util.List;

/** Cuerpos de las peticiones y respuestas de {@link ControladorPlantillas}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoPlantillas {

    private DtoPlantillas() {
    }

    /** Lo que se lee y lo que se guarda tienen la misma forma; al guardar, el orden es el de la lista. */
    public record PasoDto(Integer orden, String titulo, String explicacion) {
    }

    public record FaseDto(Integer orden, String nombre, String queEs, Boolean apruebaCliente, List<PasoDto> pasos) {
    }

    public record PlantillaDto(List<FaseDto> fases) {
    }
}
