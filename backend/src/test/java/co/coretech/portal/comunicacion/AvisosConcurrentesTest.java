package co.coretech.portal.comunicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.zaxxer.hikari.HikariDataSource;

import co.coretech.portal.ContenedoresPrueba;

/**
 * RNF-04 (hallazgo de la prueba de carga): publicar un aviso no debe pedir una SEGUNDA conexión a la base mientras la
 * petición todavía tiene la primera. Con el aviso guardado en una transacción aparte, cada petición retenía dos
 * conexiones a la vez; con el pool de 10 conexiones, diez peticiones simultáneas que avisaban se bloqueaban unas a otras
 * hasta que vencía la espera de 30 s: respuestas de 30 s y avisos perdidos.
 * <p>
 * Aquí 25 clientes se registran a la vez (cada registro avisa de la bienvenida) contra el pool de pruebas. Todos deben
 * terminar en pocos segundos y todos deben quedar con su aviso guardado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class AvisosConcurrentesTest {

    static final int SIMULTANEOS = 25;
    static final Duration LIMITE = Duration.ofSeconds(15);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    HikariDataSource pool;

    @Test
    void variosRegistrosALaVezNoAgotanElPoolNiPierdenAvisos() throws Exception {
        assertThat(SIMULTANEOS).as("más peticiones que conexiones: si cada una retiene dos, se bloquean").isGreaterThan(pool.getMaximumPoolSize());
        List<String> correos = new ArrayList<>();
        for (int i = 0; i < SIMULTANEOS; i++) {
            correos.add("avisos-" + UUID.randomUUID().toString().substring(0, 10) + "@correo.com");
        }
        ExecutorService hilos = Executors.newFixedThreadPool(SIMULTANEOS);
        CountDownLatch salida = new CountDownLatch(1);
        long inicio = System.nanoTime();
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (String correo : correos) {
                respuestas.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                            {"nombre":"Cliente Concurrente","email":"%s","celular":"3001234567","empresa":"","password":"Segura2026!","confirmacion":"Segura2026!","aceptaDatos":true}
                            """.formatted(correo))).andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            for (Future<Integer> r : respuestas) {
                assertThat(r.get(LIMITE.toSeconds() * 2, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            hilos.shutdownNow();
        }
        Duration total = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(total).as("tiempo en terminar los %d registros simultáneos", SIMULTANEOS).isLessThan(LIMITE);
        Integer conAviso = jdbc.queryForObject("""
                SELECT count(*) FROM notificaciones n JOIN usuarios u ON u.id = n.usuario_id
                WHERE n.tipo = 'CUENTA_CREADA' AND u.email = ANY (?)
                """, Integer.class, (Object) correos.toArray(new String[0]));
        assertThat(conAviso).as("clientes con su aviso de bienvenida guardado").isEqualTo(SIMULTANEOS);
    }
}
