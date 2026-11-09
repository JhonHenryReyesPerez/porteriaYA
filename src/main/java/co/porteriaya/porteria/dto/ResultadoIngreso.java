package co.porteriaya.porteria.dto;

import java.util.List;

/**
 * Resultado de intentar registrar un ingreso.
 *
 * Es un resultado y no una excepcion a proposito. Si el rechazo se señalara
 * lanzando, la transaccion haria rollback y se perderian las dos cosas que mas
 * importan de un intento denegado: la entrada en bitacora y la alerta
 * PERSONA_NO_PERMITIDA, que sale en AFTER_COMMIT y por tanto no saldria nunca.
 *
 * Asi el intento se confirma en la base, la alerta se va a administracion, y es
 * el controller quien traduce el rechazo a 409 cuando la transaccion ya cerro.
 */
public sealed interface ResultadoIngreso {

    record Registrado(RespuestaIngreso ingreso) implements ResultadoIngreso {
    }

    /** `motivo` nulo con advertencias presentes = faltaba confirmarlas. */
    record Rechazado(MotivoRechazo motivo, List<Advertencia> advertencias, String detalle)
            implements ResultadoIngreso {

        public Rechazado {
            advertencias = List.copyOf(advertencias);
        }

        /** Codigo estable que la UI interpreta (§13, manejo visible del 409). */
        public String codigo() {
            return motivo != null ? motivo.name() : "ADVERTENCIAS_SIN_CONFIRMAR";
        }

        public String mensaje() {
            if (motivo != null) {
                return detalle != null
                        ? "No se puede registrar el ingreso: %s (%s)".formatted(motivo, detalle)
                        : "No se puede registrar el ingreso: %s".formatted(motivo);
            }
            return "Hay advertencias sin confirmar: %s. Revisalas y vuelve a enviar con confirmaAdvertencias."
                    .formatted(advertencias);
        }
    }
}
