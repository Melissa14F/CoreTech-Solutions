package co.coretech.portal.acceso;

import java.util.UUID;

/**
 * Puerta del módulo acceso para que especialidad maneje la cuenta del desarrollador (Product Owner) sin tocar la
 * tabla de usuarios (RF-05, RN-18, RN-25). La contraseña temporal sale solo por correo: nunca vuelve por aquí.
 */
public interface CuentasDeProductOwner {

    /**
     * Deja lista la cuenta con rol PRODUCT_OWNER y contraseña temporal (debe cambiarla en el primer ingreso) y la
     * envía por correo cuando se confirma la transacción. Si el desarrollador ya tuvo cuenta ({@code usuarioExistente}),
     * se reactiva con una contraseña temporal nueva y sus datos al día.
     *
     * @return id de la cuenta
     */
    UUID habilitar(UUID usuarioExistente, String nombre, String email, String celular);

    /** Bloquea la cuenta (la sesión se corta en la siguiente petición) hasta que el administrador la reactive (D-15). */
    void bloquear(UUID usuarioId);

    /** El correo del Product Owner es su usuario: si cambian sus datos, cambian también en su cuenta. */
    void actualizarDatos(UUID usuarioId, String nombre, String email, String celular);
}
