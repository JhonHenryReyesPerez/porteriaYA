package co.porteriaya.rag.documento;

import java.util.ArrayList;
import java.util.List;

import co.porteriaya.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Parte el texto en trozos solapados (§11).
 *
 * Se mide en caracteres, no en tokens: la aproximacion de 4 caracteres por
 * token evita meter un tokenizador en el stack, y el limite real lo pone el
 * contexto del modelo de embeddings, que es holgado.
 *
 * Corta preferentemente en un salto de parrafo o de linea cercano al final del
 * trozo, para no partir una frase o un titulo por la mitad.
 */
@Component
@RequiredArgsConstructor
public class Chunker {

    /** Margen donde se busca un corte natural antes del limite duro. */
    private static final int MARGEN_CORTE = 300;

    private final RagProperties propiedades;

    public List<String> partir(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        String limpio = texto.replace("\r\n", "\n").strip();
        int tamano = propiedades.chunk().caracteres();
        int solape = propiedades.chunk().solape();

        List<String> trozos = new ArrayList<>();
        int inicio = 0;

        while (inicio < limpio.length()) {
            int finDuro = Math.min(inicio + tamano, limpio.length());
            int fin = finDuro < limpio.length() ? buscarCorteNatural(limpio, inicio, finDuro) : finDuro;

            String trozo = limpio.substring(inicio, fin).strip();
            if (!trozo.isEmpty()) {
                trozos.add(trozo);
            }

            if (fin >= limpio.length()) {
                break;
            }
            // El solape conserva contexto entre trozos consecutivos
            inicio = Math.max(fin - solape, inicio + 1);
        }
        return trozos;
    }

    /** Busca hacia atras un salto de parrafo, de linea o un punto. */
    private int buscarCorteNatural(String texto, int inicio, int finDuro) {
        int minimo = Math.max(inicio + 1, finDuro - MARGEN_CORTE);

        int parrafo = texto.lastIndexOf("\n\n", finDuro);
        if (parrafo >= minimo) {
            return parrafo;
        }
        int linea = texto.lastIndexOf('\n', finDuro);
        if (linea >= minimo) {
            return linea;
        }
        int punto = texto.lastIndexOf(". ", finDuro);
        if (punto >= minimo) {
            return punto + 1;
        }
        return finDuro;
    }
}
