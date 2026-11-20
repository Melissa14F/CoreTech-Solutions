package co.coretech.portal.agenda.dominio;

/** Estado de una cita (enum estado_cita de la base). Solo PENDIENTE y CONFIRMADA ocupan la franja (RN-02). */
public enum EstadoCita {
    PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA
}
