package co.coretech.portal.acceso.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.service.CargadorUsuarioSesion;
import co.coretech.portal.compartido.service.UsuarioSesion;

/** Usuario de la sesión con su rol y los permisos de su rol (rol_permisos), leídos en cada petición. */
@Service
class CargadorUsuarioSesionBaseDatos implements CargadorUsuarioSesion {

    private final UsuarioRepositorio usuarios;

    CargadorUsuarioSesionBaseDatos(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsuarioSesion> cargar(UUID usuarioId) {
        return usuarios.buscarConRol(usuarioId)
                .filter(Usuario::isActivo)
                .map(u -> new UsuarioSesion(u.getId(), u.getNombre(), u.getEmail(), u.getRol().getCodigo(),
                        new HashSet<>(usuarios.permisosDelRol(u.getRol().getId())), u.isDebeCambiarPassword(), u.getVersionSesion()));
    }
}
