package co.coretech.portal.comunicacion.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aviso para un usuario (HU-24): se ve en la campana y se envía por correo desde la cola
 * ({@code enviada_email}, hasta 3 intentos, RNF-13).
 */
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    /** Máximo de intentos de envío del correo (HU-24; la base lo exige con ck_notif_intentos). */
    public static final int MAX_INTENTOS = 3;

    @Id
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    private String tipo;

    private String titulo;

    private String mensaje;

    private String enlace;

    private boolean leida;

    @Column(name = "enviada_email")
    private boolean enviadaEmail;

    @Column(name = "intentos_envio")
    private short intentosEnvio;

    @Column(name = "ultimo_error")
    private String ultimoError;

    @Column(name = "created_at")
    private Instant creadaEn;

    protected Notificacion() {
    }

    public static Notificacion nueva(UUID usuarioId, String tipo, String titulo, String mensaje, String enlace, Instant ahora) {
        Notificacion n = new Notificacion();
        n.id = UUID.randomUUID();
        n.usuarioId = usuarioId;
        n.tipo = tipo;
        n.titulo = titulo;
        n.mensaje = mensaje;
        n.enlace = enlace;
        n.creadaEn = ahora;
        return n;
    }

    public void marcarLeida() {
        leida = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getEnlace() {
        return enlace;
    }

    public boolean isLeida() {
        return leida;
    }

    public boolean isEnviadaEmail() {
        return enviadaEmail;
    }

    public int getIntentosEnvio() {
        return intentosEnvio;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
