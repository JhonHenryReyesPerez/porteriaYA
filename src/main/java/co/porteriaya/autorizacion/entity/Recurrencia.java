package co.porteriaya.autorizacion.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Franja de un servicio recurrente o una mudanza (§6): dias de la semana fijos
 * y una hora de inicio y fin. Se guarda como "1,3,5" (dias ISO, lunes = 1) mas
 * dos columnas TIME.
 *
 * Ambos extremos de la franja son INCLUSIVOS: llegar justo a horaDesde o justo
 * a horaHasta cuenta como dentro. §15 pide probar esos dos bordes.
 */
public record Recurrencia(Set<DayOfWeek> dias, LocalTime desde, LocalTime hasta) {

    public Recurrencia {
        if (dias == null || dias.isEmpty()) {
            throw new IllegalArgumentException("La recurrencia necesita al menos un dia");
        }
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("La recurrencia necesita franja horaria completa");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("horaHasta no puede ser anterior a horaDesde");
        }
        dias = EnumSet.copyOf(dias);
    }

    /** Reconstruye la recurrencia desde las columnas; vacio si la autorizacion no es recurrente. */
    public static Optional<Recurrencia> de(String diasSemana, LocalTime desde, LocalTime hasta) {
        if (diasSemana == null || diasSemana.isBlank() || desde == null || hasta == null) {
            return Optional.empty();
        }
        return Optional.of(new Recurrencia(parsearDias(diasSemana), desde, hasta));
    }

    public static Set<DayOfWeek> parsearDias(String diasSemana) {
        return Arrays.stream(diasSemana.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .map(DayOfWeek::of)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    /** Serializa a la forma que guarda la columna dias_semana. */
    public String serializarDias() {
        return dias.stream()
                .sorted()
                .map(d -> String.valueOf(d.getValue()))
                .collect(Collectors.joining(","));
    }

    /**
     * El momento dado cae dentro de la franja. Se evalua en la zona del
     * {@link ZonedDateTime} recibido, que siempre es America/Bogota (§2).
     */
    public boolean cubre(ZonedDateTime momento) {
        if (!dias.contains(momento.getDayOfWeek())) {
            return false;
        }
        LocalTime hora = momento.toLocalTime();
        return !hora.isBefore(desde) && !hora.isAfter(hasta);
    }
}
