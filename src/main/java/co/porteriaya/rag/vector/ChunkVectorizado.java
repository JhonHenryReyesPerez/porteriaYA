package co.porteriaya.rag.vector;

import java.util.List;

/** Un trozo de documento listo para guardar en la base vectorial (§11, payload). */
public record ChunkVectorizado(
        Long documentoId,
        int chunkIdx,
        String titulo,
        Integer pagina,
        List<String> audiencias,
        String version,
        String fecha,
        String texto,
        List<Float> vector
) {
}
