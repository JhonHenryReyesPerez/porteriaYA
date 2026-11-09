package co.porteriaya.evento;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un evento listo para enviarse a n8n.
 *
 * El payload lleva solo lo necesario para el mensaje (§10): nombre, apartamento
 * y, cuando el flujo lo necesita, el codigo de la autorizacion. Nunca un
 * documento completo, y nunca texto de documentos del RAG.
 */
public record EventoPorteria(TipoEvento tipo, Instant ocurridoEn, Map<String, Object> datos) {

    public EventoPorteria {
        datos = Map.copyOf(datos);
    }

    /** Constructor de payloads con pares clave-valor, saltando los nulos. */
    public static Map<String, Object> datos(Object... paresClaveValor) {
        if (paresClaveValor.length % 2 != 0) {
            throw new IllegalArgumentException("Los datos del evento van en pares clave-valor");
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            Object valor = paresClaveValor[i + 1];
            if (valor != null) {
                mapa.put(String.valueOf(paresClaveValor[i]), valor);
            }
        }
        return mapa;
    }
}
