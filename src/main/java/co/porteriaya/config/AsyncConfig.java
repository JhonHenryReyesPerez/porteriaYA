package co.porteriaya.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Los eventos hacia n8n se publican con @Async (§10): si n8n esta caido, el
 * registro de porteria se guarda igual y el fallo solo queda en logs.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String EJECUTOR_EVENTOS = "ejecutorEventos";

    // Dimensionado de infraestructura, no umbrales de negocio: no va en propiedades.
    private static final int HILOS_MINIMOS = 2;
    private static final int HILOS_MAXIMOS = 4;
    private static final int CAPACIDAD_COLA = 100;

    @Bean(EJECUTOR_EVENTOS)
    public Executor ejecutorEventos() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(HILOS_MINIMOS);
        ejecutor.setMaxPoolSize(HILOS_MAXIMOS);
        ejecutor.setQueueCapacity(CAPACIDAD_COLA);
        ejecutor.setThreadNamePrefix("evento-");
        ejecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ejecutor.initialize();
        return ejecutor;
    }
}
