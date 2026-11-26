package co.coretech.portal.proyectos.domain;

/** Un paso de una plantilla o de un plan. {@code orden} lo pone el servidor; al guardar manda el orden de la lista. */
public record PasoPlantilla(int orden, String titulo, String explicacion) {
}
