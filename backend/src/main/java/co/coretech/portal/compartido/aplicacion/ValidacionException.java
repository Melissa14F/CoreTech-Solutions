package co.coretech.portal.compartido.aplicacion;

import java.util.ArrayList;
import java.util.List;

/**
 * Varios datos inválidos a la vez (por ejemplo, los textos de la página principal): un error por campo,
 * como los muestra el formulario. → 422 con todos los errores.
 * <pre>{@code
 * var errores = new ValidacionException.Acumulador();
 * if (nombre.isBlank()) errores.agregar("nombre", "Escribe el nombre del servicio.");
 * errores.lanzarSiHay();
 * }</pre>
 */
public class ValidacionException extends ExcepcionNegocio {

    /** Un error: el campo como lo envía la API y el mensaje del prototipo. */
    public record Falla(String campo, String mensaje) {
    }

    private final List<Falla> fallas;

    public ValidacionException(List<Falla> fallas) {
        super(fallas.isEmpty() ? "Revisa los datos marcados." : fallas.get(0).mensaje());
        this.fallas = List.copyOf(fallas);
    }

    public List<Falla> fallas() {
        return fallas;
    }

    /** Junta los errores de un formulario y lanza la excepción solo si hay alguno. */
    public static final class Acumulador {

        private final List<Falla> fallas = new ArrayList<>();

        /** Agrega el error si el campo todavía no tiene uno (se muestra el primero de cada campo). */
        public Acumulador agregar(String campo, String mensaje) {
            if (fallas.stream().noneMatch(f -> f.campo().equals(campo))) {
                fallas.add(new Falla(campo, mensaje));
            }
            return this;
        }

        public boolean tiene(String campo) {
            return fallas.stream().anyMatch(f -> f.campo().equals(campo));
        }

        public void lanzarSiHay() {
            if (!fallas.isEmpty()) {
                throw new ValidacionException(fallas);
            }
        }
    }
}
