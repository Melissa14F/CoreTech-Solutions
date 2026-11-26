package co.coretech.portal.compartido.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Token de sesión cerrado con «Cerrar sesión» que todavía no vence (tabla sesiones_cerradas, V7, RNF-10). Se guarda
 * solo su {@code jti}: mientras el token no venza, deja de valer aunque alguien lo haya copiado.
 */
@Entity
@Table(name = "sesiones_cerradas", indexes = {
        @Index(name = "ix_sesiones_cerradas_expira", columnList = "expira_at")
})
public class SesionCerrada {

    @Id
    private UUID jti;

    @Column(name = "expira_at")
    private Instant expiraEn;

    protected SesionCerrada() {
    }

    public UUID getJti() {
        return jti;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }
}
