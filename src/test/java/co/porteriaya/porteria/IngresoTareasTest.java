package co.porteriaya.porteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import co.porteriaya.PropiedadesDePrueba;
import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.service.ConsumoUsoService;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.config.ClockConfig;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.porteria.repository.IngresoRepository;
import co.porteriaya.porteria.service.IngresoService;
import co.porteriaya.porteria.service.ValidacionService;
import co.porteriaya.residente.entity.Residente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * §15, literal: «Permanencia excedida una sola vez; cierre automatico a
 * medianoche». Reloj fijo, nunca Thread.sleep (§9).
 *
 * La medianoche es el punto delicado: es medianoche en America/Bogota, no en
 * UTC. Un ingreso de las 20:00 hora local se cierra a las 00:00 locales, que
 * son las 05:00 UTC del dia siguiente. Si la cuenta se hiciera en UTC, el
 * cierre caeria cinco horas antes de lo debido, en plena noche de visita.
 */
@ExtendWith(MockitoExtension.class)
class IngresoTareasTest {

    /** Un ingreso a las 20:00 hora de Bogota. */
    private static final ZonedDateTime ENTRADA_8PM = ZonedDateTime.of(
            LocalDate.of(2026, 11, 9), LocalTime.of(20, 0), ClockConfig.ZONA);

    private static final Instant ENTRADA = ENTRADA_8PM.toInstant();

    /** Medianoche local siguiente: 00:00 del dia 10 en Bogota. */
    private static final Instant MEDIANOCHE_LOCAL = ENTRADA_8PM.toLocalDate().plusDays(1)
            .atStartOfDay(ClockConfig.ZONA).toInstant();

    @Mock
    private IngresoRepository ingresos;
    @Mock
    private ValidacionService validacion;
    @Mock
    private ConsumoUsoService consumoUso;
    @Mock
    private BitacoraService bitacora;
    @Mock
    private EventoPublisher eventos;

    private IngresoService servicioA(Instant ahora) {
        return new IngresoService(ingresos, validacion, consumoUso, bitacora, eventos,
                PropiedadesDePrueba.porteria(), Clock.fixed(ahora, ClockConfig.ZONA));
    }

    /** Ingreso con autorizacion cuya ventana termina cuando se le diga. */
    private Ingreso ingresoCon(Duration duracionVentana, int duracionEsperadaMin) {
        Residente residente = new Residente("A", "101", "Residente A-101", null, null, ENTRADA);
        Autorizacion autorizacion = new Autorizacion(residente, TipoAutorizacion.VISITA,
                "123456", "Camilo Rojas", "4821", ENTRADA.minus(Duration.ofHours(1)),
                ENTRADA.plus(duracionVentana), null, 1, duracionEsperadaMin, ENTRADA);
        return Ingreso.conAutorizacion(autorizacion, "Camilo Rojas", "4821", "vigilante1", ENTRADA);
    }

    // ------------------------------------------------- permanencia excedida

    @Test
    void laPermanenciaNoSeAvisaAntesDeLaGracia() {
        // 120 min de duracion + 30 de gracia = 150 min. A los 149 todavia no.
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        when(ingresos.dentroSinAvisarDePermanencia()).thenReturn(List.of(ingreso));

        int avisados = servicioA(ENTRADA.plus(Duration.ofMinutes(149)))
                .avisarPermanenciasExcedidas();

        assertThat(avisados).isZero();
        assertThat(ingreso.isPermanenciaAvisada()).isFalse();
        verifyNoInteractions(eventos);
    }

    @Test
    void laPermanenciaSeAvisaAlPasarDuracionMasGracia() {
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        when(ingresos.dentroSinAvisarDePermanencia()).thenReturn(List.of(ingreso));

        int avisados = servicioA(ENTRADA.plus(Duration.ofMinutes(151)))
                .avisarPermanenciasExcedidas();

        assertThat(avisados).isEqualTo(1);
        assertThat(ingreso.isPermanenciaAvisada()).isTrue();
        verify(eventos).publicar(eq(TipoEvento.PERMANENCIA_EXCEDIDA), any());
    }

    @Test
    void laPermanenciaSeAvisaUnaSolaVezAunqueElSchedulerInsista() {
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        // Primera pasada: la consulta lo devuelve
        when(ingresos.dentroSinAvisarDePermanencia())
                .thenReturn(List.of(ingreso))
                // Siguientes pasadas: ya tiene bandera, la consulta lo excluye
                .thenReturn(List.of());

        IngresoService servicio = servicioA(ENTRADA.plus(Duration.ofHours(4)));

        assertThat(servicio.avisarPermanenciasExcedidas()).isEqualTo(1);
        assertThat(servicio.avisarPermanenciasExcedidas()).isZero();
        assertThat(servicio.avisarPermanenciasExcedidas()).isZero();

        verify(eventos, times(1)).publicar(eq(TipoEvento.PERMANENCIA_EXCEDIDA), any());
    }

    @Test
    void cadaIngresoUsaSuPropiaDuracionEsperada() {
        // Un domiciliario de 30 min y una visita de 180, a la misma hora
        Ingreso domiciliario = ingresoCon(Duration.ofDays(3), 30);
        Ingreso visita = ingresoCon(Duration.ofDays(3), 180);
        when(ingresos.dentroSinAvisarDePermanencia())
                .thenReturn(List.of(domiciliario, visita));

        // A los 90 min: el domiciliario paso 30+30, la visita no paso 180+30
        int avisados = servicioA(ENTRADA.plus(Duration.ofMinutes(90)))
                .avisarPermanenciasExcedidas();

        assertThat(avisados).isEqualTo(1);
        assertThat(domiciliario.isPermanenciaAvisada()).isTrue();
        assertThat(visita.isPermanenciaAvisada()).isFalse();
    }

    // --------------------------------------------- cierre automatico

    @Test
    void aLasOnceDeLaNocheTodaviaNoSeCierra() {
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        when(ingresos.dentro()).thenReturn(List.of(ingreso));

        int cerrados = servicioA(ENTRADA.plus(Duration.ofHours(3)))
                .cerrarLosQueNoRegistraronSalida();

        assertThat(cerrados).isZero();
        assertThat(ingreso.getEstado()).isEqualTo(EstadoIngreso.DENTRO);
    }

    @Test
    void pasadaLaMedianocheLocalSeCierraSolo() {
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        when(ingresos.dentro()).thenReturn(List.of(ingreso));

        int cerrados = servicioA(MEDIANOCHE_LOCAL.plusSeconds(60))
                .cerrarLosQueNoRegistraronSalida();

        assertThat(cerrados).isEqualTo(1);
        assertThat(ingreso.getEstado()).isEqualTo(EstadoIngreso.SALIDA_NO_REGISTRADA);
        verify(eventos).publicar(eq(TipoEvento.SALIDA_NO_REGISTRADA), any());
    }

    @Test
    void justoEnLaMedianocheTodaviaNo() {
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        when(ingresos.dentro()).thenReturn(List.of(ingreso));

        int cerrados = servicioA(MEDIANOCHE_LOCAL).cerrarLosQueNoRegistraronSalida();

        assertThat(cerrados).isZero();
    }

    @Test
    void siLaVentanaTerminaAntesDeMedianocheMandaLaVentana() {
        // Ventana de 2 horas: termina a las 22:00, mucho antes de medianoche
        Ingreso ingreso = ingresoCon(Duration.ofHours(2), 120);
        when(ingresos.dentro()).thenReturn(List.of(ingreso));

        int cerrados = servicioA(ENTRADA.plus(Duration.ofHours(2)).plusSeconds(60))
                .cerrarLosQueNoRegistraronSalida();

        assertThat(cerrados).isEqualTo(1);
        assertThat(ingreso.getEstado()).isEqualTo(EstadoIngreso.SALIDA_NO_REGISTRADA);
    }

    @Test
    void unaVisitaNoAnunciadaSoloSeCierraAMedianoche() {
        Residente residente = new Residente("A", "101", "Residente A-101", null, null, ENTRADA);
        // Sin autorizacion detras: no hay ventana que mirar
        Ingreso ingreso = Ingreso.sinAnunciar(residente, "Pedro Nadie", "0001",
                "vigilante1", 180, ENTRADA);
        when(ingresos.dentro()).thenReturn(List.of(ingreso));

        assertThat(servicioA(ENTRADA.plus(Duration.ofHours(3)))
                .cerrarLosQueNoRegistraronSalida()).isZero();
        assertThat(servicioA(MEDIANOCHE_LOCAL.plusSeconds(60))
                .cerrarLosQueNoRegistraronSalida()).isEqualTo(1);
    }

    @Test
    void elCierreNoSeRepitePorqueLaConsultaSoloTraeLosQueSiguenDentro() {
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        when(ingresos.dentro())
                .thenReturn(List.of(ingreso))
                .thenReturn(List.of());

        IngresoService servicio = servicioA(MEDIANOCHE_LOCAL.plusSeconds(60));

        assertThat(servicio.cerrarLosQueNoRegistraronSalida()).isEqualTo(1);
        assertThat(servicio.cerrarLosQueNoRegistraronSalida()).isZero();

        verify(eventos, times(1)).publicar(eq(TipoEvento.SALIDA_NO_REGISTRADA), any());
    }

    @Test
    void laMedianocheSeCalculaEnBogotaYNoEnUtc() {
        // 20:00 en Bogota son 01:00 UTC del dia siguiente. Si la cuenta se
        // hiciera en UTC, la "medianoche siguiente" seria 00:00 UTC, que ya
        // paso, y el ingreso se cerraria al instante.
        Ingreso ingreso = ingresoCon(Duration.ofDays(3), 120);
        when(ingresos.dentro()).thenReturn(List.of(ingreso));

        int cerrados = servicioA(ENTRADA.plus(Duration.ofMinutes(10)))
                .cerrarLosQueNoRegistraronSalida();

        assertThat(cerrados).isZero();
        assertThat(ingreso.getEstado()).isEqualTo(EstadoIngreso.DENTRO);
    }
}
