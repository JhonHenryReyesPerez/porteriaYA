package co.porteriaya.rag.vector;

import java.util.List;
import java.util.Set;

import co.porteriaya.rag.documento.Audiencia;

/**
 * Base vectorial (§11). El resto del codigo no sabe que detras hay Qdrant.
 *
 * PostgreSQL es la fuente de verdad; esto es una copia derivada que
 * `reindexar` puede reconstruir, asi que perderla no pierde nada.
 */
public interface VectorStore {

    /** Crea la coleccion si no existe, con la dimension y distancia de §11. */
    void asegurarColeccion();

    void guardar(List<ChunkVectorizado> chunks);

    /** Al subir una version nueva se borran primero los chunks anteriores (§11). */
    void borrarPorDocumento(Long documentoId);

    /**
     * Busca los chunks mas parecidos, filtrando por audiencia.
     *
     * El filtro va aqui y no en el codigo que llama: si el filtrado fuera
     * posterior, un residente podria recibir menos de topK resultados sin
     * saberlo, o peor, alguien podria olvidarse de filtrar.
     */
    List<ResultadoBusqueda> buscar(List<Float> vector, Set<Audiencia> audiencias, int topK);

    boolean disponible();
}
