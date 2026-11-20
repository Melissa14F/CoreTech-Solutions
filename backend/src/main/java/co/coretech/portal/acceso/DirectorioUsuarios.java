package co.coretech.portal.acceso;

import java.util.Optional;
import java.util.UUID;

/**
 * Datos públicos de las cuentas que otros módulos pueden mostrar (por ejemplo, quién publicó el contenido
 * del sitio). Es la puerta del módulo acceso: nadie más lee la tabla usuarios.
 */
public interface DirectorioUsuarios {

    /** Nombre del usuario, si existe. */
    Optional<String> nombre(UUID usuarioId);

    /**
     * A quién y con qué nombre se le escribe un correo (cola de notificaciones, HU-24). Vacío si la cuenta no
     * existe, está desactivada o anonimizada: a esas cuentas no se les envían correos (la notificación interna
     * se guarda igual).
     */
    Optional<ContactoCorreo> contactoCorreo(UUID usuarioId);

    /** Ids de los administradores con la cuenta activa: a quienes se avisa de lo que solo ellos pueden resolver. */
    java.util.List<UUID> administradoresActivos();

    /** Nombre y correo de varias cuentas a la vez (para listar citas); el que no existe no aparece. */
    java.util.Map<UUID, ContactoCorreo> perfiles(java.util.Collection<UUID> usuarioIds);

    record ContactoCorreo(String nombre, String email) {
    }
}
