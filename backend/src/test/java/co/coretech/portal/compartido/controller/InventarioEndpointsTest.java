package co.coretech.portal.compartido.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import co.coretech.portal.ContenedoresPrueba;

/**
 * RNF-01: "100 % de los endpoints cubiertos por la matriz de roles". Recorre TODOS los endpoints del portal y
 * comprueba que cada uno es público a propósito (lista de {@link ConfiguracionSeguridad}), pide solo sesión a
 * propósito (lista de abajo) o pide un permiso que existe en la tabla {@code permisos}. Un endpoint nuevo sin
 * {@code @PreAuthorize} hace fallar esta prueba. Además deja el inventario en
 * {@code target/inventario-endpoints.md} para anotarlo en PROGRESO.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class InventarioEndpointsTest {

    /** Endpoints que piden solo sesión iniciada (sin permiso concreto) y por qué. */
    private static final Map<String, String> SOLO_SESION = Map.of(
            "GET /api/v1/auth/yo", "datos de la propia sesión",
            "POST /api/v1/auth/cambiar-password", "cambia la contraseña de la propia cuenta",
            "GET /api/v1/notificaciones", "solo las del usuario de la sesión",
            "PATCH /api/v1/notificaciones/leidas", "solo las del usuario de la sesión",
            "PATCH /api/v1/notificaciones/{notificacionId}/leida", "la de otro usuario responde 404"
    );

    private static final Pattern PERMISO = Pattern.compile("@permisos\\.tiene\\('([a-z_.]+)'\\)");

    @Autowired
    RequestMappingHandlerMapping mapeo;

    @Autowired
    JdbcTemplate jdbc;

    private record Fila(String metodo, String ruta, String clase, String proteccion) { }

    @Test
    void todoEndpointEsPublicoAPropositoOPideUnPermisoQueExiste() throws Exception {
        AntPathMatcher ant = new AntPathMatcher();
        Set<String> permisosExistentes = new TreeSet<>(jdbc.queryForList("SELECT codigo FROM permisos", String.class));
        List<Fila> filas = new ArrayList<>();
        List<String> problemas = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapeo.getHandlerMethods().entrySet()) {
            HandlerMethod h = e.getValue();
            if (!h.getBeanType().getName().startsWith("co.coretech.portal")) {
                continue;
            }
            Set<String> rutas = e.getKey().getPathPatternsCondition() == null ? Set.of()
                    : e.getKey().getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> metodos = e.getKey().getMethodsCondition().getMethods();
            for (String ruta : rutas) {
                for (RequestMethod metodo : metodos.isEmpty() ? Set.of(RequestMethod.GET) : metodos) {
                    String clave = metodo + " " + ruta;
                    String proteccion;
                    if (esPublico(ant, metodo, ruta)) {
                        proteccion = "PÚBLICO";
                    } else {
                        PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(h.getMethod(), PreAuthorize.class);
                        if (pa == null) {
                            pa = AnnotatedElementUtils.findMergedAnnotation(h.getBeanType(), PreAuthorize.class);
                        }
                        if (pa == null) {
                            if (SOLO_SESION.containsKey(clave)) {
                                proteccion = "SOLO SESIÓN (" + SOLO_SESION.get(clave) + ")";
                            } else {
                                proteccion = "SIN PERMISO";
                                problemas.add(clave + " no tiene @PreAuthorize ni está en la lista de públicos o solo-sesión");
                            }
                        } else {
                            Matcher m = PERMISO.matcher(pa.value());
                            List<String> codigos = new ArrayList<>();
                            while (m.find()) {
                                codigos.add(m.group(1));
                            }
                            if (codigos.isEmpty()) {
                                proteccion = "EXPRESIÓN: " + pa.value();
                                problemas.add(clave + " usa una expresión sin permiso: " + pa.value());
                            } else {
                                proteccion = String.join(", ", codigos);
                                for (String c : codigos) {
                                    if (!permisosExistentes.contains(c)) {
                                        problemas.add(clave + " pide el permiso «" + c + "» que no existe en la tabla permisos");
                                    }
                                }
                            }
                        }
                    }
                    filas.add(new Fila(metodo.name(), ruta, h.getBeanType().getSimpleName(), proteccion));
                }
            }
        }

        escribirInventario(filas);
        assertThat(filas).as("endpoints encontrados").hasSizeGreaterThan(100);
        assertThat(problemas).as("endpoints sin permiso").isEmpty();
    }

    private static boolean esPublico(AntPathMatcher ant, RequestMethod metodo, String ruta) {
        List<String> patrones = switch (metodo) {
            case POST -> List.of(ConfiguracionSeguridad.PUBLICAS_POST);
            case GET -> List.of(ConfiguracionSeguridad.PUBLICAS_GET);
            default -> List.of();
        };
        return patrones.stream().anyMatch(p -> ant.match(p, ruta));
    }

    private static void escribirInventario(List<Fila> filas) throws Exception {
        StringBuilder sb = new StringBuilder("| Método | Ruta | Controlador | Protección |\n|---|---|---|---|\n");
        filas.stream()
                .sorted((a, b) -> (a.clase() + a.ruta() + a.metodo()).compareTo(b.clase() + b.ruta() + b.metodo()))
                .forEach(f -> sb.append("| ").append(f.metodo()).append(" | `").append(f.ruta()).append("` | ")
                        .append(f.clase()).append(" | ").append(f.proteccion()).append(" |\n"));
        Path destino = Path.of("target", "inventario-endpoints.md");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, sb.toString());
    }
}
