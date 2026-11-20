package co.coretech.portal.acceso.aplicacion;

/**
 * Se creó una cuenta del personal con contraseña temporal: falta enviarle sus datos de acceso por correo
 * (RF-05). La contraseña temporal viaja solo en este correo, nunca en la pantalla ni en la API.
 */
public record CuentaPersonalCreada(String email, String nombre, String claveTemporal, String urlIngreso) {

    @Override
    public String toString() {
        return "CuentaPersonalCreada[email=" + email + "]";
    }
}
