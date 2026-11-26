package co.coretech.portal.acceso.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.Usuario;

@Service
class DirectorioUsuariosBaseDatos implements DirectorioUsuarios {

    private final UsuarioRepositorio usuarios;

    DirectorioUsuariosBaseDatos(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nombre(UUID usuarioId) {
        return usuarioId == null ? Optional.empty() : usuarios.findById(usuarioId).map(Usuario::getNombre);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UUID> administradoresActivos() {
        return usuarios.idsActivosDelRol(co.coretech.portal.acceso.domain.Rol.ADMINISTRADOR);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, ContactoCorreo> perfiles(java.util.Collection<UUID> usuarioIds) {
        java.util.Map<UUID, ContactoCorreo> perfiles = new java.util.HashMap<>();
        usuarios.findAllById(usuarioIds).forEach(u -> perfiles.put(u.getId(), new ContactoCorreo(u.getNombre(), u.getEmail())));
        return perfiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContactoCorreo> contactoCorreo(UUID usuarioId) {
        return usuarioId == null ? Optional.empty() : usuarios.findById(usuarioId)
                .filter(u -> u.isActivo() && !u.estaAnonimizada())
                .map(u -> new ContactoCorreo(u.getNombre(), u.getEmail()));
    }
}
