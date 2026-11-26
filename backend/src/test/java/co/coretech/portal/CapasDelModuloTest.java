package co.coretech.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Las capas DENTRO de cada módulo (docs/06-arquitectura.md, reglas de dependencia). {@link ModularidadTest} vigila los límites
 * ENTRE módulos; esta vigila que dentro de uno se respete el sentido: controller → service → repository, y todas pueden usar domain.
 * <ul>
 *   <li>{@code controller} no importa {@code repository}: un controlador o un DTO nunca toca un repositorio ni sus tipos.</li>
 *   <li>{@code service} no importa {@code controller} ni {@code dto}; {@code dto} no importa {@code repository} ni {@code controller}.</li>
 *   <li>{@code repository} y {@code domain} no importan {@code service} ni {@code controller}: lo que comparten las capas
 *       (una fila, una fase de plantilla) vive en {@code domain}.</li>
 * </ul>
 * Lee el código fuente, así que no necesita contexto de Spring ni base de datos.
 */
class CapasDelModuloTest {

    private static final Path RAIZ = Path.of("src/main/java/co/coretech/portal");
    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?co\\.coretech\\.portal\\.(\\w+)\\.(\\w+)\\.", Pattern.MULTILINE);

    /** capa de quien importa → capas que NO puede importar (del mismo módulo) */
    private static final Map<String, List<String>> PROHIBIDO = Map.of(
            "controller", List.of("repository"),
            "service", List.of("controller", "dto"),
            "dto", List.of("repository", "controller"),
            "repository", List.of("service", "controller", "dto"),
            "domain", List.of("service", "controller", "repository", "dto"));

    @Test
    void ningunaCapaImportaLoQueNoDebe() throws IOException {
        List<String> infracciones = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(RAIZ)) {
            for (Path p : archivos.filter(f -> f.toString().endsWith(".java")).toList()) {
                Path relativo = RAIZ.relativize(p);
                if (relativo.getNameCount() < 3) {
                    continue; // clases de la raíz del módulo (puertos y eventos) y del paquete portal
                }
                String modulo = relativo.getName(0).toString();
                String capa = relativo.getName(1).toString();
                List<String> prohibidas = PROHIBIDO.get(capa);
                if (prohibidas == null) {
                    continue;
                }
                Matcher m = IMPORT.matcher(Files.readString(p));
                while (m.find()) {
                    // Solo cuenta el módulo propio: entre módulos ya lo vigila ModularidadTest
                    if (m.group(1).equals(modulo) && prohibidas.contains(m.group(2))) {
                        infracciones.add(modulo + "/" + capa + "/" + p.getFileName() + " importa " + m.group(2) + " (" + m.group(0).trim() + ")");
                    }
                }
            }
        }
        assertThat(infracciones).as("imports que van contra el sentido de las capas").isEmpty();
    }

    @Test
    void seEncontraronLosModulos() throws IOException {
        try (Stream<Path> archivos = Files.walk(RAIZ)) {
            assertThat(archivos.filter(f -> f.toString().endsWith(".java")).count()).as("clases analizadas").isGreaterThan(200);
        }
    }
}
