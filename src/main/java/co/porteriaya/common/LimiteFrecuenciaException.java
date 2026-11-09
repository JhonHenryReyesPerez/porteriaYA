package co.porteriaya.common;

/** 429: el actor supero el limite de frecuencia, p. ej. en /api/rag/preguntar (§16). */
public class LimiteFrecuenciaException extends PorteriaException {

    public LimiteFrecuenciaException(String mensaje) {
        super("LIMITE_FRECUENCIA", mensaje);
    }
}
