package co.coretech.portal.acceso.service;

/** Datos del formulario "Crear cuenta" (HU-05). */
public record NuevoCliente(String nombre, String email, String celular, String empresa, String password,
        String confirmacion, boolean aceptaDatos) {

    /** Sin la contraseña: un registro de log nunca debe llevarla (RNF-10). */
    @Override
    public String toString() {
        return "NuevoCliente[email=" + email + "]";
    }
}
