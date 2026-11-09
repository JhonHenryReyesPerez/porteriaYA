package co.porteriaya.porteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.porteria.dto.ResultadoIngreso;
import co.porteriaya.porteria.dto.SolicitudIngreso;
import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.repository.IngresoRepository;
import co.porteriaya.porteria.service.IngresoService;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * El test no negociable de §15, ahora sobre la cadena COMPLETA y no solo sobre
 * la sentencia atomica: varios vigilantes registrando el mismo codigo de un
 * solo uso a la vez.
 *
 * Esto cubre lo que el test del repositorio no puede: que la doble evaluacion
 * de la cadena, el descuento y la creacion del Ingreso aguanten juntos la
 * contienda. Si uno de los hilos perdiera el uso pero igual creara su ingreso,
 * o si dos crearan ingreso, saldria aqui.
 *
 * Sin @Transactional, por lo mismo de siempre: envolver los hilos en la
 * transaccion del test daria un verde falso.
 */
class IngresoConcurrenteIT extends IntegracionBase {

    private static final int HILOS = 12;
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private IngresoService ingresoService;
    @Autowired
    private AutorizacionRepository autorizaciones;
    @Autowired
    private IngresoRepository ingresos;
    @Autowired
    private ResidenteRepository residentes;
    @Autowired
    private Clock clock;

    @Test
    void variosVigilantesSobreUnCodigoDeUnSoloUsoYSoloEntraUno() throws Exception {
        String codigo = nuevaVisita(1);

        int registrados = registrarEnParalelo(codigo);

        assertThat(registrados).isEqualTo(1);

        Autorizacion despues = autorizaciones.findByCodigo(codigo).orElseThrow();
        assertThat(despues.getUsosActuales()).isEqualTo(1);
        assertThat(despues.getEstado()).isEqualTo(EstadoAutorizacion.AGOTADA);

        // Y hay exactamente un ingreso, no doce
        assertThat(ingresosDe(despues)).isEqualTo(1);
    }

    @Test
    void conDosUsosEntranExactamenteDos() throws Exception {
        String codigo = nuevaVisita(2);

        int registrados = registrarEnParalelo(codigo);

        assertThat(registrados).isEqualTo(2);

        Autorizacion despues = autorizaciones.findByCodigo(codigo).orElseThrow();
        assertThat(despues.getUsosActuales()).isEqualTo(2);
        assertThat(ingresosDe(despues)).isEqualTo(2);
    }

    // ------------------------------------------------------------ utilidades

    /** Cuenta los ingresos creados contra esa autorizacion. */
    private long ingresosDe(Autorizacion autorizacion) {
        return ingresos.findAll().stream()
                .filter(i -> i.getAutorizacion() != null
                        && i.getAutorizacion().getId().equals(autorizacion.getId()))
                .count();
    }

    /**
     * Confirma las advertencias a proposito: asi el unico camino de rechazo
     * posible es USOS_AGOTADOS y la prueba aisla la carrera por el uso de la
     * advertencia YA_DENTRO, que es otra cosa.
     */
    private int registrarEnParalelo(String codigo) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch pistoletazo = new CountDownLatch(1);
        List<Callable<Boolean>> tareas = new ArrayList<>();

        for (int i = 0; i < HILOS; i++) {
            String vigilante = "vigilante" + i;
            tareas.add(() -> {
                pistoletazo.await(10, TimeUnit.SECONDS);
                try {
                    ResultadoIngreso resultado = ingresoService.registrar(
                            new SolicitudIngreso(codigo, "Camilo Rojas", "4821", true), vigilante);
                    return resultado instanceof ResultadoIngreso.Registrado;
                } catch (RuntimeException e) {
                    // 409 USOS_AGOTADOS: perdio la carrera
                    return false;
                }
            });
        }

        List<Future<Boolean>> futuros = new ArrayList<>();
        for (Callable<Boolean> tarea : tareas) {
            futuros.add(pool.submit(tarea));
        }
        pistoletazo.countDown();

        int registrados = 0;
        for (Future<Boolean> futuro : futuros) {
            if (Boolean.TRUE.equals(futuro.get(30, TimeUnit.SECONDS))) {
                registrados++;
            }
        }

        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        return registrados;
    }

    private String nuevaVisita(int usosMax) {
        int n = SECUENCIA.incrementAndGet();
        Residente residente = residentes.save(new Residente(
                "CO", "O" + n, "Residente de prueba " + n, null, null, clock.instant()));
        Instant ahora = clock.instant();
        String codigo = "6%05d".formatted(n);
        autorizaciones.save(new Autorizacion(residente, TipoAutorizacion.VISITA, codigo,
                "Camilo Rojas", "4821", ahora.minus(Duration.ofHours(1)),
                ahora.plus(Duration.ofHours(8)), null, usosMax, 120, ahora));
        return codigo;
    }

    @Test
    void losIngresosCreadosQuedanTodosEnEstadoDentro() throws Exception {
        String codigo = nuevaVisita(3);

        registrarEnParalelo(codigo);

        Autorizacion despues = autorizaciones.findByCodigo(codigo).orElseThrow();
        long dentro = ingresos.findAll().stream()
                .filter(i -> i.getAutorizacion() != null
                        && i.getAutorizacion().getId().equals(despues.getId()))
                .filter(i -> i.getEstado() == EstadoIngreso.DENTRO)
                .count();

        assertThat(dentro).isEqualTo(3);
    }
}
