package co.coretech.portal.proyectos.domain;

import java.util.List;

/** Una fase de una plantilla o de un plan, con sus pasos. */
public record FasePlantilla(int orden, String nombre, String queEs, boolean apruebaCliente, List<PasoPlantilla> pasos) {
}
