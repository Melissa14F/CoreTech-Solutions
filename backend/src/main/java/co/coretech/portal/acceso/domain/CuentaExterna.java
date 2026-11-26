package co.coretech.portal.acceso.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Vínculo entre un cliente y su cuenta de Google o Microsoft (tabla cuentas_externas, V9, RF-50). Es única por proveedor
 * y sujeto ({@code ux_cuentas_externas_sujeto}) y por usuario y proveedor ({@code ux_cuentas_externas_usuario}).
 */
@Entity
@Table(name = "cuentas_externas")
public class CuentaExterna {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    private ProveedorExterno proveedor;

    /** Identificador de la persona en el proveedor (el «sub» de OpenID Connect). */
    private String sujeto;

    private String email;

    @Column(name = "created_at")
    private Instant creadaEn;

    @Column(name = "ultimo_ingreso_at")
    private Instant ultimoIngresoEn;

    protected CuentaExterna() {
    }

    public static CuentaExterna nueva(Usuario usuario, ProveedorExterno proveedor, String sujeto, String email, Instant ahora) {
        CuentaExterna c = new CuentaExterna();
        c.id = UUID.randomUUID();
        c.usuario = usuario;
        c.proveedor = proveedor;
        c.sujeto = sujeto;
        c.email = email;
        c.creadaEn = ahora;
        c.ultimoIngresoEn = ahora;
        return c;
    }

    public UUID getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public ProveedorExterno getProveedor() {
        return proveedor;
    }

    public String getSujeto() {
        return sujeto;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getUltimoIngresoEn() {
        return ultimoIngresoEn;
    }
}
