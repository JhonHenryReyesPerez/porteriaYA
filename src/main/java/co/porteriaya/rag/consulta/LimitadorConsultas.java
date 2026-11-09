package co.porteriaya.rag.consulta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import co.porteriaya.common.LimiteFrecuenciaException;
import co.porteriaya.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Limita las preguntas por actor y por minuto (§16, → 429).
 *
 * Es una ventana deslizante en memoria: suficiente para controlar costo y
 * abuso con una sola instancia, que es lo asumido (§20). Y es evadible
 * cambiando X-Actor, igual que todo lo que se apoya en esa cabecera; eso ya
 * esta dicho como limitacion conocida en §16.
 */
@Component
@RequiredArgsConstructor
public class LimitadorConsultas {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final RagProperties propiedades;
    private final Clock clock;
    private final Map<String, Deque<Instant>> registro = new ConcurrentHashMap<>();

    public void comprobar(String actor) {
        int maximo = propiedades.consultas().maxPorMinutoPorActor();
        Instant ahora = clock.instant();
        Instant limite = ahora.minus(VENTANA);

        Deque<Instant> marcas = registro.computeIfAbsent(
                actor == null ? "anonimo" : actor, k -> new ArrayDeque<>());

        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst().isBefore(limite)) {
                marcas.pollFirst();
            }
            if (marcas.size() >= maximo) {
                throw new LimiteFrecuenciaException(
                        "Demasiadas preguntas seguidas. El limite es %d por minuto."
                                .formatted(maximo));
            }
            marcas.addLast(ahora);
        }
    }
}
