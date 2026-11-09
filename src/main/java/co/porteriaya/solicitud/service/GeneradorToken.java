package co.porteriaya.solicitud.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Tokens de aprobacion (§6): aleatorios, de un solo uso y guardados hasheados.
 *
 * 32 bytes de SecureRandom son 256 bits de entropia: adivinar uno por fuerza
 * bruta no es una via de ataque. Lo que se guarda es su SHA-256.
 */
@Component
public class GeneradorToken {

    private static final int BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();
    private final Base64.Encoder codificador = Base64.getUrlEncoder().withoutPadding();

    /** Token en claro. Va al enlace del correo y no se guarda ni se registra. */
    public String generar() {
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        return codificador.encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal: 64 caracteres, que es lo que espera la columna. */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 deberia estar siempre disponible", e);
        }
    }
}
