package co.porteriaya.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas periodicas de §9. Las tareas son idempotentes y usan el
 * {@link java.time.Clock} inyectado, nunca Thread.sleep.
 *
 * Asume una sola instancia de la app: no hay bloqueo distribuido (§20.4).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
