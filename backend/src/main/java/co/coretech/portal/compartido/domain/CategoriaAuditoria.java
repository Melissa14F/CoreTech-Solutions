package co.coretech.portal.compartido.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Categoría de una acción en la auditoría ({@code auditoria.categoria}); el administrador filtra por ella
 * (HU-22). Son los filtros de prototipo/admin/auditoria.html, en el mismo orden.
 */
public enum CategoriaAuditoria {
    CITAS("citas", "Citas"),
    PROYECTOS("proyectos", "Proyectos"),
    AVANCES("avances", "Avances"),
    SOLICITUDES("solicitudes", "Solicitudes"),
    USUARIOS("usuarios", "Usuarios"),
    PERMISOS("permisos", "Permisos"),
    SITIO("sitio", "Sitio web"),
    /** Inicios de sesión. */
    SESIONES("sesiones", "Sesiones"),
    /** Intentos fallidos, bloqueos, contraseñas y desbloqueos. */
    SEGURIDAD("seguridad", "Seguridad");

    private final String valor;
    private final String nombre;

    CategoriaAuditoria(String valor, String nombre) {
        this.valor = valor;
        this.nombre = nombre;
    }

    /** Valor que se guarda en la columna. */
    public String valor() {
        return valor;
    }

    /** Como se muestra en la pantalla y en el CSV. */
    public String nombre() {
        return nombre;
    }

    public static Optional<CategoriaAuditoria> deValor(String valor) {
        return Arrays.stream(values()).filter(c -> c.valor.equals(valor)).findFirst();
    }
}
