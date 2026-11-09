package co.porteriaya.rag.llm;

import java.time.Duration;
import java.util.Map;

import co.porteriaya.config.RagProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * LLM local por Ollama. Se activa con rag.llm.proveedor=ollama.
 *
 * En CPU va lento: para una sustentacion en vivo es mas seguro `claude`.
 */
@Component
@ConditionalOnProperty(name = "rag.llm.proveedor", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    private final RestClient cliente;
    private final RagProperties propiedades;

    public OllamaLlmClient(RagProperties propiedades) {
        this.propiedades = propiedades;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        // Generar en CPU es lento: el timeout de lectura tiene que ser holgado
        fabrica.setReadTimeout(Duration.ofSeconds(180));
        this.cliente = RestClient.builder()
                .baseUrl(propiedades.embedding().url())
                .requestFactory(fabrica)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String responder(String promptSistema, String promptUsuario) {
        Map<String, Object> respuesta = cliente.post()
                .uri("/api/chat")
                .body(Map.of(
                        "model", propiedades.llm().modelo(),
                        "stream", false,
                        "options", Map.of(
                                "temperature", propiedades.llm().temperatura(),
                                "num_predict", propiedades.llm().maxTokens()),
                        "messages", java.util.List.of(
                                Map.of("role", "system", "content", promptSistema),
                                Map.of("role", "user", "content", promptUsuario))))
                .retrieve()
                .body(Map.class);

        if (respuesta == null || respuesta.get("message") == null) {
            throw new IllegalStateException("Ollama no devolvio ninguna respuesta");
        }
        Map<String, Object> mensaje = (Map<String, Object>) respuesta.get("message");
        return String.valueOf(mensaje.get("content"));
    }

    @Override
    public String proveedor() {
        return "ollama/" + propiedades.llm().modelo();
    }
}
