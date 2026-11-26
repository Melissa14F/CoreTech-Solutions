package co.coretech.portal.agenda.domain;

/** Estado de una cita (enum estado_cita de la base). Solo PENDIENTE y CONFIRMADA ocupan la franja (RN-02). */
public enum EstadoCita {
    PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA
}
