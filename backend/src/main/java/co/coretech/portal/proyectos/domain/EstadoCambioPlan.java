package co.coretech.portal.proyectos.domain;

/** Estado de un cambio de plan (enum estado_cambio_plan de la base): PROPUESTO → AUTORIZADO → ACEPTADO, o rechazado. */
public enum EstadoCambioPlan {
    PROPUESTO, AUTORIZADO, ACEPTADO, RECHAZADO_ADMIN, RECHAZADO_CLIENTE
}
