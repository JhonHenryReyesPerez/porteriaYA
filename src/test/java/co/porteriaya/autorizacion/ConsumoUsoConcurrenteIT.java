package co.porteriaya.autorizacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import co.porteriaya.IntegracionBase;
import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.entity.Recurrencia;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.autorizacion.service.ConsumoUsoService;
import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * El test no negociable de §15: varios hilos intentando usar el mismo codigo
 * de un solo uso a la vez. Solo uno puede pasar.
 *
 * Esta clase NO lleva @Transactional, y es deliberado: si la transaccion del
 * test envolviera a los hilos, el UPDATE atomico no se veria entre ellos y el
 * test pasaria en falso, que es la peor clase de test verde.
 */
class ConsumoUsoConcurrenteIT extends IntegracionBase {

    private static final int HILOS = 16;
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private AutorizacionRepository autorizaciones;
    @Autowired
    private ResidenteRepository residentes;
    @Autowired
    private ConsumoUsoService consumo;
    @Autowired
    private Clock clock;

    @Test
    void deVariosHilosSobreUnCodigoDeUnSoloUsoSoloPasaUno() throws Exception {
        Long id = nuevaVisita(1);

        Resultado resultado = consumirEnParalelo(id);

        assertThat(resultado.exitos()).isEqualTo(1);
        assertThat(resultado.agotados()).isEqualTo(HILOS - 1);

        Autorizacion despues = autorizaciones.findById(id).orElseThrow();
        assertThat(despues.getUsosActuales()).isEqualTo(1);
        // Al gastarse el ultimo uso la autorizacion pasa a AGOTADA (§6)
        assertThat(despues.getEstado()).isEqualTo(EstadoAutorizacion.AGOTADA);
    }

    @Test
    void conTresUsosPasanExactamenteTresHilos() throws Exception {
        Long id = nuevaVisita(3);

        Resultado resultado = consumirEnParalelo(id);

        assertThat(resultado.exitos()).isEqualTo(3);
        assertThat(resultado.agotados()).isEqualTo(HILOS - 3);

        Autorizacion despues = autorizaciones.findById(id).orElseThrow();
        assertThat(despues.getUsosActuales()).isEqualTo(3);
        assertThat(despues.getEstado()).isEqualTo(EstadoAutorizacion.AGOTADA);
    }

    @Test
    void conUsosIlimitadosPasanTodosLosHilos() throws Exception {
        Long id = nuevaRecurrenteIlimitada();

        Resultado resultado = consumirEnParalelo(id);

        assertThat(resultado.exitos()).isEqualTo(HILOS);
        assertThat(resultado.agotados()).isZero();

        Autorizacion despues = autorizaciones.findById(id).orElseThrow();
        assertThat(despues.getUsosActuales()).isEqualTo(HILOS);
        // Ilimitada: nunca se agota
        assertThat(despues.getEstado()).isEqualTo(EstadoAutorizacion.ACTIVA);
    }

    // ------------------------------------------------------------ utilidades

    private record Resultado(int exitos, int agotados) {
    }

    /** Lanza todos los hilos a la vez con un latch, para maximizar la contienda. */
    private Resultado consumirEnParalelo(Long autorizacionId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch pistoletazo = new CountDownLatch(1);
        List<Callable<Boolean>> tareas = new ArrayList<>();

        for (int i = 0; i < HILOS; i++) {
            tareas.add(() -> {
                pistoletazo.await(10, TimeUnit.SECONDS);
                try {
                    consumo.consumirUso(autorizacionId);
                    return true;
                } catch (TransicionInvalidaException e) {
                    return false;
                }
            });
        }

        List<Future<Boolean>> futuros = new ArrayList<>();
        for (Callable<Boolean> tarea : tareas) {
            futuros.add(pool.submit(tarea));
        }
        pistoletazo.countDown();

        int exitos = 0;
        int agotados = 0;
        for (Future<Boolean> futuro : futuros) {
            if (Boolean.TRUE.equals(futuro.get(30, TimeUnit.SECONDS))) {
                exitos++;
            } else {
                agotados++;
            }
        }

        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        return new Resultado(exitos, agotados);
    }

    private Residente nuevoResidente() {
        int n = SECUENCIA.incrementAndGet();
        return residentes.save(new Residente(
                "CC", "C" + n, "Residente de prueba " + n, null, null, clock.instant()));
    }

    private Long nuevaVisita(int usosMax) {
        Instant ahora = clock.instant();
        Autorizacion autorizacion = new Autorizacion(
                nuevoResidente(), TipoAutorizacion.VISITA, codigoLibre(), "Visitante de prueba",
                "1234", ahora.minus(Duration.ofHours(1)), ahora.plus(Duration.ofHours(8)),
                null, usosMax, 120, ahora);
        return autorizaciones.save(autorizacion).getId();
    }

    private Long nuevaRecurrenteIlimitada() {
        Instant ahora = clock.instant();
        Recurrencia recurrencia = new Recurrencia(
                EnumSet.allOf(DayOfWeek.class), LocalTime.MIDNIGHT, LocalTime.of(23, 59));
        Autorizacion autorizacion = new Autorizacion(
                nuevoResidente(), TipoAutorizacion.SERVICIO_RECURRENTE, codigoLibre(),
                "Servicio de prueba", "5567", ahora.minus(Duration.ofDays(1)),
                ahora.plus(Duration.ofDays(30)), recurrencia, null, 300, ahora);
        return autorizaciones.save(autorizacion).getId();
    }

    /** Codigos de la franja 9xxxxx para no chocar con los de otras pruebas. */
    private String codigoLibre() {
        return "9%05d".formatted(SECUENCIA.incrementAndGet());
    }
}
