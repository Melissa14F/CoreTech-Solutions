package co.coretech.portal.acceso.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.repository.TokenRecuperacionRepositorio;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.TokenRecuperacion;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Recuperación de la contraseña con un enlace de un solo uso que vale 30 minutos (RF-04, RNF-10).
 * El correo sale después de confirmar la transacción ({@link EnlaceRecuperacionEmitido}).
 */
@Service
public class ServicioRecuperacion {

    static final String ENLACE_INVALIDO = "Este enlace ya no es válido, solicita uno nuevo";

    private final UsuarioRepositorio usuarios;
    private final TokenRecuperacionRepositorio tokens;
    private final PasswordEncoder codificador;
    private final PoliticaContrasena politica;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final PropiedadesCoretech propiedades;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    public ServicioRecuperacion(UsuarioRepositorio usuarios, TokenRecuperacionRepositorio tokens, PasswordEncoder codificador,
            PoliticaContrasena politica, RegistroAuditoria auditoria, ApplicationEventPublisher eventos,
            PropiedadesCoretech propiedades, Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.codificador = codificador;
        this.politica = politica;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * "¿Olvidaste tu contraseña?" (HU-06). La respuesta es la misma exista o no el correo, para no
     * revelar qué cuentas existen; solo se envía el enlace a una cuenta activa.
     */
    @Transactional
    public void solicitarPorCorreo(String email) {
        usuarios.buscarPorEmail(ServicioAutenticacion.normalizarEmail(email))
                .filter(Usuario::isActivo)
                .ifPresent(u -> emitir(u, "Pidió un enlace para restablecer su contraseña", true));
    }

    /** El administrador envía el enlace a una cuenta (HU-20, flujo 2 de docs/04-api.md). */
    @Transactional
    public String solicitarPorAdministrador(UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));
        if (!usuario.isActivo()) {
            throw new ConflictoException("La cuenta está desactivada: actívala antes de restablecer la contraseña");
        }
        emitir(usuario, "Envió un enlace para restablecer la contraseña", false);
        return usuario.getEmail();
    }

    /** Define la contraseña nueva con el enlace; el enlace queda usado. */
    @Transactional
    public void restablecer(String token, String nueva, String confirmacion) {
        Instant ahora = Instant.now(reloj);
        TokenRecuperacion registro = tokens.findByTokenHash(hash(token))
                .filter(t -> t.esValido(ahora))
                .orElseThrow(() -> new ReglaNegocioException("token", ENLACE_INVALIDO));
        Usuario usuario = usuarios.findById(registro.getUsuarioId())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ReglaNegocioException("token", ENLACE_INVALIDO));
        politica.validar(nueva, confirmacion, "passwordNueva", "confirmacion");
        usuario.cambiarPassword(codificador.encode(nueva), ahora);
        registro.usar(ahora);
        // Sin sesión: el autor es la persona dueña del enlace
        auditoria.registrarComo(usuario.getId(), CategoriaAuditoria.SEGURIDAD, "Restableció su contraseña con el enlace", "usuario",
                usuario.getId().toString(), Map.of());
    }

    /** @param porSiMismo lo pide la persona sin sesión ("¿Olvidaste tu contraseña?"): ella es el autor en la auditoría */
    private void emitir(Usuario usuario, String accion, boolean porSiMismo) {
        Instant ahora = Instant.now(reloj);
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.borrarPendientes(usuario.getId());
        tokens.save(TokenRecuperacion.nuevo(usuario.getId(), hash(token), ahora));
        if (porSiMismo) {
            auditoria.registrarComo(usuario.getId(), CategoriaAuditoria.SEGURIDAD, accion, "usuario", usuario.getId().toString(), Map.of());
        } else {
            auditoria.registrar(CategoriaAuditoria.SEGURIDAD, accion, "usuario", usuario.getId().toString());
        }
        String enlace = propiedades.urlPortal() + "/restablecer?token=" + token;
        eventos.publishEvent(new EnlaceRecuperacionEmitido(usuario.getEmail(), usuario.getNombre(), enlace));
    }

    static String hash(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
