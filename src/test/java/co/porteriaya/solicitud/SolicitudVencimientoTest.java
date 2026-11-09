package co.porteriaya.solicitud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import co.porteriaya.PropiedadesDePrueba;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.config.ClockConfig;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.porteria.repository.IngresoRepository;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import co.porteriaya.solicitud.entity.EstadoSolicitud;
import co.porteriaya.solicitud.entity.SolicitudNoAnunciada;
import co.porteriaya.solicitud.repository.SolicitudRepository;
import co.porteriaya.solicitud.repository.TokenAprobacionRepository;
import co.porteriaya.solicitud.service.GeneradorToken;
import co.porteriaya.solicitud.service.SolicitudService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * La tarea de §9 que pasa a SIN_RESPUESTA las solicitudes que el residente
 * dejo sin contestar, para que la decision llegue al vigilante.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudVencimientoTest {

    private static final Instant CREADA = Instant.parse("2026-11-08T17:00:00Z");
    private static final Instant VENCE = CREADA.plus(Duration.ofMinutes(3));

    @Mock
    private SolicitudRepository solicitudes;
    @Mock
    private TokenAprobacionRepository tokens;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private IngresoRepository ingresos;
    @Mock
    private GeneradorToken generadorToken;
    @Mock
    private BitacoraService bitacora;
    @Mock
    private EventoPublisher eventos;

    private SolicitudService servicioA(Instant ahora) {
        return new SolicitudService(solicitudes, tokens, residentes, ingresos, generadorToken,
                bitacora, eventos, PropiedadesDePrueba.porteria(),
                Clock.fixed(ahora, ClockConfig.ZONA));
    }

    private SolicitudNoAnunciada pendiente() {
        Residente residente = new Residente("A", "101", "Residente A-101", null, null, CREADA);
        return new SolicitudNoAnunciada(residente, "Pedro Nadie", "0001", "Entrega",
                "vigilante1", CREADA, VENCE);
    }

    @Test
    void unaPendienteVencidaPasaASinRespuesta() {
        SolicitudNoAnunciada solicitud = pendiente();
        when(solicitudes.pendientesVencidas(any())).thenReturn(List.of(solicitud));

        int vencidas = servicioA(VENCE.plusSeconds(1)).vencerPendientes();

        assertThat(vencidas).isEqualTo(1);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.SIN_RESPUESTA);
        assertThat(solicitud.getResueltoEn()).isEqualTo(VENCE.plusSeconds(1));
    }

    @Test
    void laConsultaRecibeElInstanteDelReloj() {
        Instant ahora = VENCE.plusSeconds(30);
        when(solicitudes.pendientesVencidas(ahora)).thenReturn(List.of());

        assertThat(servicioA(ahora).vencerPendientes()).isZero();
    }

    @Test
    void elVencimientoQuedaEnBitacoraParaQueElVigilanteLoVea() {
        when(solicitudes.pendientesVencidas(any())).thenReturn(List.of(pendiente()));

        servicioA(VENCE.plusSeconds(1)).vencerPendientes();

        verify(bitacora).registrar(eq("sistema"), any(), anyString(), any(),
                eq("SOLICITUD_SIN_RESPUESTA"), anyString());
    }

    @Test
    void noSeEmiteNingunEventoPorqueElContratoNoDefineUnoParaEsto() {
        when(solicitudes.pendientesVencidas(any())).thenReturn(List.of(pendiente()));

        servicioA(VENCE.plusSeconds(1)).vencerPendientes();

        // La lista de §10 es contrato: antes que inventar un tipo nuevo, el
        // aviso al vigilante es que le aparece en el listado de SIN_RESPUESTA.
        verifyNoInteractions(eventos);
    }

    @Test
    void siNoHayNingunaVencidaNoSeTocaNada() {
        when(solicitudes.pendientesVencidas(any())).thenReturn(List.of());

        assertThat(servicioA(VENCE.plusSeconds(1)).vencerPendientes()).isZero();
        verifyNoInteractions(bitacora, eventos, ingresos);
    }

    @Test
    void variasSolicitudesVencenEnLaMismaPasada() {
        when(solicitudes.pendientesVencidas(any()))
                .thenReturn(List.of(pendiente(), pendiente()));

        assertThat(servicioA(VENCE.plusSeconds(1)).vencerPendientes()).isEqualTo(2);
        verify(bitacora, times(2)).registrar(anyString(), any(), anyString(), any(),
                anyString(), anyString());
    }

    @Test
    void vencerNoCreaNingunIngreso() {
        when(solicitudes.pendientesVencidas(any())).thenReturn(List.of(pendiente()));

        servicioA(VENCE.plusSeconds(1)).vencerPendientes();

        // Sin respuesta no es una aprobacion: nadie entra por el silencio
        verifyNoInteractions(ingresos);
    }

    @Test
    void laTareaEsIdempotentePorqueLaConsultaFiltraPorEstado() {
        SolicitudNoAnunciada solicitud = pendiente();
        when(solicitudes.pendientesVencidas(any()))
                .thenReturn(List.of(solicitud))
                // Segunda pasada: ya es SIN_RESPUESTA, la consulta la excluye
                .thenReturn(List.of());

        SolicitudService servicio = servicioA(VENCE.plusSeconds(1));

        assertThat(servicio.vencerPendientes()).isEqualTo(1);
        assertThat(servicio.vencerPendientes()).isZero();
    }
}
