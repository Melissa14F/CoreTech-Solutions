package co.coretech.portal.acceso.aplicacion;

/** Datos del formulario "Crear cuenta" (HU-05). */
public record NuevoCliente(String nombre, String email, String celular, String empresa, String password,
        String confirmacion, boolean aceptaDatos) {
}
