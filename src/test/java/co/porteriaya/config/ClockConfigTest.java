package co.porteriaya.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** El reloj es el unico dueno del tiempo (§9), asi que se prueba aparte. */
class ClockConfigTest {

    private static final Instant ARRANQUE = Instant.parse("2026-11-08T17:00:00Z");

    private RelojDemo relojFijado() {
        return new RelojDemo(Clock.fixed(ARRANQUE, ClockConfig.ZONA));
    }

    @Test
    void laZonaDelSistemaEsBogota() {
        assertThat(ClockConfig.ZONA.getId()).isEqualTo("America/Bogota");
    }

    @Test
    void elRelojDeSistemaUsaLaZonaDelSistema() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ClockConfig.ZONA);
    }

    @Test
    void elRelojDeDemoArrancaSinDesfase() {
        RelojDemo reloj = relojFijado();

        assertThat(reloj.desfase()).isZero();
        assertThat(reloj.instant()).isEqualTo(ARRANQUE);
    }

    @Test
    void elRelojDeDemoAcumulaCadaAvance() {
        RelojDemo reloj = relojFijado();

        reloj.avanzar(Duration.ofHours(25));
        reloj.avanzar(Duration.ofHours(50));

        assertThat(reloj.desfase()).isEqualTo(Duration.ofHours(75));
        assertThat(reloj.instant()).isEqualTo(ARRANQUE.plus(Duration.ofHours(75)));
    }

    @Test
    void elRelojDeDemoNoRetrocede() {
        RelojDemo reloj = relojFijado();

        assertThatThrownBy(() -> reloj.avanzar(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no retrocede");

        assertThat(reloj.desfase()).isZero();
    }

    @Test
    void cambiarDeZonaNoReiniciaLaDemo() {
        RelojDemo reloj = relojFijado();
        reloj.avanzar(Duration.ofDays(8));

        Clock enUtc = reloj.withZone(ZoneOffset.UTC);

        assertThat(enUtc.getZone()).isEqualTo(ZoneId.of("Z"));
        assertThat(enUtc.instant()).isEqualTo(ARRANQUE.plus(Duration.ofDays(8)));
    }
}
