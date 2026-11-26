package co.coretech.portal.acceso.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.ClienteRegistrado;
import co.coretech.portal.acceso.repository.RolRepositorio;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.CuentaBloqueadaException;
import co.coretech.portal.compartido.exception.ExcepcionNegocio;
import co.coretech.portal.compartido.exception.NoAutenticadoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/** Registro de clientes, inicio de sesión y cambio de contraseña (HU-05, HU-06; RN-18, RN-22; RNF-10). */
@Service
public class ServicioAutenticacion {

    static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos";
    static final String CUENTA_BLOQUEADA = "Cuenta bloqueada 15 minutos por seguridad";
    static final String CUENTA_DESACTIVADA = "Tu cuenta está desactivada, contacta al administrador";
    /** Igual que la tabla de errores de la base (ux_usuarios_email_ci); el registro agrega el enlace "¿Quieres iniciar sesión?" (D-38). */
    static final String CORREO_REPETIDO = "Ya existe una cuenta con este correo";

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final PasswordEncoder codificador;
    private final PoliticaContrasena politica;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;
    /** Para que un correo que no existe tarde lo mismo que uno con contraseña errada (no revela cuáles existen). */
    private final String hashFicticio;

    public ServicioAutenticacion(UsuarioRepositorio usuarios, RolRepositorio roles, PasswordEncoder codificador,
            PoliticaContrasena politica, RegistroAuditoria auditoria, SesionActual sesion, Clock reloj,
            ApplicationEventPublisher eventos) {
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
        this.politica = politica;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.reloj = reloj;
        this.hashFicticio = codificador.encode("contraseña-que-nadie-tiene");
    }

    /** HU-05: el visitante crea su cuenta de cliente y queda con sesión iniciada. */
    @Transactional
    public Ingreso registrar(NuevoCliente datos) {
        if (!datos.aceptaDatos()) {
            throw new ReglaNegocioException("aceptaDatos", "Debes aceptar la política de datos para crear la cuenta.");
        }
        politica.validar(datos.password(), datos.confirmacion(), "password", "confirmacion");
        String email = normalizarEmail(datos.email());
        if (usuarios.existeEmail(email)) {
            throw new ConflictoException("email", CORREO_REPETIDO);
        }
        Rol cliente = roles.findByCodigo(Rol.CLIENTE).orElseThrow();
        String empresa = datos.empresa() == null || datos.empresa().isBlank() ? null : datos.empresa().trim();
        Usuario usuario = Usuario.nuevoCliente(cliente, datos.nombre().trim(), email, datos.celular().trim(), empresa,
                codificador.encode(datos.password()), Instant.now(reloj));
        usuarios.saveAndFlush(usuario);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("nombre", CambioDeValor.nuevo(usuario.getNombre()));
        cambios.put("email", CambioDeValor.nuevo(email));
        cambios.put("rol", CambioDeValor.nuevo(Rol.CLIENTE));
        // Aún no hay sesión en la petición: el autor es la cuenta que se acaba de crear
        auditoria.registrarComo(usuario.getId(), CategoriaAuditoria.USUARIOS, "Creó su cuenta de cliente", "usuario",
                usuario.getId().toString(), cambios);
        // Bienvenida en la campana y por correo (D-52); si el registro falla, no se envía
        eventos.publishEvent(new ClienteRegistrado(usuario.getId(), usuario.getNombre()));
        return new Ingreso(usuario.getId(), Rol.CLIENTE, usuario.getNombre(), false, usuario.getVersionSesion());
    }

    /**
     * HU-06: inicia sesión. Tras 5 intentos fallidos bloquea la cuenta 15 minutos (423).
     * Los intentos fallidos y el bloqueo se guardan aunque la respuesta sea un error (noRollbackFor).
     */
    @Transactional(noRollbackFor = ExcepcionNegocio.class)
    public Ingreso iniciarSesion(String email, String password) {
        Instant ahora = Instant.now(reloj);
        Optional<Usuario> encontrado = usuarios.buscarPorEmail(normalizarEmail(email));
        if (encontrado.isEmpty()) {
            codificador.matches(password, hashFicticio);
            auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Intento fallido de inicio de sesión", "usuario", null,
                    Map.of("email", CambioDeValor.nuevo(normalizarEmail(email))));
            throw new NoAutenticadoException(CREDENCIALES_INCORRECTAS);
        }
        Usuario usuario = encontrado.get();
        usuario.liberarBloqueoVencido(ahora);
        if (usuario.estaBloqueada(ahora)) {
            throw new CuentaBloqueadaException(CUENTA_BLOQUEADA);
        }
        if (!codificador.matches(password, usuario.getPasswordHash())) {
            int antes = usuario.getIntentosFallidos();
            boolean seBloqueo = usuario.registrarIntentoFallido(ahora);
            String id = usuario.getId().toString();
            auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Intento fallido de inicio de sesión", "usuario", id,
                    Map.of("intentosFallidos", CambioDeValor.de(antes, usuario.getIntentosFallidos())));
            if (seBloqueo) {
                auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Bloqueó la cuenta por intentos fallidos", "usuario", id,
                        Map.of("bloqueadoHasta", CambioDeValor.nuevo(usuario.getBloqueadoHasta())));
                throw new CuentaBloqueadaException(CUENTA_BLOQUEADA);
            }
            throw new NoAutenticadoException(CREDENCIALES_INCORRECTAS);
        }
        if (!usuario.isActivo()) {
            throw new ProhibidoException(CUENTA_DESACTIVADA);
        }
        usuario.registrarIngreso(ahora);
        auditoria.registrarComo(usuario.getId(), CategoriaAuditoria.SESIONES, "Inició sesión", "usuario",
                usuario.getId().toString(), Map.of());
        return new Ingreso(usuario.getId(), usuario.getRol().getCodigo(), usuario.getNombre(), usuario.isDebeCambiarPassword(),
                usuario.getVersionSesion());
    }

    /** HU-06: cambia la contraseña del usuario con sesión (obligatorio en el primer ingreso con la temporal). */
    @Transactional
    public Ingreso cambiarPassword(String actual, String nueva, String confirmacion) {
        Usuario usuario = usuarios.buscarConRol(sesion.usuario().id())
                .orElseThrow(() -> new NoAutenticadoException("Tu sesión venció, ingresa de nuevo"));
        if (!codificador.matches(actual, usuario.getPasswordHash())) {
            throw new ReglaNegocioException("passwordActual", "La contraseña actual no es correcta");
        }
        if (codificador.matches(nueva, usuario.getPasswordHash())) {
            throw new ReglaNegocioException("passwordNueva", usuario.isDebeCambiarPassword()
                    ? "La nueva contraseña debe ser distinta de la temporal."
                    : "La nueva contraseña debe ser distinta de la actual.");
        }
        politica.validar(nueva, confirmacion, "passwordNueva", "confirmacion");
        boolean eraTemporal = usuario.isDebeCambiarPassword();
        usuario.cambiarPassword(codificador.encode(nueva), Instant.now(reloj));
        auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Cambió su contraseña", "usuario", usuario.getId().toString(),
                Map.of("debeCambiarPassword", CambioDeValor.de(eraTemporal, false)));
        return new Ingreso(usuario.getId(), usuario.getRol().getCodigo(), usuario.getNombre(), false, usuario.getVersionSesion());
    }

    static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
