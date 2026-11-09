package co.porteriaya.rag.documento;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de un documento (§6).
 *
 * Subido -> Indexando -> Indexado | Error, mas los dos reintentos:
 * Error -> Indexando y Indexado -> Indexando (reindexar o version nueva).
 */
public enum EstadoDocumento {

    SUBIDO,
    INDEXANDO,
    INDEXADO,
    ERROR;

    private static final Map<EstadoDocumento, Set<EstadoDocumento>> TRANSICIONES;

    static {
        Map<EstadoDocumento, Set<EstadoDocumento>> mapa = new EnumMap<>(EstadoDocumento.class);
        mapa.put(SUBIDO, EnumSet.of(INDEXANDO, ERROR));
        mapa.put(INDEXANDO, EnumSet.of(INDEXADO, ERROR));
        mapa.put(INDEXADO, EnumSet.of(INDEXANDO));
        mapa.put(ERROR, EnumSet.of(INDEXANDO));
        TRANSICIONES = Collections.unmodifiableMap(mapa);
    }

    public boolean puedePasarA(EstadoDocumento destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public Set<EstadoDocumento> destinosPermitidos() {
        return TRANSICIONES.get(this);
    }
}
