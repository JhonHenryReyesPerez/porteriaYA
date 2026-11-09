package co.porteriaya.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Unico lugar del sistema donde se construye un reloj (§9). El resto del codigo
 * inyecta {@link Clock} y nunca llama a Instant.now() ni LocalDateTime.now().
 */
@Configuration
public class ClockConfig {

    /** Zona horaria de todo el sistema (§2). */
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Bean
    @Profile("!demo")
    public Clock clock() {
        return Clock.system(ZONA);
    }

    /**
     * En demo el reloj es simulado y se adelanta con POST /api/demo/avanzar.
     * Se expone como {@link RelojDemo} para que el controlador de demo pueda
     * moverlo, y satisface igual cualquier inyeccion de {@link Clock}.
     */
    @Bean
    @Profile("demo")
    public RelojDemo clockDemo() {
        return new RelojDemo(Clock.system(ZONA));
    }
}
