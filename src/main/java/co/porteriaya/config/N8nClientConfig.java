package co.porteriaya.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el webhook de n8n (§10).
 *
 * Los timeouts no son decorativos: un n8n que acepta la conexion y se queda
 * colgado agotaria los hilos del ejecutor de eventos, y eso si acabaria
 * afectando a la porteria. Con timeouts cortos, el peor caso es un log.
 */
@Configuration
public class N8nClientConfig {

    /** Cabecera compartida con n8n; el secreto vive en .env (§16). */
    public static final String CABECERA_SECRETO = "X-Porteria-Secret";

    @Bean
    public RestClient clienteN8n(PorteriaProperties propiedades, RestClient.Builder constructor) {
        PorteriaProperties.N8n n8n = propiedades.n8n();

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(n8n.timeoutConexionSegundos()));
        fabrica.setReadTimeout(Duration.ofSeconds(n8n.timeoutLecturaSegundos()));

        return constructor
                .baseUrl(n8n.webhookUrl())
                .requestFactory(fabrica)
                .defaultHeader(CABECERA_SECRETO, n8n.secret() == null ? "" : n8n.secret())
                .build();
    }
}
