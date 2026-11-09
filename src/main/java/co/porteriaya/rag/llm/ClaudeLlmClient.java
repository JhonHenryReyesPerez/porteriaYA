package co.porteriaya.rag.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import co.porteriaya.config.RagProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * LLM por la API de Claude. Se activa con rag.llm.proveedor=claude.
 *
 * Es la opcion recomendada para la sustentacion: no depende de la RAM ni la
 * CPU de la maquina, aunque si de la red y de una clave en el .env.
 */
@Component
@ConditionalOnProperty(name = "rag.llm.proveedor", havingValue = "claude")
public class ClaudeLlmClient implements LlmClient {

    private static final String URL = "https://api.anthropic.com";
    private static final String VERSION_API = "2023-06-01";

    private final RestClient cliente;
    private final RagProperties propiedades;

    public ClaudeLlmClient(RagProperties propiedades) {
        this.propiedades = propiedades;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(60));
        this.cliente = RestClient.builder()
                .baseUrl(URL)
                .requestFactory(fabrica)
                .defaultHeader("x-api-key", propiedades.llm().apiKey())
                .defaultHeader("anthropic-version", VERSION_API)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String responder(String promptSistema, String promptUsuario) {
        Map<String, Object> respuesta = cliente.post()
                .uri("/v1/messages")
                .body(Map.of(
                        "model", propiedades.llm().modelo(),
                        "max_tokens", propiedades.llm().maxTokens(),
                        "temperature", propiedades.llm().temperatura(),
                        "system", promptSistema,
                        "messages", List.of(Map.of("role", "user", "content", promptUsuario))))
                .retrieve()
                .body(Map.class);

        if (respuesta == null || respuesta.get("content") == null) {
            throw new IllegalStateException("La API de Claude no devolvio contenido");
        }
        List<Map<String, Object>> bloques = (List<Map<String, Object>>) respuesta.get("content");
        return bloques.stream()
                .filter(b -> "text".equals(b.get("type")))
                .map(b -> String.valueOf(b.get("text")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Respuesta sin bloque de texto"));
    }

    @Override
    public String proveedor() {
        return "claude/" + propiedades.llm().modelo();
    }
}
