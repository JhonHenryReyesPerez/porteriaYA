package co.porteriaya.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza nombres para comparar (§6, coincidencia con la lista de no
 * permitidos): minusculas, sin acentos y con los espacios colapsados.
 * "Jose  Perez" y "jose perez" quedan iguales.
 */
public final class Normalizador {

    private Normalizador() {
    }

    public static String nombre(String valor) {
        if (valor == null) {
            return null;
        }
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
