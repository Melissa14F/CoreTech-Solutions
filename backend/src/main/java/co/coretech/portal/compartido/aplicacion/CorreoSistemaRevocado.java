package co.coretech.portal.compartido.aplicacion;

/**
 * Evento: Google retiró el permiso con el que el portal enviaba correos por Gmail (venció, la persona lo quitó o cambió
 * la contraseña). Se publica una sola vez por revocación; comunicacion avisa a los administradores en la campana,
 * porque por correo no se les puede avisar mientras Gmail no esté conectado (D-51).
 *
 * @param cuenta la cuenta de Gmail que quedó desconectada
 */
public record CorreoSistemaRevocado(String cuenta) {
}
