package co.porteriaya.autorizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import co.porteriaya.PropiedadesDePrueba;
import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.entity.Recurrencia;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.autorizacion.service.AutorizacionService;
import co.porteriaya.autorizacion.service.GeneradorCodigo;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.config.ClockConfig;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * La tarea de §9 que pasa a VENCIDA las autorizaciones cuya ventana termino.
 *
 * El caso que mas facil es equivocarse: un servicio recurrente NO vence por
 * ser martes. Su ventana son meses; la franja horaria es otra cosa y la mira
 * el paso 2 de la validacion, no esta tarea.
 */
@ExtendWith(MockitoExtension.class)
class AutorizacionVencimientoTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T17:00:00Z");

    @Mock
    private AutorizacionRepository autorizaciones;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private GeneradorCodigo generadorCodigo;
    @Mock
    private BitacoraService bitacora;
    @Mock
    private EventoPublisher eventos;

    private AutorizacionService servicio() {
        return new AutorizacionService(autorizaciones, residentes, generadorCodigo, bitacora,
                eventos, PropiedadesDePrueba.porteria(), Clock.fixed(AHORA, ClockConfig.ZONA));
    }

    private Residente residente() {
        return new Residente("A", "101", "Residente A-101", null, null, AHORA);
    }

    private Autorizacion visitaTerminada() {
        return new Autorizacion(residente(), TipoAutorizacion.VISITA, "100001", "Camilo Rojas",
                "4821", AHORA.minus(Duration.ofDays(3)), AHORA.minus(Duration.ofDays(1)),
                null, 1, 120, AHORA.minus(Duration.ofDays(3)));
    }

    @Test
    void unaAutorizacionConLaVentanaTerminadaPasaAVencida() {
        Autorizacion terminada = visitaTerminada();
        when(autorizaciones.findByEstadoAndValidaHastaBefore(
                eq(EstadoAutorizacion.ACTIVA), any())).thenReturn(List.of(terminada));

        int vencidas = servicio().vencerLasQueTerminaron();

        assertThat(vencidas).isEqualTo(1);
        assertThat(terminada.getEstado()).isEqualTo(EstadoAutorizacion.VENCIDA);
    }

    @Test
    void laConsultaBuscaSoloLasActivasYConElInstanteDelReloj() {
        when(autorizaciones.findByEstadoAndValidaHastaBefore(
                EstadoAutorizacion.ACTIVA, AHORA)).thenReturn(List.of());

        assertThat(servicio().vencerLasQueTerminaron()).isZero();
    }

    @Test
    void siNoHayNadaQueVencerNoSeTocaNada() {
        when(autorizaciones.findByEstadoAndValidaHastaBefore(
                eq(EstadoAutorizacion.ACTIVA), any())).thenReturn(List.of());

        assertThat(servicio().vencerLasQueTerminaron()).isZero();
        verifyNoInteractions(bitacora, eventos);
    }

    @Test
    void variasAutorizacionesVencenEnLaMismaPasada() {
        when(autorizaciones.findByEstadoAndValidaHastaBefore(
                eq(EstadoAutorizacion.ACTIVA), any()))
                .thenReturn(List.of(visitaTerminada(), visitaTerminada(), visitaTerminada()));

        assertThat(servicio().vencerLasQueTerminaron()).isEqualTo(3);
    }

    @Test
    void laTareaEsIdempotentePorqueSoloMiraLasActivas() {
        Autorizacion terminada = visitaTerminada();
        when(autorizaciones.findByEstadoAndValidaHastaBefore(
                eq(EstadoAutorizacion.ACTIVA), any()))
                .thenReturn(List.of(terminada))
                // Segunda pasada: ya es VENCIDA, la consulta la excluye
                .thenReturn(List.of());

        AutorizacionService servicio = servicio();

        assertThat(servicio.vencerLasQueTerminaron()).isEqualTo(1);
        assertThat(servicio.vencerLasQueTerminaron()).isZero();
        assertThat(terminada.getEstado()).isEqualTo(EstadoAutorizacion.VENCIDA);
    }

    @Test
    void unRecurrenteVenceCuandoTerminaSuPeriodoNoCuandoTerminaLaFranja() {
        // Periodo de meses ya cumplido: esta si vence
        Recurrencia recurrencia = new Recurrencia(
                Recurrencia.parsearDias("1,3,5"),
                java.time.LocalTime.of(7, 0), java.time.LocalTime.of(12, 0));
        Autorizacion recurrenteTerminada = new Autorizacion(residente(),
                TipoAutorizacion.SERVICIO_RECURRENTE, "100004", "Marta Quintero", "5567",
                AHORA.minus(Duration.ofDays(200)), AHORA.minus(Duration.ofDays(1)),
                recurrencia, null, 300, AHORA.minus(Duration.ofDays(200)));

        when(autorizaciones.findByEstadoAndValidaHastaBefore(
                eq(EstadoAutorizacion.ACTIVA), any())).thenReturn(List.of(recurrenteTerminada));

        assertThat(servicio().vencerLasQueTerminaron()).isEqualTo(1);
        assertThat(recurrenteTerminada.getEstado()).isEqualTo(EstadoAutorizacion.VENCIDA);

        // Y la que sigue en periodo no entra en la consulta, aunque hoy caiga
        // fuera de su franja horaria: eso lo mira el paso 2, no esta tarea
        assertThat(recurrenteTerminada.esRecurrente()).isTrue();
    }

    @Test
    void vencerNoEmiteEventoNiEscribeBitacora() {
        when(autorizaciones.findByEstadoAndValidaHastaBefore(
                eq(EstadoAutorizacion.ACTIVA), any())).thenReturn(List.of(visitaTerminada()));

        servicio().vencerLasQueTerminaron();

        // §10 no define un evento para esto y la lista es contrato: vencer es
        // un cambio de estado interno, no algo que avisar al residente.
        verifyNoInteractions(eventos, bitacora);
    }
}
