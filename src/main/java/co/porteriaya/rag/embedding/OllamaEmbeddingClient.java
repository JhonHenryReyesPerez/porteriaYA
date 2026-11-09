package co.porteriaya.rag.embedding;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import co.porteriaya.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Embeddings con Ollama (§11).
 *
 * `nomic-embed-text` exige prefijos de tarea: `search_document:` al indexar y
 * `search_query:` al preguntar. Omitirlos degrada el recall de forma silenciosa,
 * que es la peor manera de romper un RAG: todo parece funcionar y las
 * respuestas simplemente son peores.
 */
@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);
    private static final String PREFIJO_DOCUMENTO = "search_document: ";
    private static final String PREFIJO_CONSULTA = "search_query: ";

    private final RestClient cliente;
    private final RagProperties propiedades;

    public OllamaEmbeddingClient(RagProperties propiedades) {
        this.propiedades = propiedades;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(60));
        this.cliente = RestClient.builder()
                .baseUrl(propiedades.embedding().url())
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public List<Float> paraDocumento(String texto) {
        return embeber(PREFIJO_DOCUMENTO + texto);
    }

    @Override
    public List<Float> paraConsulta(String pregunta) {
        return embeber(PREFIJO_CONSULTA + pregunta);
    }

    @Override
    public int dimension() {
        return propiedades.embedding().dimension();
    }

    @SuppressWarnings("unchecked")
    private List<Float> embeber(String texto) {
        Map<String, Object> respuesta = cliente.post()
                .uri("/api/embeddings")
                .body(Map.of("model", propiedades.embedding().modelo(), "prompt", texto))
                .retrieve()
                .body(Map.class);

        if (respuesta == null || respuesta.get("embedding") == null) {
            throw new IllegalStateException("Ollama no devolvio ningun embedding");
        }
        List<Number> crudo = (List<Number>) respuesta.get("embedding");
        return crudo.stream().map(Number::floatValue).toList();
    }

    /**
     * §11 pide verificar la dimension al arrancar. Se avisa y no se revienta:
     * si Ollama no esta levantado (va en el perfil `rag` de Compose), la
     * porteria tiene que seguir funcionando igual.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verificarDimension() {
        try {
            int real = paraConsulta("comprobacion de arranque").size();
            if (real != dimension()) {
                log.error("El modelo {} devuelve {} dimensiones y la configuracion espera {}. "
                                + "Hay que reindexar o corregir rag.embedding.dimension",
                        propiedades.embedding().modelo(), real, dimension());
            } else {
                log.info("Embeddings listos: {} con {} dimensiones",
                        propiedades.embedding().modelo(), real);
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar la dimension de los embeddings: {}. "
                    + "El asistente respondera 'servicio no disponible' hasta que Ollama este arriba",
                    e.getMessage());
        }
    }
}
