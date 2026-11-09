package co.porteriaya.common;

import java.time.Clock;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Traduce las excepciones a el cuerpo uniforme de §5. Nunca expone stack
 * traces ni mensajes internos (§16), y fecha con el Clock inyectado (§9).
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    private final Clock clock;

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(TransicionInvalidaException.class)
    public ResponseEntity<ApiError> transicionInvalida(TransicionInvalidaException e) {
        return respuesta(HttpStatus.CONFLICT, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ApiError> accesoDenegado(AccesoDenegadoException e) {
        return respuesta(HttpStatus.FORBIDDEN, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(LimiteFrecuenciaException.class)
    public ResponseEntity<ApiError> limiteFrecuencia(LimiteFrecuenciaException e) {
        return respuesta(HttpStatus.TOO_MANY_REQUESTS, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ApiError> solicitudInvalida(SolicitudInvalidaException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", detalle);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> subidaDemasiadoGrande(MaxUploadSizeExceededException e) {
        return respuesta(HttpStatus.PAYLOAD_TOO_LARGE, "SUBIDA_DEMASIADO_GRANDE",
                "El archivo supera el tamano maximo permitido");
    }

    /** Ultimo recurso: se registra con detalle y se responde sin filtrar nada. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(Exception e) {
        log.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrio un error inesperado");
    }

    private ResponseEntity<ApiError> respuesta(HttpStatus status, String codigo, String mensaje) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), codigo, mensaje, clock.instant()));
    }
}
