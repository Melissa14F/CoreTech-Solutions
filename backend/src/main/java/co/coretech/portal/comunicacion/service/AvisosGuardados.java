package co.coretech.portal.comunicacion.service;

/**
 * Evento interno de comunicación: ya quedaron guardadas {@code cantidad} notificaciones nuevas. Lo escucha
 * {@link EnvioInmediato} cuando la transacción que las guardó se confirma.
 */
record AvisosGuardados(int cantidad) {
}
