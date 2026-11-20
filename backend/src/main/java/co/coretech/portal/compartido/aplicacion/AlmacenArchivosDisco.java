package co.coretech.portal.compartido.aplicacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import co.coretech.portal.compartido.PropiedadesCoretech;

/** Guarda los archivos en la carpeta de {@code coretech.archivos.directorio}. */
@Service
class AlmacenArchivosDisco implements AlmacenArchivos {

    /** Solo nombres generados por el almacén: UUID + extensión. Impide rutas como "../../etc/passwd". */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.[a-z0-9]{2,5}$");
    private static final Pattern CARPETA_VALIDA = Pattern.compile("^[a-z]+$");

    private final Path raiz;

    AlmacenArchivosDisco(PropiedadesCoretech propiedades) {
        this.raiz = Path.of(propiedades.archivos().directorio()).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(String carpeta, byte[] contenido, String extension) {
        String nombre = UUID.randomUUID() + "." + extension;
        Path destino = ruta(carpeta, nombre);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo en " + destino, e);
        }
        return nombre;
    }

    @Override
    public Optional<Archivo> leer(String carpeta, String nombre, String tipoMime) {
        if (!existe(carpeta, nombre)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Archivo(Files.readAllBytes(ruta(carpeta, nombre)), tipoMime));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo " + nombre, e);
        }
    }

    @Override
    public boolean existe(String carpeta, String nombre) {
        return nombre != null && NOMBRE_VALIDO.matcher(nombre).matches() && Files.isRegularFile(ruta(carpeta, nombre));
    }

    @Override
    public List<Guardado> listar(String carpeta) {
        Path directorio = raiz.resolve(validarCarpeta(carpeta));
        if (!Files.isDirectory(directorio)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(directorio)) {
            return archivos.filter(Files::isRegularFile)
                    .filter(a -> NOMBRE_VALIDO.matcher(a.getFileName().toString()).matches())
                    .map(a -> {
                        try {
                            return new Guardado(a.getFileName().toString(), Files.getLastModifiedTime(a).toInstant());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la carpeta " + carpeta, e);
        }
    }

    @Override
    public boolean borrar(String carpeta, String nombre) {
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            return false;
        }
        try {
            return Files.deleteIfExists(ruta(carpeta, nombre));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el archivo " + nombre, e);
        }
    }

    private static String validarCarpeta(String carpeta) {
        if (!CARPETA_VALIDA.matcher(carpeta).matches()) {
            throw new IllegalArgumentException("Carpeta no válida: " + carpeta);
        }
        return carpeta;
    }

    private Path ruta(String carpeta, String nombre) {
        if (!CARPETA_VALIDA.matcher(carpeta).matches()) {
            throw new IllegalArgumentException("Carpeta no válida: " + carpeta);
        }
        Path ruta = raiz.resolve(carpeta).resolve(nombre).normalize();
        if (!ruta.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta fuera del almacén: " + nombre);
        }
        return ruta;
    }
}
