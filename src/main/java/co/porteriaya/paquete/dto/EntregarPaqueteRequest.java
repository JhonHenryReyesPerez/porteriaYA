package co.porteriaya.paquete.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Las dos vias de retiro de §6: el codigo que le llego al residente por correo,
 * o una persona que el residente registro como autorizada a retirar.
 *
 * Basta con una de las dos, pero no con ninguna: el service lo comprueba.
 */
public record EntregarPaqueteRequest(

        @Pattern(regexp = "[0-9]{6}", message = "El codigo de retiro son 6 digitos")
        String codigoRetiro,

        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String retiradoPor,

        @Pattern(regexp = "[0-9]{4}", message = "Se registran solo los ultimos 4 digitos del documento")
        String doc4
) {

    public boolean traeCodigo() {
        return codigoRetiro != null && !codigoRetiro.isBlank();
    }

    public boolean traePersona() {
        return retiradoPor != null && !retiradoPor.isBlank();
    }
}
