package co.coretech.portal.acceso.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.ClienteRegistrado;
import co.coretech.portal.acceso.service.ClienteIdentidadExterna.ErrorProveedorException;
import co.coretech.portal.acceso.service.ClienteIdentidadExterna.Identidad;
import co.coretech.portal.acceso.repository.CuentaExternaRepositorio;
import co.coretech.portal.acceso.repository.RolRepositorio;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.ProveedorExterno;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Registro e ingreso de clientes con Google o Microsoft (RF-50, D-50). Al volver del proveedor:
 * <ol>
 * <li>Si su cuenta del proveedor ya está vinculada → entra.</li>
 * <li>Si su correo ya tiene cuenta de cliente → se une a esa cuenta y entra. Solo con el correo verificado por el
 * proveedor; si no, cualquiera podría crear una cuenta con el correo de otra persona y entrar a sus proyectos.</li>
 * <li>Si no hay cuenta → todavía NO se crea: la persona completa celular, empresa y la política de datos en
 * "Completa tu registro" (HU-05, Ley 1581). Entonces se crea, se vincula y recibe la bienvenida (D-52).</li>
 * </ol>
 * Solo clientes: el personal entra con su correo y contraseña. El "estado" de cada ingreso (10 minutos, un solo uso,
 * con PKCE) y los registros a medio completar viven en memoria: un solo servidor, como el límite de intentos.
 */
@Service
public class ServicioIngresoExterno {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioIngresoExterno.class);

    static final Duration VIGENCIA = Duration.ofMinutes(10);
    static final String RUTA_REGRESO = "/api/v1/auth/externo/callback/";
    static final String REGISTRO_VENCIDO = "El registro tardó demasiado. Vuelve a elegir con qué cuenta registrarte.";

    /** Por qué no se pudo entrar; la pantalla de inicio de sesión lo explica (?externo=…). */
    public enum Rechazo { CANCELADO, VENCIDO, SIN_CORREO, CORREO_SIN_VERIFICAR, SOLO_CLIENTES, DESACTIVADA, BLOQUEADA, ERROR }

    public sealed interface Regreso permits Entro, FaltaRegistro, NoEntro {
    }

    /** Entró: el controlador abre la sesión. */
    public record Entro(Ingreso ingreso) implements Regreso {
    }

    /** Es nuevo: debe completar el registro; {@code id} va en una cookie de 10 minutos. */
    public record FaltaRegistro(String id) implements Regreso {
    }

    public record NoEntro(Rechazo motivo, ProveedorExterno proveedor) implements Regreso {
    }

    /** Lo que ve "Completa tu registro". */
    public record DatosPendientes(String proveedor, String nombre, String email) {
    }

    private record Intento(ProveedorExterno proveedor, String verificador, Instant expira) {
    }

    private record Pendiente(ProveedorExterno proveedor, String sujeto, String email, String nombre, Instant expira) {
    }

    private final ClienteIdentidadExterna proveedores;
    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final CuentaExternaRepositorio vinculos;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final String urlPortal;
    private final SecureRandom azar = new SecureRandom();
    private final Map<String, Intento> intentos = new ConcurrentHashMap<>();
    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();

    ServicioIngresoExterno(ClienteIdentidadExterna proveedores, UsuarioRepositorio usuarios, RolRepositorio roles,
            CuentaExternaRepositorio vinculos, RegistroAuditoria auditoria, ApplicationEventPublisher eventos, Clock reloj,
            PropiedadesCoretech propiedades) {
        this.proveedores = proveedores;
        this.usuarios = usuarios;
        this.roles = roles;
        this.vinculos = vinculos;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
        this.urlPortal = propiedades.urlPortal().replaceAll("/+$", "");
    }

    /** Los que tienen su cliente configurado: solo esos botones se muestran. */
    public List<ProveedorExterno> activos() {
        return Arrays.stream(ProveedorExterno.values()).filter(proveedores::configurado).toList();
    }

    /** Paso 1: la página del proveedor a la que se lleva el navegador. 404 si el proveedor no existe o no está activo. */
    public String iniciar(String ruta) {
        ProveedorExterno proveedor = ProveedorExterno.deRuta(ruta).filter(proveedores::configurado)
                .orElseThrow(() -> new NoEncontradoException("Esa forma de ingresar no está disponible"));
        Instant ahora = Instant.now(reloj);
        intentos.values().removeIf(i -> i.expira().isBefore(ahora));
        pendientes.values().removeIf(p -> p.expira().isBefore(ahora));
        String estado = aleatorio(32);
        String verificador = aleatorio(48);
        intentos.put(estado, new Intento(proveedor, verificador, ahora.plus(VIGENCIA)));
        return proveedores.urlAutorizacion(proveedor, redirect(proveedor), estado, reto(verificador));
    }

    /** Paso 2: el proveedor devuelve al navegador con el código (o con un error si la persona canceló). */
    @Transactional
    public Regreso regreso(String ruta, String estado, String codigo, String error) {
        ProveedorExterno proveedor = ProveedorExterno.deRuta(ruta).orElse(null);
        Intento intento = estado == null ? null : intentos.remove(estado);
        if (proveedor == null || intento == null || intento.proveedor() != proveedor
                || intento.expira().isBefore(Instant.now(reloj))) {
            return new NoEntro(Rechazo.VENCIDO, proveedor);
        }
        if (error != null || codigo == null) {
            return new NoEntro(Rechazo.CANCELADO, proveedor);
        }
        Identidad identidad;
        try {
            identidad = proveedores.identidad(proveedor, codigo, intento.verificador(), redirect(proveedor));
        } catch (ErrorProveedorException e) {
            LOG.warn("No se pudo ingresar con {}: {}", proveedor.nombre(), e.getMessage());
            return new NoEntro(Rechazo.ERROR, proveedor);
        }
        if (identidad.sujeto() == null || identidad.email() == null || identidad.email().isBlank()) {
            return new NoEntro(Rechazo.SIN_CORREO, proveedor);
        }
        String email = ServicioAutenticacion.normalizarEmail(identidad.email());
        Instant ahora = Instant.now(reloj);

        Optional<UUID> vinculado = vinculos.usuarioDe(proveedor, identidad.sujeto());
        if (vinculado.isPresent()) {
            Usuario usuario = usuarios.findById(vinculado.get()).orElse(null);
            if (usuario == null) {
                return new NoEntro(Rechazo.DESACTIVADA, proveedor);
            }
            vinculos.registrarIngreso(proveedor, identidad.sujeto(), identidad.correoVerificado() ? email : null, ahora);
            return entrar(usuario, proveedor, ahora);
        }

        if (!identidad.correoVerificado()) {
            return new NoEntro(Rechazo.CORREO_SIN_VERIFICAR, proveedor);
        }
        Optional<Usuario> conEseCorreo = usuarios.buscarPorEmail(email);
        if (conEseCorreo.isPresent()) {
            Usuario usuario = conEseCorreo.get();
            if (!Rol.CLIENTE.equals(usuario.getRol().getCodigo())) {
                return new NoEntro(Rechazo.SOLO_CLIENTES, proveedor);
            }
            if (!usuario.isActivo() || usuario.estaAnonimizada()) {
                return new NoEntro(Rechazo.DESACTIVADA, proveedor);
            }
            if (vinculos.tieneVinculo(usuario.getId(), proveedor)) {
                // Ya tiene otra cuenta de este proveedor vinculada: no se cambia en silencio.
                return new NoEntro(Rechazo.ERROR, proveedor);
            }
            vinculos.vincular(usuario, proveedor, identidad.sujeto(), email, ahora);
            Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
            cambios.put("cuentaExterna", CambioDeValor.nuevo(proveedor.nombre() + " (" + email + ")"));
            auditoria.registrarComo(usuario.getId(), CategoriaAuditoria.SESIONES, "Unió su cuenta de " + proveedor.nombre(),
                    "usuario", usuario.getId().toString(), cambios);
            return entrar(usuario, proveedor, ahora);
        }

        String id = aleatorio(32);
        pendientes.put(id, new Pendiente(proveedor, identidad.sujeto(), email, nombreSugerido(identidad.nombre()),
                ahora.plus(VIGENCIA)));
        return new FaltaRegistro(id);
    }

    public DatosPendientes pendiente(String id) {
        Pendiente p = vigente(id);
        return new DatosPendientes(p.proveedor().nombre(), p.nombre(), p.email());
    }

    /** "Completa tu registro": crea el cliente con lo que dio el proveedor y lo que la persona escribió (HU-05). */
    @Transactional
    public Ingreso completarRegistro(String id, String nombre, String celular, String empresa, boolean aceptaDatos) {
        Pendiente p = vigente(id);
        if (!aceptaDatos) {
            throw new ReglaNegocioException("aceptaDatos", "Debes aceptar la política de datos para crear la cuenta.");
        }
        if (usuarios.existeEmail(p.email())) {
            throw new ConflictoException("email", ServicioAutenticacion.CORREO_REPETIDO);
        }
        Instant ahora = Instant.now(reloj);
        Rol cliente = roles.findByCodigo(Rol.CLIENTE).orElseThrow();
        String laEmpresa = empresa == null || empresa.isBlank() ? null : empresa.trim();
        // Sin contraseña del portal: entra con su cuenta del proveedor (o crea una con "¿Olvidaste tu contraseña?")
        Usuario usuario = Usuario.nuevoCliente(cliente, nombre.trim(), p.email(), celular.trim(), laEmpresa, null, ahora);
        usuario.registrarIngreso(ahora);
        usuarios.saveAndFlush(usuario);
        vinculos.vincular(usuario, p.proveedor(), p.sujeto(), p.email(), ahora);
        pendientes.remove(id);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("nombre", CambioDeValor.nuevo(usuario.getNombre()));
        cambios.put("email", CambioDeValor.nuevo(p.email()));
        cambios.put("rol", CambioDeValor.nuevo(Rol.CLIENTE));
        cambios.put("cuentaExterna", CambioDeValor.nuevo(p.proveedor().nombre()));
        auditoria.registrarComo(usuario.getId(), CategoriaAuditoria.USUARIOS, "Creó su cuenta de cliente con " + p.proveedor().nombre(),
                "usuario", usuario.getId().toString(), cambios);
        eventos.publishEvent(new ClienteRegistrado(usuario.getId(), usuario.getNombre()));
        return new Ingreso(usuario.getId(), Rol.CLIENTE, usuario.getNombre(), false, usuario.getVersionSesion());
    }

    private Regreso entrar(Usuario usuario, ProveedorExterno proveedor, Instant ahora) {
        usuario.liberarBloqueoVencido(ahora);
        if (usuario.estaBloqueada(ahora)) {
            return new NoEntro(Rechazo.BLOQUEADA, proveedor);
        }
        if (!usuario.isActivo()) {
            return new NoEntro(Rechazo.DESACTIVADA, proveedor);
        }
        usuario.registrarIngreso(ahora);
        auditoria.registrarComo(usuario.getId(), CategoriaAuditoria.SESIONES, "Inició sesión con " + proveedor.nombre(),
                "usuario", usuario.getId().toString(), Map.of());
        return new Entro(new Ingreso(usuario.getId(), usuario.getRol().getCodigo(), usuario.getNombre(), false,
                usuario.getVersionSesion()));
    }

    private Pendiente vigente(String id) {
        Pendiente p = id == null ? null : pendientes.get(id);
        if (p == null || p.expira().isBefore(Instant.now(reloj))) {
            if (id != null) {
                pendientes.remove(id);
            }
            throw new ConflictoException(REGISTRO_VENCIDO);
        }
        return p;
    }

    private String redirect(ProveedorExterno proveedor) {
        return urlPortal + RUTA_REGRESO + proveedor.ruta();
    }

    /** El nombre del proveedor como sugerencia: solo letras y espacios, máximo 60 (lo puede corregir). */
    static String nombreSugerido(String nombre) {
        if (nombre == null) {
            return "";
        }
        String limpio = nombre.replaceAll("[^\\p{L} ]", " ").replaceAll("\\s+", " ").trim();
        return limpio.length() > 60 ? limpio.substring(0, 60).trim() : limpio;
    }

    private String aleatorio(int bytes) {
        byte[] b = new byte[bytes];
        azar.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String reto(String verificador) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verificador.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
