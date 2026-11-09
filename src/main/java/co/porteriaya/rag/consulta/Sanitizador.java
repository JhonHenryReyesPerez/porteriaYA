package co.porteriaya.rag.consulta;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Limpia la pregunta antes de vectorizarla o guardarla (§11).
 *
 * Quita correos, teléfonos, secuencias largas de dígitos (documentos) y lo que
 * parezca un token.
 *
 * Los NOMBRES no se pueden detectar de forma fiable sin un modelo de
 * entidades, así que esto **no** los elimina. Lo que protege ese flanco es que
 * el asistente no consulta tablas operativas y que las preguntas del chat son
 * sobre el reglamento, no sobre personas. Queda dicho aquí y no escondido,
 * porque es una limitación real frente a §11.
 */
@Component
public class Sanitizador {

    private static final Pattern CORREO =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");

    /** Documentos de identidad y similares: cinco dígitos o más seguidos. */
    private static final Pattern DIGITOS_LARGOS =
            Pattern.compile("\\b\\d{5,}\\b");

    private static final Pattern TELEFONO =
            Pattern.compile("\\+?\\d[\\d\\s-]{6,}\\d");

    /** Cadenas largas sin espacios: tokens, claves, identificadores opacos. */
    private static final Pattern TOKEN =
            Pattern.compile("\\b[A-Za-z0-9_-]{24,}\\b");

    private static final int LARGO_MAXIMO = 1000;

    public String limpiar(String pregunta) {
        if (pregunta == null) {
            return "";
        }
        String limpia = pregunta.strip();

        // El orden importa: el correo primero, porque su parte local podría
        // caer en el patrón de token y perderse la arroba.
        limpia = CORREO.matcher(limpia).replaceAll("[correo]");
        limpia = TOKEN.matcher(limpia).replaceAll("[token]");
        limpia = TELEFONO.matcher(limpia).replaceAll("[telefono]");
        limpia = DIGITOS_LARGOS.matcher(limpia).replaceAll("[numero]");
        limpia = limpia.replaceAll("\\s+", " ").strip();

        return limpia.length() > LARGO_MAXIMO ? limpia.substring(0, LARGO_MAXIMO) : limpia;
    }
}
