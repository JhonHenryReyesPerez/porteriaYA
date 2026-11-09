package co.porteriaya.rag.embedding;

import java.util.List;

/**
 * Convierte texto en vectores (§11).
 *
 * El resto del codigo no sabe que detras hay Ollama. Cambiar de proveedor es
 * cambiar esta implementacion y una propiedad.
 *
 * Hay dos metodos y no uno porque los modelos de embeddings modernos esperan
 * un prefijo distinto segun si el texto es un documento que se indexa o una
 * pregunta que se busca. Dejarlo a eleccion del llamador seria pedirle que se
 * acuerde de un detalle del modelo.
 */
public interface EmbeddingClient {

    List<Float> paraDocumento(String texto);

    List<Float> paraConsulta(String pregunta);

    /** Dimension que produce el modelo. Se verifica al arrancar (§11). */
    int dimension();
}
