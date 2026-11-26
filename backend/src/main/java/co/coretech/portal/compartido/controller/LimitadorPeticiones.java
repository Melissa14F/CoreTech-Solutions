package co.coretech.portal.compartido.controller;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante en memoria: cuántas veces una clave (ruta + IP) hizo algo en el último periodo.
 * Basta para un solo servidor (Docker Compose, P25); con varios habría que llevarlo a la base o a Redis.
 */
class LimitadorPeticiones {

    /** Por encima de este número de claves se limpian las que ya no tienen intentos recientes. */
    private static final int LIMPIAR_DESDE = 10_000;

    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();
    private final Clock reloj;

    LimitadorPeticiones(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Cuenta un intento. Si ya se alcanzó el máximo en la ventana, no lo cuenta y devuelve cuánto falta
     * para poder intentar de nuevo.
     */
    Optional<Duration> intentar(String clave, int maximo, Duration ventana) {
        Instant ahora = Instant.now(reloj);
        if (intentos.size() > LIMPIAR_DESDE) {
            limpiar(ahora, ventana);
        }
        Deque<Instant> lista = intentos.computeIfAbsent(clave, c -> new ArrayDeque<>());
        synchronized (lista) {
            while (!lista.isEmpty() && !lista.peekFirst().isAfter(ahora.minus(ventana))) {
                lista.pollFirst();
            }
            if (lista.size() >= maximo) {
                return Optional.of(Duration.between(ahora, lista.peekFirst().plus(ventana)));
            }
            lista.addLast(ahora);
            return Optional.empty();
        }
    }

    private void limpiar(Instant ahora, Duration ventana) {
        intentos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || !e.getValue().peekLast().isAfter(ahora.minus(ventana));
            }
        });
    }
}
