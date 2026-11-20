package co.coretech.portal.compartido.dominio;

/**
 * Valor anterior y nuevo de un campo, para {@code auditoria.cambios} (RN-20).
 * Al crear un registro, {@code anterior} es {@code null}; al quitarlo, {@code nuevo} es {@code null}.
 */
public record CambioDeValor(Object anterior, Object nuevo) {

    public static CambioDeValor de(Object anterior, Object nuevo) {
        return new CambioDeValor(anterior, nuevo);
    }

    public static CambioDeValor nuevo(Object valor) {
        return new CambioDeValor(null, valor);
    }
}
