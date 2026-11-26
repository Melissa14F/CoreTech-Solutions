package co.coretech.portal.comunicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RNF-13: "el 95 % de los correos sale en menos de 1 minuto desde el cambio". Mide el tiempo real, de punta a punta:
 * desde que el cliente se registra (el cambio que genera el aviso de bienvenida) hasta que el correo llega a un
 * servidor SMTP de verdad (Mailpit, el de docker-compose en el puerto 1025). Si Mailpit no está encendido, la prueba
 * se omite en vez de fallar. El resultado queda en {@code target/medicion-correos-<modo>.txt}.
 */
abstract class MedicionCorreosBase {

    static final int CORREOS = 20;
    static final Duration LIMITE = Duration.ofMinutes(1);
    static final String MAILPIT = "http://localhost:8025/api/v1/messages?limit=200";

    @Autowired
    MockMvc mvc;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    abstract String modo();

    void medir() throws Exception {
        assumeTrue(mailpitEncendido(), "Mailpit no responde en localhost:8025 (docker compose up -d en prototipo/)");

        // 20 cambios seguidos, cada uno con su instante de partida
        Map<String, Long> salida = new HashMap<>();
        for (int i = 0; i < CORREOS; i++) {
            String email = "medicion-" + UUID.randomUUID().toString().substring(0, 10) + "@empresa-cliente.com";
            long inicio = System.nanoTime();
            mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                    {"nombre":"Sofía Duarte","email":"%s","celular":"3142208815","empresa":"","password":"Segura2026!","confirmacion":"Segura2026!","aceptaDatos":true}
                    """.formatted(email))).andExpect(status().isCreated());
            salida.put(email, inicio);
        }

        // Se consulta la bandeja hasta que lleguen todos (o pase el doble del límite) y se anota cuándo llegó cada uno
        Map<String, Long> llegada = new HashMap<>();
        long tope = System.nanoTime() + LIMITE.multipliedBy(2).toNanos();
        while (llegada.size() < CORREOS && System.nanoTime() < tope) {
            String bandeja = http.send(HttpRequest.newBuilder(URI.create(MAILPIT)).build(), HttpResponse.BodyHandlers.ofString()).body();
            long ahora = System.nanoTime();
            salida.keySet().stream().filter(e -> !llegada.containsKey(e) && bandeja.contains("\"" + e + "\""))
                    .forEach(e -> llegada.put(e, ahora));
            Thread.sleep(250);
        }

        List<Long> segundosPorCorreo = new ArrayList<>();
        for (Map.Entry<String, Long> e : salida.entrySet()) {
            Long l = llegada.get(e.getKey());
            segundosPorCorreo.add(l == null ? Long.MAX_VALUE : Duration.ofNanos(l - e.getValue()).toMillis());
        }
        segundosPorCorreo.sort(Long::compare);
        long p95 = segundosPorCorreo.get((int) Math.ceil(CORREOS * 0.95) - 1);
        long maximo = segundosPorCorreo.get(CORREOS - 1);
        long dentro = segundosPorCorreo.stream().filter(ms -> ms < LIMITE.toMillis()).count();

        String resumen = "modo=%s correos=%d llegaron=%d dentro_de_1_min=%d p95_ms=%s max_ms=%s%n".formatted(
                modo(), CORREOS, llegada.size(), dentro, p95 == Long.MAX_VALUE ? "nunca" : p95, maximo == Long.MAX_VALUE ? "nunca" : maximo);
        Path destino = Path.of("target", "medicion-correos-" + modo() + ".txt");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, resumen);
        System.out.print(resumen);

        assertThat(llegada).as("correos que llegaron a Mailpit").hasSize(CORREOS);
        assertThat(dentro * 100.0 / CORREOS).as("% de correos en menos de 1 minuto").isGreaterThanOrEqualTo(95.0);
        assertThat(p95).as("P95 en milisegundos").isLessThan(LIMITE.toMillis());
    }

    private boolean mailpitEncendido() {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(MAILPIT)).timeout(Duration.ofSeconds(2)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
