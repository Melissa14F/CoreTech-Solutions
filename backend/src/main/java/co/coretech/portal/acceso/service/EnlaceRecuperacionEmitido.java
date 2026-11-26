package co.coretech.portal.acceso.service;

/** Se creó un enlace para restablecer la contraseña: falta enviarlo por correo. */
public record EnlaceRecuperacionEmitido(String email, String nombre, String enlace) {
}
