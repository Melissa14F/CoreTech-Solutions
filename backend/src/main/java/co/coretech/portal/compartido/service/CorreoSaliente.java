package co.coretech.portal.compartido.service;

/**
 * Correos directos, que no pasan por la tabla {@code notificaciones}: el enlace para restablecer la contraseña
 * y la contraseña temporal (D-49: ningún secreto queda guardado en la base).
 * <p>
 * Nunca bloquea la petición: se envía en segundo plano, con hasta 3 intentos y espera creciente entre ellos
 * (en memoria: si la API se reinicia, los reintentos pendientes se pierden). Los demás avisos van por la cola
 * de {@code notificaciones}: se publica el evento {@code comunicacion.AvisoSolicitado}.
 */
public interface CorreoSaliente {

    void enviar(String para, String asunto, String texto);
}
