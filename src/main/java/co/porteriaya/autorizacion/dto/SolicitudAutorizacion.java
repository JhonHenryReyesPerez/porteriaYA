package co.porteriaya.autorizacion.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta de una autorizacion. El apartamento NO viaja en el cuerpo: sale de la
 * cabecera X-Actor, asi un residente no puede crear autorizaciones a nombre de
 * otro apartamento ni por equivocacion.
 *
 * Los dias de la semana van como enteros ISO (lunes = 1) para no depender del
 * idioma de un enum.
 */
public record SolicitudAutorizacion(

        @NotNull(message = "El tipo de autorizacion es obligatorio")
        TipoAutorizacion tipo,

        @NotBlank(message = "El nombre del visitante es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String visitanteNombre,

        @Pattern(regexp = "[0-9]{4}", message = "Se registran solo los ultimos 4 digitos del documento")
        String visitanteDoc4,

        @NotNull(message = "La fecha de inicio es obligatoria")
        Instant validaDesde,

        @NotNull(message = "La fecha de fin es obligatoria")
        Instant validaHasta,

        Set<@Min(value = 1, message = "Dia invalido") @Max(value = 7, message = "Dia invalido") Integer> diasSemana,

        LocalTime horaDesde,

        LocalTime horaHasta,

        @Positive(message = "Los usos deben ser un numero positivo")
        Integer usosMax,

        @Positive(message = "La duracion esperada debe ser un numero positivo")
        Integer duracionEsperadaMin
) {

    public boolean traeRecurrencia() {
        return diasSemana != null && !diasSemana.isEmpty();
    }

    public boolean traeFranja() {
        return horaDesde != null && horaHasta != null;
    }
}
