package co.porteriaya.porteria.dto;

import java.time.Instant;
import java.util.List;

import co.porteriaya.autorizacion.entity.TipoAutorizacion;

/**
 * Respuesta de POST /api/porteria/validar (§6).
 *
 * Tres cosas distintas y no las confundas:
 * - `permitido` false con `motivo`: el ingreso NO puede registrarse.
 * - `permitido` true con `advertencias`: puede registrarse, pero el vigilante
 *   tiene que confirmarlas al llamar a POST /api/porteria/ingresos.
 * - `permitido` true y sin advertencias: adelante.
 */
public record RespuestaValidacion(
        boolean permitido,
        MotivoRechazo motivo,
        String detalle,
        List<Advertencia> advertencias,
        ResumenAutorizacion autorizacion
) {

    /** Lo minimo que el vigilante necesita ver en pantalla. Sin el codigo completo. */
    public record ResumenAutorizacion(
            Long id,
            TipoAutorizacion tipo,
            String apartamento,
            String visitanteNombre,
            Integer usosRestantes,
            int duracionEsperadaMin,
            Instant validaHasta
    ) {
    }

    public static RespuestaValidacion rechazo(MotivoRechazo motivo) {
        return new RespuestaValidacion(false, motivo, null, List.of(), null);
    }

    public static RespuestaValidacion rechazo(MotivoRechazo motivo, String detalle) {
        return new RespuestaValidacion(false, motivo, detalle, List.of(), null);
    }

    public static RespuestaValidacion permitido(ResumenAutorizacion autorizacion,
                                                List<Advertencia> advertencias,
                                                String detalle) {
        return new RespuestaValidacion(true, null, detalle, List.copyOf(advertencias), autorizacion);
    }
}
