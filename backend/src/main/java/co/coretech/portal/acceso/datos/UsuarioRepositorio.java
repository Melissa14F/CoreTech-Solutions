package co.coretech.portal.acceso.datos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.coretech.portal.acceso.dominio.Usuario;

public interface UsuarioRepositorio extends JpaRepository<Usuario, UUID> {

    /** El correo es único sin distinguir mayúsculas (ux_usuarios_email_ci). */
    @EntityGraph(attributePaths = "rol")
    @Query("SELECT u FROM Usuario u WHERE lower(u.email) = lower(:email)")
    Optional<Usuario> buscarPorEmail(@Param("email") String email);

    @Query("SELECT count(u) > 0 FROM Usuario u WHERE lower(u.email) = lower(:email)")
    boolean existeEmail(@Param("email") String email);

    @EntityGraph(attributePaths = "rol")
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> buscarConRol(@Param("id") UUID id);

    /** Ids de las cuentas activas de un rol (por ejemplo, los administradores a quienes avisar). */
    @Query("SELECT u.id FROM Usuario u WHERE u.rol.codigo = :rol AND u.activo = true ORDER BY u.creadoEn")
    List<UUID> idsActivosDelRol(@Param("rol") String rol);

    /** Todas las cuentas con su rol, para Usuarios (HU-20). */
    @Query("SELECT u FROM Usuario u JOIN FETCH u.rol")
    List<Usuario> listarConRol();

    /** Códigos de los permisos del rol (tablas permisos y rol_permisos). */
    @Query(value = """
            SELECT p.codigo FROM permisos p JOIN rol_permisos rp ON rp.permiso_id = p.id
             WHERE rp.rol_id = :rolId ORDER BY p.codigo
            """, nativeQuery = true)
    List<String> permisosDelRol(@Param("rolId") short rolId);
}
