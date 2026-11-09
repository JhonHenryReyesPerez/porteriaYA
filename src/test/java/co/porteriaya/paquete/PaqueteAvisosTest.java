package co.porteriaya.paquete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.paquete.entity.Paquete;
import co.porteriaya.paquete.repository.AutorizadoRetiroRepository;
import co.porteriaya.paquete.repository.PaqueteRepository;
import co.porteriaya.paquete.service.PaqueteService;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * §15, literal: «Escalamiento de paquetes (24 h, 72 h, 7 dias) con Clock fijo y
 * sin repetir avisos». Nada de Thread.sleep: el reloj se fija y se mueve a mano.
 *
 * Lo que de verdad hay que blindar es el «sin repetir»: el scheduler corre cada
 * minuto, asi que un aviso sin bandera se enviaria 1440 veces al dia.
 */
@ExtendWith(MockitoExtension.class)
class PaqueteAvisosTest {

    private static final Instant RECIBIDO = Instant.parse("2026-11-08T15:00:00Z");

    @Mock
    private PaqueteRepository paquetes;
    @Mock
    private AutorizadoRetiroRepository autorizados;
    @Mock
    private ResidenteRepository residentes;
    @Mock
    private BitacoraService bitacora;
    @Mock
    private EventoPublisher eventos;

    /** Servicio con el reloj clavado en el instante que pida cada prueba. */
    private PaqueteService servicioA(Instant ahora) {
        return new PaqueteService(paquetes, autorizados, residentes, bitacora, eventos,
                PropiedadesDePrueba.porteria(), Clock.fixed(ahora, ClockConfig.ZONA));
    }

    private Paquete paquete() {
        Residente residente = new Residente("A", "101", "Residente A-101",
                "a101@example.test", null, RECIBIDO);
        return new Paquete(residente, "Caja mediana", "Tienda", "200001", "vigilante1", RECIBIDO);
    }

    private Paquete conPaqueteDe(Duration antiguedad) {
        Paquete paquete = paquete();
        when(paquetes.sinRetirar()).thenReturn(List.of(paquete));
        return paquete;
    }

    // ------------------------------------------------------- antes del primer hito

    @Test
    void aLasDosHorasNoSeAvisaDeNada() {
        Paquete paquete = conPaqueteDe(Duration.ofHours(2));

        int avisos = servicioA(RECIBIDO.plus(Duration.ofHours(2))).procesarAvisos();

        assertThat(avisos).isZero();
        assertThat(paquete.isAviso24h()).isFalse();
        verifyNoInteractions(eventos);
    }

    @Test
    void aLasVeintitresHorasTodaviaNo() {
        Paquete paquete = conPaqueteDe(Duration.ofHours(23));

        servicioA(RECIBIDO.plus(Duration.ofHours(23))).procesarAvisos();

        assertThat(paquete.isAviso24h()).isFalse();
    }

    // ------------------------------------------------------------- hito de 24 h

    @Test
    void justoALas24HorasSaleElPrimerRecordatorio() {
        Paquete paquete = conPaqueteDe(Duration.ofHours(24));

        int avisos = servicioA(RECIBIDO.plus(Duration.ofHours(24))).procesarAvisos();

        assertThat(avisos).isEqualTo(1);
        assertThat(paquete.isAviso24h()).isTrue();
        assertThat(paquete.isAviso72h()).isFalse();
        assertThat(paquete.isEscalado()).isFalse();
        verify(eventos).publicar(eq(TipoEvento.PAQUETE_RECORDATORIO), any());
    }

    @Test
    void elRecordatorioDe24NoSeRepiteEnLasPasadasSiguientes() {
        Paquete paquete = conPaqueteDe(Duration.ofHours(30));
        PaqueteService servicio = servicioA(RECIBIDO.plus(Duration.ofHours(30)));

        assertThat(servicio.procesarAvisos()).isEqualTo(1);
        // El scheduler vuelve a pasar un minuto despues, y otra vez, y otra
        assertThat(servicio.procesarAvisos()).isZero();
        assertThat(servicio.procesarAvisos()).isZero();

        assertThat(paquete.isAviso24h()).isTrue();
        verify(eventos, times(1)).publicar(eq(TipoEvento.PAQUETE_RECORDATORIO), any());
    }

    // ------------------------------------------------------------- hito de 72 h

    @Test
    void alPasarDe24A72SaleElSegundoRecordatorioYSoloEse() {
        Paquete paquete = paquete();
        when(paquetes.sinRetirar()).thenReturn(List.of(paquete));

        // Primera pasada a las 25 h: solo el de 24
        servicioA(RECIBIDO.plus(Duration.ofHours(25))).procesarAvisos();
        // Segunda pasada a las 73 h: solo el de 72, porque el otro ya tiene bandera
        int avisos = servicioA(RECIBIDO.plus(Duration.ofHours(73))).procesarAvisos();

        assertThat(avisos).isEqualTo(1);
        assertThat(paquete.isAviso72h()).isTrue();
        assertThat(paquete.isEscalado()).isFalse();
        verify(eventos, times(2)).publicar(eq(TipoEvento.PAQUETE_RECORDATORIO), any());
    }

    // -------------------------------------------------------- escalamiento 7 dias

    @Test
    void aLosSieteDiasSeEscalaAAdministracion() {
        Paquete paquete = paquete();
        when(paquetes.sinRetirar()).thenReturn(List.of(paquete));
        servicioA(RECIBIDO.plus(Duration.ofHours(25))).procesarAvisos();
        servicioA(RECIBIDO.plus(Duration.ofHours(73))).procesarAvisos();

        int avisos = servicioA(RECIBIDO.plus(Duration.ofDays(7))).procesarAvisos();

        assertThat(avisos).isEqualTo(1);
        assertThat(paquete.isEscalado()).isTrue();
        verify(eventos).publicar(eq(TipoEvento.PAQUETE_ESCALADO), any());
        verify(bitacora).registrar(anyString(), any(), anyString(), any(),
                eq("PAQUETE_ESCALADO"), anyString());
    }

    @Test
    void elEscalamientoTampocoSeRepite() {
        Paquete paquete = conPaqueteDe(Duration.ofDays(10));
        PaqueteService servicio = servicioA(RECIBIDO.plus(Duration.ofDays(10)));

        servicio.procesarAvisos();
        int segunda = servicio.procesarAvisos();

        assertThat(segunda).isZero();
        assertThat(paquete.isEscalado()).isTrue();
        verify(eventos, times(1)).publicar(eq(TipoEvento.PAQUETE_ESCALADO), any());
    }

    // ------------------------------------------- el salto de reloj de la demo

    @Test
    void siElRelojSaltaOchoDiasDeGolpeSalenLosTresAvisos() {
        Paquete paquete = conPaqueteDe(Duration.ofDays(8));

        // Es lo que hace POST /api/demo/avanzar: §19 espera ver aparecer los
        // recordatorios Y el escalamiento, no solo el ultimo hito
        int avisos = servicioA(RECIBIDO.plus(Duration.ofDays(8))).procesarAvisos();

        assertThat(avisos).isEqualTo(3);
        assertThat(paquete.isAviso24h()).isTrue();
        assertThat(paquete.isAviso72h()).isTrue();
        assertThat(paquete.isEscalado()).isTrue();

        verify(eventos, times(2)).publicar(eq(TipoEvento.PAQUETE_RECORDATORIO), any());
        verify(eventos, times(1)).publicar(eq(TipoEvento.PAQUETE_ESCALADO), any());
    }

    @Test
    void unPaqueteYaEntregadoNoGeneraAvisos() {
        // La consulta ya filtra por RECIBIDO: un entregado no llega hasta aqui
        when(paquetes.sinRetirar()).thenReturn(List.of());

        assertThat(servicioA(RECIBIDO.plus(Duration.ofDays(30))).procesarAvisos()).isZero();
        verify(eventos, never()).publicar(any(), any());
    }

    @Test
    void variosPaquetesSeProcesanIndependientemente() {
        Residente residente = new Residente("A", "101", "Residente", null, null, RECIBIDO);
        Paquete nuevo = new Paquete(residente, "Nuevo", null, "200001", "vigilante1",
                RECIBIDO.plus(Duration.ofDays(7)));
        Paquete viejo = new Paquete(residente, "Viejo", null, "200002", "vigilante1", RECIBIDO);
        when(paquetes.sinRetirar()).thenReturn(List.of(nuevo, viejo));

        // A los 7 dias y 1 hora: el viejo cruza los tres hitos, el nuevo ninguno
        int avisos = servicioA(RECIBIDO.plus(Duration.ofDays(7)).plus(Duration.ofHours(1)))
                .procesarAvisos();

        assertThat(avisos).isEqualTo(3);
        assertThat(nuevo.isAviso24h()).isFalse();
        assertThat(viejo.isEscalado()).isTrue();
    }
}
