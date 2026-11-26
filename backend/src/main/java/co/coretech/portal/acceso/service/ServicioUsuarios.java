package co.coretech.portal.acceso.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.CitasDeUsuarios;
import co.coretech.portal.acceso.CuentasDeProductOwner;
import co.coretech.portal.acceso.EquiposDeUsuarios;
import co.coretech.portal.acceso.ProyectosDeUsuarios;
import co.coretech.portal.acceso.repository.CuentaExternaRepositorio;
import co.coretech.portal.acceso.repository.RolRepositorio;
import co.coretech.portal.acceso.repository.TokenRecuperacionRepositorio;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Cuentas de usuario para el administrador (HU-20; RN-18, RN-26; RF-05, RF-46): listar, crear cuentas de
 * administrador con contraseña temporal, editar, activar o desactivar, desbloquear y anonimizar los datos
 * de un cliente (derecho de supresión, Ley 1581). Todo cambio queda en la auditoría (RN-20).
 */
@Service
public class ServicioUsuarios implements CuentasDeProductOwner {

    static final String DOMINIO_CORPORATIVO = "@coretechsolutions.co";
    static final String NO_ENCONTRADO = "Usuario no encontrado";
    static final String DESACTIVARSE_A_SI_MISMO = "No puedes desactivar tu propia cuenta";
    /** RN-26, D-34: mismo texto que HU-25. */
    static final String PO_CON_PROYECTOS = "Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo";
    static final String ANONIMIZADA = "Los datos de esta cuenta ya están anonimizados";
    static final String SOLO_CLIENTES = "Solo se anonimizan los datos de un cliente";
    static final String NO_BLOQUEADA = "Esta cuenta no está bloqueada";

    /** Valores que reemplazan los datos personales; el correo lleva el id para seguir siendo único. */
    static final String NOMBRE_ANONIMO = "Cliente anónimo";
    static final String CELULAR_ANONIMO = "3000000000";
    static final String DOMINIO_ANONIMO = "@anonimo.invalid";

    private static final Pattern SOLO_LETRAS = Pattern.compile("^[\\p{L} ]+$");
    private static final Pattern CELULAR = Pattern.compile("^3\\d{9}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String LETRAS_CLAVE = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final UsuarioRepositorio usuarios;
    private final RolRepositorio roles;
    private final TokenRecuperacionRepositorio tokens;
    private final PasswordEncoder codificador;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final PropiedadesCoretech propiedades;
    private final CitasDeUsuarios citas;
    private final ProyectosDeUsuarios proyectos;
    private final EquiposDeUsuarios equipos;
    private final Clock reloj;
    private final CuentaExternaRepositorio cuentasExternas;
    private final SecureRandom aleatorio = new SecureRandom();

    public ServicioUsuarios(UsuarioRepositorio usuarios, RolRepositorio roles, TokenRecuperacionRepositorio tokens,
            PasswordEncoder codificador, RegistroAuditoria auditoria, SesionActual sesion,
            ApplicationEventPublisher eventos, PropiedadesCoretech propiedades, CitasDeUsuarios citas,
            ProyectosDeUsuarios proyectos, EquiposDeUsuarios equipos, Clock reloj, CuentaExternaRepositorio cuentasExternas) {
        this.cuentasExternas = cuentasExternas;
        this.usuarios = usuarios;
        this.roles = roles;
        this.tokens = tokens;
        this.codificador = codificador;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.propiedades = propiedades;
        this.citas = citas;
        this.proyectos = proyectos;
        this.equipos = equipos;
        this.reloj = reloj;
    }

    /** Datos que el administrador escribe al crear o editar una cuenta. */
    public record DatosCuenta(String nombre, String celular, String email) {
    }

    // ------------------------------------------------------------------ Consultas

    /** Todas las cuentas: administradores, Product Owners y clientes, cada grupo por nombre. */
    @Transactional(readOnly = true)
    public List<UsuarioGestion> listar() {
        Map<UUID, CitasDeUsuarios.Conteo> citasPorCliente = citas.porCliente();
        Map<UUID, ProyectosDeUsuarios.Conteo> proyectosPorCliente = proyectos.porCliente();
        Map<UUID, Integer> activosPorEquipo = proyectos.activosPorEquipo();
        Map<UUID, EquiposDeUsuarios.Equipo> equipoPorPo = equipos.porProductOwner();
        Instant ahora = Instant.now(reloj);
        return usuarios.listarConRol().stream()
                .sorted(Comparator.comparingInt((Usuario u) -> ordenRol(u.getRol().getCodigo()))
                        .thenComparing(u -> u.getNombre().toLowerCase()))
                .map(u -> aGestion(u, ahora, actividad(u, citasPorCliente, proyectosPorCliente, activosPorEquipo, equipoPorPo)))
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioGestion obtener(UUID usuarioId) {
        return conActividad(buscar(usuarioId));
    }

    // ------------------------------------------------------------------ Cambios

    /** HU-20: cuenta de administrador con contraseña temporal que le llega por correo (RF-05). */
    @Transactional
    public UsuarioGestion crearAdministrador(DatosCuenta datos) {
        DatosCuenta limpios = validar(datos, true, true, null);
        String clave = claveTemporal();
        Rol administrador = roles.findByCodigo(Rol.ADMINISTRADOR).orElseThrow();
        Usuario usuario = Usuario.nuevoPersonal(administrador, limpios.nombre(), limpios.email(), limpios.celular(),
                codificador.encode(clave), Instant.now(reloj));
        usuarios.saveAndFlush(usuario);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("nombre", CambioDeValor.nuevo(usuario.getNombre()));
        cambios.put("email", CambioDeValor.nuevo(usuario.getEmail()));
        cambios.put("celular", CambioDeValor.nuevo(usuario.getCelular()));
        cambios.put("rol", CambioDeValor.nuevo(Rol.ADMINISTRADOR));
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Creó una cuenta de administrador", "usuario",
                usuario.getId().toString(), cambios);
        eventos.publishEvent(new CuentaPersonalCreada(usuario.getEmail(), usuario.getNombre(), clave,
                propiedades.urlPortal() + "/ingresar"));
        return conActividad(usuario);
    }

    /** HU-20: corrige nombre, celular y correo de cualquier cuenta (el personal usa el correo corporativo). */
    @Transactional
    public UsuarioGestion editar(UUID usuarioId, DatosCuenta datos) {
        Usuario usuario = buscar(usuarioId);
        if (usuario.estaAnonimizada()) {
            throw new ConflictoException("Esta cuenta está anonimizada: sus datos ya no se editan");
        }
        boolean esCliente = Rol.CLIENTE.equals(usuario.getRol().getCodigo());
        DatosCuenta limpios = validar(datos, !esCliente, !esCliente, usuario.getId());

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        anotar(cambios, "nombre", usuario.getNombre(), limpios.nombre());
        anotar(cambios, "celular", usuario.getCelular(), limpios.celular());
        anotar(cambios, "email", usuario.getEmail(), limpios.email());
        if (!cambios.isEmpty()) {
            usuario.editar(limpios.nombre(), limpios.email(), limpios.celular(), Instant.now(reloj));
            usuarios.flush();
            auditoria.registrar(CategoriaAuditoria.USUARIOS, "Editó los datos de una cuenta", "usuario",
                    usuario.getId().toString(), cambios);
        }
        return conActividad(usuario);
    }

    /**
     * HU-20: activa o desactiva la cuenta. Desactivar corta su sesión en la siguiente petición (la sesión
     * se lee de la base) y la bloquea hasta que el administrador la reactive.
     */
    @Transactional
    public UsuarioGestion cambiarActivo(UUID usuarioId, boolean activo) {
        Usuario usuario = buscar(usuarioId);
        if (usuario.isActivo() == activo) {
            return conActividad(usuario);
        }
        if (!activo) {
            if (usuario.getId().equals(sesion.usuario().id())) {
                throw new ReglaNegocioException("activo", DESACTIVARSE_A_SI_MISMO);
            }
            if (proyectosActivosDelEquipo(usuario) > 0) {
                throw new ConflictoException(PO_CON_PROYECTOS);
            }
        } else if (usuario.estaAnonimizada()) {
            throw new ConflictoException("Una cuenta anonimizada no se puede activar");
        }
        usuario.cambiarActivo(activo, Instant.now(reloj));
        usuarios.flush();
        auditoria.registrar(CategoriaAuditoria.USUARIOS, activo ? "Activó la cuenta" : "Desactivó la cuenta", "usuario",
                usuario.getId().toString(), Map.of("activo", CambioDeValor.de(!activo, activo)));
        return conActividad(usuario);
    }

    // ------------------------------------------------------------------ Cuenta del Product Owner (HU-25, HU-26)

    @Override
    @Transactional
    public UUID habilitar(UUID usuarioExistente, String nombre, String email, String celular) {
        String clave = claveTemporal();
        Instant ahora = Instant.now(reloj);
        Usuario usuario;
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        if (usuarioExistente == null) {
            DatosCuenta limpios = validar(new DatosCuenta(nombre, celular, email), true, true, null);
            Rol rol = roles.findByCodigo(Rol.PRODUCT_OWNER).orElseThrow();
            usuario = Usuario.nuevoPersonal(rol, limpios.nombre(), limpios.email(), limpios.celular(), codificador.encode(clave), ahora);
            usuarios.saveAndFlush(usuario);
            cambios.put("nombre", CambioDeValor.nuevo(usuario.getNombre()));
            cambios.put("email", CambioDeValor.nuevo(usuario.getEmail()));
            cambios.put("rol", CambioDeValor.nuevo(Rol.PRODUCT_OWNER));
            auditoria.registrar(CategoriaAuditoria.USUARIOS, "Creó la cuenta de un desarrollador (Product Owner)", "usuario",
                    usuario.getId().toString(), cambios);
        } else {
            usuario = buscar(usuarioExistente);
            DatosCuenta limpios = validar(new DatosCuenta(nombre, celular, email), true, true, usuario.getId());
            anotar(cambios, "email", usuario.getEmail(), limpios.email());
            cambios.put("activo", CambioDeValor.de(usuario.isActivo(), true));
            usuario.editar(limpios.nombre(), limpios.email(), limpios.celular(), ahora);
            usuario.reactivarConClaveTemporal(codificador.encode(clave), ahora);
            usuarios.flush();
            auditoria.registrar(CategoriaAuditoria.USUARIOS, "Reactivó la cuenta de un desarrollador (Product Owner)", "usuario",
                    usuario.getId().toString(), cambios);
        }
        eventos.publishEvent(new CuentaPersonalCreada(usuario.getEmail(), usuario.getNombre(), clave,
                propiedades.urlPortal() + "/ingresar"));
        return usuario.getId();
    }

    @Override
    @Transactional
    public void bloquear(UUID usuarioId) {
        Usuario usuario = buscar(usuarioId);
        if (!usuario.isActivo()) {
            return;
        }
        usuario.cambiarActivo(false, Instant.now(reloj));
        usuarios.flush();
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Bloqueó la cuenta de un desarrollador (Product Owner)", "usuario",
                usuario.getId().toString(), Map.of("activo", CambioDeValor.de(true, false)));
    }

    @Override
    @Transactional
    public void actualizarDatos(UUID usuarioId, String nombre, String email, String celular) {
        editar(usuarioId, new DatosCuenta(nombre, celular, email));
    }

    /** Levanta el bloqueo de 15 minutos por intentos fallidos (RNF-10). */
    @Transactional
    public UsuarioGestion desbloquear(UUID usuarioId) {
        Usuario usuario = buscar(usuarioId);
        Instant ahora = Instant.now(reloj);
        if (!usuario.estaBloqueada(ahora)) {
            throw new ConflictoException(NO_BLOQUEADA);
        }
        Instant hasta = usuario.getBloqueadoHasta();
        usuario.desbloquear(ahora);
        usuarios.flush();
        auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Desbloqueó la cuenta", "usuario", usuario.getId().toString(),
                Map.of("bloqueadoHasta", CambioDeValor.de(hasta, null)));
        return conActividad(usuario);
    }

    /**
     * Derecho de supresión (Ley 1581; docs "Conservación de datos"): nombre, correo, celular y empresa
     * pasan a valores anónimos, la cuenta queda inactiva con anonimizado_at y el historial se conserva.
     * La auditoría registra el hecho, no los datos que se borran.
     */
    @Transactional
    public UsuarioGestion anonimizar(UUID usuarioId) {
        Usuario usuario = buscar(usuarioId);
        if (!Rol.CLIENTE.equals(usuario.getRol().getCodigo())) {
            throw new ReglaNegocioException(SOLO_CLIENTES);
        }
        if (usuario.estaAnonimizada()) {
            throw new ConflictoException(ANONIMIZADA);
        }
        boolean estabaActiva = usuario.isActivo();
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String claveInservible = Base64.getEncoder().encodeToString(bytes);
        String email = "anonimo-" + usuario.getId().toString().replace("-", "") + DOMINIO_ANONIMO;
        usuario.anonimizar(NOMBRE_ANONIMO, email, CELULAR_ANONIMO, codificador.encode(claveInservible), Instant.now(reloj));
        tokens.borrarPendientes(usuario.getId());
        // Sin vínculos con Google o Microsoft: ya no puede entrar con ellos ni quedan sus identificadores (RF-50)
        cuentasExternas.borrarDeUsuario(usuario.getId());
        usuarios.flush();

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("datosPersonales", CambioDeValor.de("Nombre, correo, celular y empresa", "Valores anónimos"));
        cambios.put("activo", CambioDeValor.de(estabaActiva, false));
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Anonimizó los datos de un cliente", "usuario",
                usuario.getId().toString(), cambios);
        return conActividad(usuario);
    }

    // ------------------------------------------------------------------ Reglas de los datos

    /**
     * Reglas del formulario de prototipo/admin/usuarios.html, todas a la vez (un error por campo). El
     * personal usa el correo corporativo y un nombre de 5 a 60; los clientes, 3 a 60 (D-40). El correo
     * es único sin distinguir mayúsculas (RN-18): 409.
     */
    private DatosCuenta validar(DatosCuenta datos, boolean personal, boolean correoCorporativo, UUID propio) {
        String nombre = datos.nombre() == null ? "" : datos.nombre().trim().replaceAll("\\s+", " ");
        String celular = datos.celular() == null ? "" : datos.celular().trim();
        String email = ServicioAutenticacion.normalizarEmail(datos.email());
        int minimoNombre = personal ? 5 : 3;

        var errores = new ValidacionException.Acumulador();
        if (nombre.isEmpty()) {
            errores.agregar("nombre", "Escribe el nombre completo.");
        } else if (nombre.length() < minimoNombre) {
            errores.agregar("nombre", "Escribe nombre y apellido.");
        } else if (nombre.length() > 60) {
            errores.agregar("nombre", "Escribe máximo 60 caracteres.");
        } else if (!SOLO_LETRAS.matcher(nombre).matches()) {
            errores.agregar("nombre", "Escribe solo letras y espacios.");
        }
        if (celular.isEmpty()) {
            errores.agregar("celular", "Escribe el número de celular.");
        } else if (!CELULAR.matcher(celular).matches()) {
            errores.agregar("celular", "Escribe un celular de 10 dígitos que empiece por 3.");
        }
        if (email.isEmpty()) {
            errores.agregar("email", "Escribe el correo.");
        } else if (email.length() > 120) {
            errores.agregar("email", "Escribe máximo 120 caracteres.");
        } else if (!CORREO.matcher(email).matches()) {
            errores.agregar("email", "Escribe un correo válido, por ejemplo nombre@empresa.com.");
        } else if (correoCorporativo && !email.endsWith(DOMINIO_CORPORATIVO)) {
            errores.agregar("email", "Usa el correo corporativo que termina en @coretechsolutions.co.");
        }
        errores.lanzarSiHay();

        usuarios.buscarPorEmail(email)
                .filter(otro -> !otro.getId().equals(propio))
                .ifPresent(otro -> {
                    throw new ConflictoException("email", ServicioAutenticacion.CORREO_REPETIDO);
                });
        return new DatosCuenta(nombre, celular, email);
    }

    /** RF-05: "Ct-XXXX-XXXX" como en el prototipo, con mayúscula, número y símbolo (RN-22). */
    String claveTemporal() {
        String clave;
        do {
            clave = "Ct-" + aleatorias(4) + "-" + aleatorias(4);
        } while (!PoliticaContrasena.cumple(clave));
        return clave;
    }

    private String aleatorias(int n) {
        StringBuilder texto = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            texto.append(LETRAS_CLAVE.charAt(aleatorio.nextInt(LETRAS_CLAVE.length())));
        }
        return texto.toString();
    }

    // ------------------------------------------------------------------ Apoyo

    private Usuario buscar(UUID usuarioId) {
        return usuarios.buscarConRol(usuarioId).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private int proyectosActivosDelEquipo(Usuario usuario) {
        if (!Rol.PRODUCT_OWNER.equals(usuario.getRol().getCodigo())) {
            return 0;
        }
        EquiposDeUsuarios.Equipo equipo = equipos.porProductOwner().get(usuario.getId());
        return equipo == null ? 0 : proyectos.activosPorEquipo().getOrDefault(equipo.equipoId(), 0);
    }

    private UsuarioGestion conActividad(Usuario u) {
        return aGestion(u, Instant.now(reloj), actividad(u, citas.porCliente(), proyectos.porCliente(),
                proyectos.activosPorEquipo(), equipos.porProductOwner()));
    }

    private static UsuarioGestion.Actividad actividad(Usuario u, Map<UUID, CitasDeUsuarios.Conteo> citasPorCliente,
            Map<UUID, ProyectosDeUsuarios.Conteo> proyectosPorCliente, Map<UUID, Integer> activosPorEquipo,
            Map<UUID, EquiposDeUsuarios.Equipo> equipoPorPo) {
        return switch (u.getRol().getCodigo()) {
            case Rol.CLIENTE -> {
                CitasDeUsuarios.Conteo c = citasPorCliente.getOrDefault(u.getId(), new CitasDeUsuarios.Conteo(0, 0, 0, 0));
                ProyectosDeUsuarios.Conteo p = proyectosPorCliente.getOrDefault(u.getId(), new ProyectosDeUsuarios.Conteo(0, 0));
                yield new UsuarioGestion.Actividad(null, p.enCurso(), p.finalizados(), c.pendientes(), c.confirmadas(),
                        c.atendidas(), c.canceladas());
            }
            case Rol.PRODUCT_OWNER -> {
                EquiposDeUsuarios.Equipo e = equipoPorPo.get(u.getId());
                yield e == null ? UsuarioGestion.Actividad.NINGUNA
                        : new UsuarioGestion.Actividad(new UsuarioGestion.Equipo(e.equipoId(),
                                activosPorEquipo.getOrDefault(e.equipoId(), 0), e.maxProyectos()), 0, 0, 0, 0, 0, 0);
            }
            default -> UsuarioGestion.Actividad.NINGUNA;
        };
    }

    private static UsuarioGestion aGestion(Usuario u, Instant ahora, UsuarioGestion.Actividad actividad) {
        return new UsuarioGestion(u.getId(), u.getNombre(), u.getEmail(), u.getCelular(), u.getEmpresa(),
                u.getRol().getCodigo(), u.isActivo(), u.estaBloqueada(ahora) ? u.getBloqueadoHasta() : null,
                u.isDebeCambiarPassword(), u.getUltimoAccesoAt(), u.estaAnonimizada(), u.getCreadoEn(), actividad);
    }

    private static int ordenRol(String rol) {
        return switch (rol) {
            case Rol.ADMINISTRADOR -> 0;
            case Rol.PRODUCT_OWNER -> 1;
            default -> 2;
        };
    }

    private static void anotar(Map<String, CambioDeValor> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, CambioDeValor.de(antes, despues));
        }
    }
}
