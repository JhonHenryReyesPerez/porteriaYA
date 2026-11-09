package co.porteriaya.rag.vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.porteriaya.config.RagProperties;
import co.porteriaya.rag.documento.Audiencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Qdrant por su API REST (§11).
 *
 * Se habla HTTP directo con RestClient en vez de añadir el cliente oficial:
 * son cuatro llamadas y §17 pide justificar cada dependencia nueva.
 */
@Component
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    /** Para que los ids de punto no choquen entre chunks de documentos distintos. */
    private static final long FACTOR_ID = 100_000L;

    private final RestClient cliente;
    private final RagProperties propiedades;

    public QdrantVectorStore(RagProperties propiedades) {
        this.propiedades = propiedades;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        this.cliente = RestClient.builder()
                .baseUrl(propiedades.vector().url())
                .requestFactory(fabrica)
                .build();
    }

    private String coleccion() {
        return propiedades.vector().coleccionReglamento();
    }

    @Override
    public void asegurarColeccion() {
        try {
            cliente.get().uri("/collections/{c}", coleccion()).retrieve().toBodilessEntity();
            log.debug("La coleccion {} ya existe", coleccion());
        } catch (Exception noExiste) {
            cliente.put()
                    .uri("/collections/{c}", coleccion())
                    .body(Map.of("vectors", Map.of(
                            "size", propiedades.embedding().dimension(),
                            "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Coleccion {} creada con {} dimensiones y distancia coseno",
                    coleccion(), propiedades.embedding().dimension());
        }
    }

    @Override
    public void guardar(List<ChunkVectorizado> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        List<Map<String, Object>> puntos = new ArrayList<>();
        for (ChunkVectorizado chunk : chunks) {
            puntos.add(Map.of(
                    "id", chunk.documentoId() * FACTOR_ID + chunk.chunkIdx(),
                    "vector", chunk.vector(),
                    "payload", payloadDe(chunk)));
        }

        cliente.put()
                .uri("/collections/{c}/points?wait=true", coleccion())
                .body(Map.of("points", puntos))
                .retrieve()
                .toBodilessEntity();

        log.info("{} chunks guardados en {}", chunks.size(), coleccion());
    }

    private Map<String, Object> payloadDe(ChunkVectorizado chunk) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("documentoId", chunk.documentoId());
        payload.put("titulo", chunk.titulo());
        payload.put("chunkIdx", chunk.chunkIdx());
        payload.put("audiencias", chunk.audiencias());
        payload.put("texto", chunk.texto());
        if (chunk.pagina() != null) {
            payload.put("pagina", chunk.pagina());
        }
        if (chunk.version() != null) {
            payload.put("version", chunk.version());
        }
        if (chunk.fecha() != null) {
            payload.put("fecha", chunk.fecha());
        }
        return payload;
    }

    @Override
    public void borrarPorDocumento(Long documentoId) {
        cliente.post()
                .uri("/collections/{c}/points/delete?wait=true", coleccion())
                .body(Map.of("filter", Map.of("must", List.of(
                        Map.of("key", "documentoId", "match", Map.of("value", documentoId))))))
                .retrieve()
                .toBodilessEntity();
        log.info("Chunks del documento {} borrados de {}", documentoId, coleccion());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ResultadoBusqueda> buscar(List<Float> vector, Set<Audiencia> audiencias, int topK) {
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
        cuerpo.put("vector", vector);
        cuerpo.put("limit", topK);
        cuerpo.put("with_payload", true);

        // Administracion ve todas las audiencias, asi que no se filtra
        if (!audiencias.containsAll(Set.of(Audiencia.values()))) {
            cuerpo.put("filter", Map.of("must", List.of(
                    Map.of("key", "audiencias", "match",
                            Map.of("any", Audiencia.nombres(audiencias))))));
        }

        Map<String, Object> respuesta = cliente.post()
                .uri("/collections/{c}/points/search", coleccion())
                .body(cuerpo)
                .retrieve()
                .body(Map.class);

        if (respuesta == null || respuesta.get("result") == null) {
            return List.of();
        }

        List<Map<String, Object>> resultados = (List<Map<String, Object>>) respuesta.get("result");
        return resultados.stream().map(this::aResultado).toList();
    }

    @SuppressWarnings("unchecked")
    private ResultadoBusqueda aResultado(Map<String, Object> punto) {
        Map<String, Object> payload = (Map<String, Object>) punto.get("payload");
        Number similitud = (Number) punto.get("score");
        Number pagina = (Number) payload.get("pagina");
        Number documentoId = (Number) payload.get("documentoId");
        Number chunkIdx = (Number) payload.get("chunkIdx");

        return new ResultadoBusqueda(
                documentoId == null ? null : documentoId.longValue(),
                (String) payload.get("titulo"),
                pagina == null ? null : pagina.intValue(),
                chunkIdx == null ? 0 : chunkIdx.intValue(),
                (String) payload.get("texto"),
                similitud == null ? 0 : similitud.doubleValue());
    }

    @Override
    public boolean disponible() {
        try {
            cliente.get().uri("/healthz").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("Qdrant no responde: {}", e.getMessage());
            return false;
        }
    }
}
