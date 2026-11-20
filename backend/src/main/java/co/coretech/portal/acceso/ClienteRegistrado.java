package co.coretech.portal.acceso;

import java.util.UUID;

/**
 * Evento: un visitante creó su cuenta de cliente (HU-05). Se publica dentro de la transacción del registro.
 * <p>
 * Lo escucha {@code comunicacion} para darle la bienvenida en la campana y por correo (D-52). Va en la raíz de acceso,
 * y no como un aviso directo, porque comunicacion ya depende de acceso (no se crea un ciclo entre módulos).
 */
public record ClienteRegistrado(UUID usuarioId, String nombre) {
}
