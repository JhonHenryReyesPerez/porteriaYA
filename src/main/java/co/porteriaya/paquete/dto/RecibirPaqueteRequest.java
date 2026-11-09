package co.porteriaya.paquete.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Lo que el vigilante teclea al recibir un paquete. */
public record RecibirPaqueteRequest(

        @NotBlank(message = "El apartamento es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9]+-[A-Za-z0-9]+",
                message = "El apartamento va como torre-apartamento, por ejemplo A-101")
        String apartamento,

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 200, message = "La descripcion no puede pasar de 200 caracteres")
        String descripcion,

        @Size(max = 120, message = "El remitente no puede pasar de 120 caracteres")
        String remitente
) {
}
