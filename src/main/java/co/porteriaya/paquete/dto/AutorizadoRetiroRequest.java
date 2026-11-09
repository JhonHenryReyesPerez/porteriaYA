package co.porteriaya.paquete.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de una persona autorizada a retirar los paquetes de un apartamento (§6).
 *
 * El doc4 es opcional a proposito, igual que en la lista de no permitidos, pero
 * por el motivo contrario: aqui, sin doc4, el cotejo del retiro se queda solo
 * con el nombre. Dos personas con el mismo nombre acreditarian igual, asi que
 * conviene registrarlo siempre que se conozca.
 */
public record AutorizadoRetiroRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String nombre,

        @Pattern(regexp = "[0-9]{4}",
                message = "Se registran solo los ultimos 4 digitos del documento")
        String doc4
) {
}
