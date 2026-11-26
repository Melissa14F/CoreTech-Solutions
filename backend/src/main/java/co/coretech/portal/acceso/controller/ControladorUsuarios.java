package co.coretech.portal.acceso.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.acceso.service.ServicioRecuperacion;
import co.coretech.portal.acceso.service.ServicioUsuarios;
import co.coretech.portal.acceso.service.UsuarioGestion;
import co.coretech.portal.acceso.domain.TokenRecuperacion;
import co.coretech.portal.acceso.dto.DtoAcceso.RespuestaRecuperacionAdmin;
import co.coretech.portal.acceso.dto.DtoAcceso.SolicitudActivo;
import co.coretech.portal.acceso.dto.DtoAcceso.SolicitudCuenta;
import jakarta.validation.Valid;

/** Cuentas de usuario para el administrador (HU-20). Todo pide el permiso seguridad.cuentas. */
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("@permisos.tiene('seguridad.cuentas')")
class ControladorUsuarios {

    private final ServicioUsuarios usuarios;
    private final ServicioRecuperacion recuperacion;

    ControladorUsuarios(ServicioUsuarios usuarios, ServicioRecuperacion recuperacion) {
        this.usuarios = usuarios;
        this.recuperacion = recuperacion;
    }

    @GetMapping
    List<UsuarioGestion> listar() {
        return usuarios.listar();
    }

    @GetMapping("/{usuarioId}")
    UsuarioGestion obtener(@PathVariable UUID usuarioId) {
        return usuarios.obtener(usuarioId);
    }

    /** Crear cuenta de administrador: la contraseña temporal le llega por correo (RF-05). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UsuarioGestion crear(@RequestBody SolicitudCuenta cuerpo) {
        return usuarios.crearAdministrador(cuerpo.aDatos());
    }

    @PutMapping("/{usuarioId}")
    UsuarioGestion editar(@PathVariable UUID usuarioId, @RequestBody SolicitudCuenta cuerpo) {
        return usuarios.editar(usuarioId, cuerpo.aDatos());
    }

    @PatchMapping("/{usuarioId}/activo")
    UsuarioGestion cambiarActivo(@PathVariable UUID usuarioId, @Valid @RequestBody SolicitudActivo cuerpo) {
        return usuarios.cambiarActivo(usuarioId, cuerpo.activo());
    }

    @PostMapping("/{usuarioId}/desbloquear")
    UsuarioGestion desbloquear(@PathVariable UUID usuarioId) {
        return usuarios.desbloquear(usuarioId);
    }

    /** Derecho de supresión de un cliente (Ley 1581). */
    @PostMapping("/{usuarioId}/anonimizar")
    UsuarioGestion anonimizar(@PathVariable UUID usuarioId) {
        return usuarios.anonimizar(usuarioId);
    }

    /** Envía al usuario el enlace para restablecer su contraseña (30 minutos, un solo uso). */
    @PostMapping("/{usuarioId}/recuperacion")
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaRecuperacionAdmin enviarRecuperacion(@PathVariable UUID usuarioId) {
        String correo = recuperacion.solicitarPorAdministrador(usuarioId);
        return new RespuestaRecuperacionAdmin(correo, TokenRecuperacion.VIGENCIA.toMinutes());
    }
}
