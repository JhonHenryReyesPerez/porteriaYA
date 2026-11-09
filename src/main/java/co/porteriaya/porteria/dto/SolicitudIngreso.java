package co.porteriaya.porteria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registro de un ingreso con codigo. Repite los datos del visitante porque la
 * cadena de validacion se evalua otra vez aqui, y esta vez si escribe (§6).
 */
public record SolicitudIngreso(

        @NotBlank(message = "El codigo es obligatorio")
        @Pattern(regexp = "[0-9]{6}", message = "El codigo debe tener exactamente 6 digitos")
        String codigo,

        @NotBlank(message = "El nombre del visitante es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String visitanteNombre,

        @Pattern(regexp = "[0-9]{4}", message = "Se registran solo los ultimos 4 digitos del documento")
        String visitanteDoc4,

        /**
         * El vigilante vio las advertencias y decide seguir. Sin esto, un
         * ingreso con advertencias responde 409: asi nadie se salta el aviso
         * llamando directo a este endpoint (§6).
         */
        boolean confirmaAdvertencias
) {
}
