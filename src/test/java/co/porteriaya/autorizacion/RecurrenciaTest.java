package co.porteriaya.autorizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import co.porteriaya.autorizacion.entity.Recurrencia;
import co.porteriaya.config.ClockConfig;
import org.junit.jupiter.api.Test;

/**
 * §15 pide probar la recurrencia por dia de la semana y los bordes de la
 * franja: justo en horaDesde, justo en horaHasta y el cambio de dia.
 */
class RecurrenciaTest {

    private static final Set<DayOfWeek> LUNES_MIERCOLES_VIERNES =
            EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

    private final Recurrencia recurrencia =
            new Recurrencia(LUNES_MIERCOLES_VIERNES, LocalTime.of(7, 0), LocalTime.of(12, 0));

    /** Construye un momento en el proximo dia de la semana pedido, sin asumir el calendario. */
    private ZonedDateTime momento(DayOfWeek dia, LocalTime hora) {
        LocalDate fecha = LocalDate.of(2026, 11, 9).with(TemporalAdjusters.nextOrSame(dia));
        return ZonedDateTime.of(fecha, hora, ClockConfig.ZONA);
    }

    @Test
    void cubreUnDiaYUnaHoraDeLaFranja() {
        assertThat(recurrencia.cubre(momento(DayOfWeek.WEDNESDAY, LocalTime.of(9, 30)))).isTrue();
    }

    @Test
    void noCubreUnDiaQueNoEstaEnLaLista() {
        assertThat(recurrencia.cubre(momento(DayOfWeek.TUESDAY, LocalTime.of(9, 30)))).isFalse();
        assertThat(recurrencia.cubre(momento(DayOfWeek.SUNDAY, LocalTime.of(9, 30)))).isFalse();
    }

    @Test
    void elBordeDeHoraDesdeEstaIncluido() {
        assertThat(recurrencia.cubre(momento(DayOfWeek.MONDAY, LocalTime.of(7, 0)))).isTrue();
    }

    @Test
    void elBordeDeHoraHastaEstaIncluido() {
        assertThat(recurrencia.cubre(momento(DayOfWeek.MONDAY, LocalTime.of(12, 0)))).isTrue();
    }

    @Test
    void unSegundoAntesDeHoraDesdeQuedaFuera() {
        assertThat(recurrencia.cubre(momento(DayOfWeek.MONDAY, LocalTime.of(6, 59, 59)))).isFalse();
    }

    @Test
    void unSegundoDespuesDeHoraHastaQuedaFuera() {
        assertThat(recurrencia.cubre(momento(DayOfWeek.MONDAY, LocalTime.of(12, 0, 1)))).isFalse();
    }

    @Test
    void elCambioDeDiaNoArrastraLaFranja() {
        // Viernes a las 23:59 esta fuera, y el sabado no es dia valido aunque
        // sean dos instantes consecutivos.
        assertThat(recurrencia.cubre(momento(DayOfWeek.FRIDAY, LocalTime.of(23, 59)))).isFalse();
        assertThat(recurrencia.cubre(momento(DayOfWeek.SATURDAY, LocalTime.of(0, 1)))).isFalse();
    }

    @Test
    void serializaLosDiasOrdenadosComoEnterosIso() {
        assertThat(recurrencia.serializarDias()).isEqualTo("1,3,5");
    }

    @Test
    void reconstruyeDesdeLasColumnas() {
        Optional<Recurrencia> leida =
                Recurrencia.de("1,3,5", LocalTime.of(7, 0), LocalTime.of(12, 0));

        assertThat(leida).isPresent();
        assertThat(leida.get().dias()).containsExactlyInAnyOrderElementsOf(LUNES_MIERCOLES_VIERNES);
    }

    @Test
    void sinDiasNiFranjaNoHayRecurrencia() {
        assertThat(Recurrencia.de(null, null, null)).isEmpty();
        assertThat(Recurrencia.de("", LocalTime.of(7, 0), LocalTime.of(12, 0))).isEmpty();
        assertThat(Recurrencia.de("1,3", null, LocalTime.of(12, 0))).isEmpty();
    }

    @Test
    void rechazaUnaFranjaInvertida() {
        assertThatThrownBy(() -> new Recurrencia(
                LUNES_MIERCOLES_VIERNES, LocalTime.of(12, 0), LocalTime.of(7, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior a horaDesde");
    }

    @Test
    void rechazaUnaRecurrenciaSinDias() {
        assertThatThrownBy(() -> new Recurrencia(
                EnumSet.noneOf(DayOfWeek.class), LocalTime.of(7, 0), LocalTime.of(12, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos un dia");
    }
}
