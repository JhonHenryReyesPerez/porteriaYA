package co.porteriaya.solicitud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que el vigilante teclea cuando llega alguien sin anunciarse.
 *
 * El apartamento SI va en el cuerpo, al contrario que en las autorizaciones:
 * aqui X-Actor es el vigilante, no el residente, asi que el apartamento al que
 * el visitante dice venir es un dato de la peticion.
 */
public record SolicitudNoAnunciadaRequest(

        @NotBlank(message = "El apartamento es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9]+-[A-Za-z0-9]+",
                message = "El apartamento va como torre-apartamento, por ejemplo A-101")
        String apartamento,

        @NotBlank(message = "El nombre del visitante es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String visitanteNombre,

        @Pattern(regexp = "[0-9]{4}", message = "Se registran solo los ultimos 4 digitos del documento")
        String visitanteDoc4,

        @Size(max = 200, message = "El motivo no puede pasar de 200 caracteres")
        String motivo
) {
}
