package co.coretech.portal.acceso.datos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.coretech.portal.acceso.dominio.Rol;

public interface RolRepositorio extends JpaRepository<Rol, Short> {

    Optional<Rol> findByCodigo(String codigo);
}
