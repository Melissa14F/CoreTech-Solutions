package co.coretech.portal.acceso.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Cuenta de usuario (tabla usuarios). Guarda las reglas de su propio estado: intentos fallidos y
 * bloqueo de 15 minutos (RNF-10), contraseña temporal (RF-05) y último acceso.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    public static final int MAX_INTENTOS = 5;
    public static final Duration BLOQUEO = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id")
    private Rol rol;

    private String nombre;
    private String email;
    private String celular;
    private String empresa;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "debe_cambiar_password")
    private boolean debeCambiarPassword;

    @Column(name = "acepta_datos_at")
    private Instant aceptaDatosAt;

    private boolean activo;

    @Column(name = "intentos_fallidos")
    private short intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "ultimo_acceso_at")
    private Instant ultimoAccesoAt;

    /** Sube al cambiar o restablecer la contraseña: las sesiones abiertas dejan de valer (V7, RNF-10). */
    @Column(name = "version_sesion")
    private int versionSesion;

    @Column(name = "anonimizado_at")
    private Instant anonimizadoAt;

    @Column(name = "created_at")
    private Instant creadoEn;

    @Column(name = "updated_at")
    private Instant actualizadoEn;

    protected Usuario() {
    }

    /** Cliente que se registra solo (RN-18): acepta la política de datos al crear la cuenta (RNF-09). */
    public static Usuario nuevoCliente(Rol rolCliente, String nombre, String email, String celular, String empresa,
            String passwordHash, Instant ahora) {
        Usuario u = new Usuario();
        u.id = UUID.randomUUID();
        u.rol = Objects.requireNonNull(rolCliente);
        u.nombre = nombre;
        u.email = email;
        u.celular = celular;
        u.empresa = empresa;
        u.passwordHash = passwordHash;
        u.aceptaDatosAt = ahora;
        u.activo = true;
        u.creadoEn = ahora;
        u.actualizadoEn = ahora;
        return u;
    }

    /**
     * Cuenta del personal que crea el administrador (RN-18, RF-05): nace con una contraseña temporal que
     * debe cambiar en el primer ingreso.
     */
    public static Usuario nuevoPersonal(Rol rol, String nombre, String email, String celular, String hashTemporal,
            Instant ahora) {
        Usuario u = new Usuario();
        u.id = UUID.randomUUID();
        u.rol = Objects.requireNonNull(rol);
        u.nombre = nombre;
        u.email = email;
        u.celular = celular;
        u.passwordHash = hashTemporal;
        u.debeCambiarPassword = true;
        u.activo = true;
        u.creadoEn = ahora;
        u.actualizadoEn = ahora;
        return u;
    }

    /** Cuenta de un desarrollador (Product Owner) que se reactiva con una contraseña temporal nueva (HU-26). */
    public void reactivarConClaveTemporal(String hashTemporal, Instant ahora) {
        passwordHash = hashTemporal;
        debeCambiarPassword = true;
        versionSesion++;
        activo = true;
        intentosFallidos = 0;
        bloqueadoHasta = null;
        actualizadoEn = ahora;
    }

    /** El administrador corrige los datos de la cuenta (HU-20). */
    public void editar(String nombre, String email, String celular, Instant ahora) {
        this.nombre = nombre;
        this.email = email;
        this.celular = celular;
        actualizadoEn = ahora;
    }

    /** Desactivar bloquea la cuenta hasta que el administrador la reactive (HU-20). */
    public void cambiarActivo(boolean activo, Instant ahora) {
        this.activo = activo;
        actualizadoEn = ahora;
    }

    /** El administrador levanta el bloqueo por intentos fallidos antes de los 15 minutos. */
    public void desbloquear(Instant ahora) {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        actualizadoEn = ahora;
    }

    /**
     * Derecho de supresión (Ley 1581): los datos personales se reemplazan por valores anónimos, la cuenta
     * queda inactiva y sin una contraseña que alguien conozca. El historial (citas, proyectos) se conserva.
     */
    public void anonimizar(String nombreAnonimo, String emailAnonimo, String celularAnonimo, String hashInservible,
            Instant ahora) {
        nombre = nombreAnonimo;
        email = emailAnonimo;
        celular = celularAnonimo;
        empresa = null;
        passwordHash = hashInservible;
        versionSesion++;
        debeCambiarPassword = false;
        activo = false;
        intentosFallidos = 0;
        bloqueadoHasta = null;
        anonimizadoAt = ahora;
        actualizadoEn = ahora;
    }

    public boolean estaAnonimizada() {
        return anonimizadoAt != null;
    }

    /** ¿Está bloqueada por intentos fallidos en este momento? */
    public boolean estaBloqueada(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /**
     * Suma un intento fallido; al quinto bloquea la cuenta 15 minutos.
     * @return true si este intento la bloqueó
     */
    public boolean registrarIntentoFallido(Instant ahora) {
        liberarBloqueoVencido(ahora);
        intentosFallidos++;
        actualizadoEn = ahora;
        if (intentosFallidos >= MAX_INTENTOS) {
            bloqueadoHasta = ahora.plus(BLOQUEO);
            return true;
        }
        return false;
    }

    /** Si el bloqueo ya pasó, la cuenta empieza de cero. */
    public void liberarBloqueoVencido(Instant ahora) {
        if (bloqueadoHasta != null && !bloqueadoHasta.isAfter(ahora)) {
            bloqueadoHasta = null;
            intentosFallidos = 0;
            actualizadoEn = ahora;
        }
    }

    public void registrarIngreso(Instant ahora) {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        ultimoAccesoAt = ahora;
        actualizadoEn = ahora;
    }

    /** Contraseña nueva elegida por el usuario: deja de ser temporal y se desbloquea la cuenta. */
    public void cambiarPassword(String nuevoHash, Instant ahora) {
        passwordHash = nuevoHash;
        debeCambiarPassword = false;
        versionSesion++;
        intentosFallidos = 0;
        bloqueadoHasta = null;
        actualizadoEn = ahora;
    }

    public UUID getId() {
        return id;
    }

    public Rol getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isDebeCambiarPassword() {
        return debeCambiarPassword;
    }

    public boolean isActivo() {
        return activo;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public int getVersionSesion() {
        return versionSesion;
    }

    public String getCelular() {
        return celular;
    }

    public String getEmpresa() {
        return empresa;
    }

    public Instant getUltimoAccesoAt() {
        return ultimoAccesoAt;
    }

    public Instant getAnonimizadoAt() {
        return anonimizadoAt;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
