package co.porteriaya.nopermitido.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta en la lista de no permitidos. Solo administracion (§7). */
public record NoPermitidoRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String nombre,

        @Pattern(regexp = "[0-9]{4}",
                message = "Se registran solo los ultimos 4 digitos del documento")
        String doc4,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 200, message = "El motivo no puede pasar de 200 caracteres")
        String motivo
) {
}
