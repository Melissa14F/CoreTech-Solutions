package co.coretech.portal.acceso.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rol de la cuenta: CLIENTE, PRODUCT_OWNER o ADMINISTRADOR (tabla roles; los crea la migración V1). */
@Entity
@Table(name = "roles")
public class Rol {

    public static final String CLIENTE = "CLIENTE";
    public static final String PRODUCT_OWNER = "PRODUCT_OWNER";
    public static final String ADMINISTRADOR = "ADMINISTRADOR";

    @Id
    private Short id;

    private String codigo;

    private String nombre;

    protected Rol() {
    }

    public Short getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }
}
