package co.coretech.portal.acceso.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Enlace para restablecer la contraseña (RF-04, RNF-10): vale 30 minutos y una sola vez.
 * Se guarda solo el hash SHA-256 del token: quien lea la base no puede usar los enlaces.
 */
@Entity
@Table(name = "tokens_recuperacion")
public class TokenRecuperacion {

    public static final Duration VIGENCIA = Duration.ofMinutes(30);

    @Id
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expira_at")
    private Instant expiraEn;

    @Column(name = "usado_at")
    private Instant usadoEn;

    @Column(name = "created_at")
    private Instant creadoEn;

    protected TokenRecuperacion() {
    }

    public static TokenRecuperacion nuevo(UUID usuarioId, String tokenHash, Instant ahora) {
        TokenRecuperacion t = new TokenRecuperacion();
        t.id = UUID.randomUUID();
        t.usuarioId = usuarioId;
        t.tokenHash = tokenHash;
        t.expiraEn = ahora.plus(VIGENCIA);
        t.creadoEn = ahora;
        return t;
    }

    public boolean esValido(Instant ahora) {
        return usadoEn == null && expiraEn.isAfter(ahora);
    }

    public void usar(Instant ahora) {
        usadoEn = ahora;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
