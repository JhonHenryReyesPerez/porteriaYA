package co.porteriaya.porteria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que el vigilante teclea para validar un codigo.
 *
 * El documento se pide con 4 digitos a proposito: el documento completo no
 * entra al sistema ni siquiera en la peticion (§6, Ley 1581 de 2012).
 */
public record SolicitudValidacion(

        @NotBlank(message = "El codigo es obligatorio")
        @Pattern(regexp = "[0-9]{6}", message = "El codigo debe tener exactamente 6 digitos")
        String codigo,

        @NotBlank(message = "El nombre del visitante es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String visitanteNombre,

        @Pattern(regexp = "[0-9]{4}", message = "Se registran solo los ultimos 4 digitos del documento")
        String visitanteDoc4
) {
}
