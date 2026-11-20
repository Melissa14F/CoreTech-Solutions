package co.coretech.portal.proyectos.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas;
import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.FasePlantilla;
import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.PasoPlantilla;

/**
 * Plantilla de fases de un servicio (HU-27). Leerla la necesita quien crea proyectos o gestiona el plan;
 * guardarla, solo quien gestiona proyectos (el administrador).
 */
@RestController
@RequestMapping("/api/v1/servicios/{servicioId}/plantilla")
class ControladorPlantillas {

    private final ServicioPlantillas plantillas;

    ControladorPlantillas(ServicioPlantillas plantillas) {
        this.plantillas = plantillas;
    }

    /** Lo que se lee y lo que se guarda tienen la misma forma; al guardar, el orden es el de la lista. */
    record PasoDto(Integer orden, String titulo, String explicacion) {
    }

    record FaseDto(Integer orden, String nombre, String queEs, Boolean apruebaCliente, List<PasoDto> pasos) {
    }

    record PlantillaDto(List<FaseDto> fases) {
    }

    @GetMapping
    @PreAuthorize("@permisos.tiene('proyectos.crear') or @permisos.tiene('proyectos.gestionar')")
    PlantillaDto obtener(@PathVariable UUID servicioId) {
        return aDto(plantillas.obtener(servicioId));
    }

    @PutMapping
    @PreAuthorize("@permisos.tiene('proyectos.gestionar')")
    PlantillaDto guardar(@PathVariable UUID servicioId, @RequestBody PlantillaDto cuerpo) {
        List<FasePlantilla> fases = cuerpo.fases() == null ? null : cuerpo.fases().stream()
                .map(f -> f == null ? null : new FasePlantilla(0, f.nombre(), f.queEs(), Boolean.TRUE.equals(f.apruebaCliente()),
                        f.pasos() == null ? null : f.pasos().stream()
                                .map(p -> p == null ? null : new PasoPlantilla(0, p.titulo(), p.explicacion())).toList()))
                .toList();
        return aDto(plantillas.guardar(servicioId, fases));
    }

    private static PlantillaDto aDto(List<FasePlantilla> fases) {
        return new PlantillaDto(fases.stream().map(f -> new FaseDto(f.orden(), f.nombre(), f.queEs(), f.apruebaCliente(),
                f.pasos().stream().map(p -> new PasoDto(p.orden(), p.titulo(), p.explicacion())).toList())).toList());
    }
}
