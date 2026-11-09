package co.porteriaya.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del RAG (§11). Las URLs de Ollama y Qdrant viven aqui para que
 * EmbeddingClient, VectorStore y LlmClient sean lo unico que las conozca.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        double umbral,
        int topK,
        Chunk chunk,
        Embedding embedding,
        Vector vector,
        Llm llm,
        Subida subida
) {

    /**
     * Chunking de §11. Se mide en caracteres, no en tokens: la aproximacion de
     * 4 caracteres por token evita meter un tokenizador en el stack.
     */
    public record Chunk(int caracteres, int solape) {
    }

    /** Mismo modelo y dimension para indexar y consultar; cambiarlo obliga a reindexar. */
    public record Embedding(String url, String modelo, int dimension) {
    }

    public record Vector(String url, String coleccionReglamento, String coleccionIncidentes) {
    }

    public record Llm(String proveedor, String modelo, double temperatura, int maxTokens, String apiKey) {
    }

    public record Subida(int maxMb, List<String> tiposPermitidos) {
    }
}
