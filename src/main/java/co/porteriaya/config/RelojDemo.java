package co.porteriaya.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloj del perfil demo (§9): acumula un desfase que POST /api/demo/avanzar
 * incrementa, para que la demo pueda saltar horas y disparar recordatorios,
 * escalamientos y cierres automaticos sin esperar. Nunca retrocede.
 */
public class RelojDemo extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> desfase;

    public RelojDemo(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private RelojDemo(Clock base, AtomicReference<Duration> desfase) {
        this.base = base;
        this.desfase = desfase;
    }

    /** Adelanta el reloj y devuelve el desfase acumulado. */
    public Duration avanzar(Duration cantidad) {
        if (cantidad.isNegative()) {
            throw new IllegalArgumentException("El reloj de demo no retrocede");
        }
        return desfase.updateAndGet(actual -> actual.plus(cantidad));
    }

    public Duration desfase() {
        return desfase.get();
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zona) {
        // Comparte el desfase: cambiar de zona no debe reiniciar la demo.
        return new RelojDemo(base.withZone(zona), desfase);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(desfase.get());
    }
}
