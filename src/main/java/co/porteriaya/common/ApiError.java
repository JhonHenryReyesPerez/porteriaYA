package co.porteriaya.common;

import java.time.Instant;

/** Cuerpo uniforme de error (§5). No lleva stack trace ni detalles internos (§16). */
public record ApiError(int status, String codigo, String mensaje, Instant timestamp) {
}
