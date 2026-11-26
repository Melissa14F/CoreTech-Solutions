package co.coretech.portal.compartido.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Archivos que suben los usuarios (imágenes de los servicios del catálogo; en P18, los adjuntos de los
 * pasos). Se guardan en una carpeta (un volumen de Docker en producción), nunca en la base de datos.
 * <p>
 * El nombre de cada archivo lo genera el almacén (UUID + extensión): nunca se usa el nombre que trae el
 * usuario, así no puede salir de su carpeta ni pisar otro archivo.
 */
public interface AlmacenArchivos {

    /** Tipos de imagen que se aceptan, reconocidos por su contenido (no por la extensión que dice el usuario). */
    enum TipoImagen {
        WEBP("webp", "image/webp"),
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg");

        private final String extension;
        private final String tipoMime;

        TipoImagen(String extension, String tipoMime) {
            this.extension = extension;
            this.tipoMime = tipoMime;
        }

        public String extension() {
            return extension;
        }

        public String tipoMime() {
            return tipoMime;
        }

        /** Reconoce la imagen por sus primeros bytes ("firma"); vacío si no es WEBP, PNG ni JPG. */
        public static Optional<TipoImagen> reconocer(byte[] contenido) {
            if (contenido.length >= 12 && contenido[0] == 'R' && contenido[1] == 'I' && contenido[2] == 'F' && contenido[3] == 'F'
                    && contenido[8] == 'W' && contenido[9] == 'E' && contenido[10] == 'B' && contenido[11] == 'P') {
                return Optional.of(WEBP);
            }
            if (contenido.length >= 8 && (contenido[0] & 0xFF) == 0x89 && contenido[1] == 'P' && contenido[2] == 'N' && contenido[3] == 'G') {
                return Optional.of(PNG);
            }
            if (contenido.length >= 3 && (contenido[0] & 0xFF) == 0xFF && (contenido[1] & 0xFF) == 0xD8 && (contenido[2] & 0xFF) == 0xFF) {
                return Optional.of(JPEG);
            }
            return Optional.empty();
        }

        /** Tipo de un archivo guardado, por la extensión que le puso el almacén. */
        public static Optional<TipoImagen> deNombre(String nombre) {
            for (TipoImagen tipo : values()) {
                if (nombre.endsWith("." + tipo.extension)) {
                    return Optional.of(tipo);
                }
            }
            return Optional.empty();
        }
    }

    /** Un archivo leído del almacén. */
    record Archivo(byte[] contenido, String tipoMime) {
    }

    /**
     * Guarda el contenido en la carpeta indicada ("servicios", "adjuntos"…) con un nombre nuevo.
     *
     * @return el nombre del archivo guardado (por ejemplo "3f2a…c1.webp")
     */
    String guardar(String carpeta, byte[] contenido, String extension);

    /** El archivo, si existe y el nombre tiene el formato que genera el almacén. */
    Optional<Archivo> leer(String carpeta, String nombre, String tipoMime);

    boolean existe(String carpeta, String nombre);

    /** Un archivo guardado y cuándo se escribió (para las tareas de limpieza). */
    record Guardado(String nombre, Instant modificado) {
    }

    /** Los archivos que generó el almacén en la carpeta (vacío si la carpeta no existe). */
    List<Guardado> listar(String carpeta);

    /** Borra el archivo si existe; devuelve si lo borró. */
    boolean borrar(String carpeta, String nombre);
}
