package co.porteriaya.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.config.ClockConfig;
import co.porteriaya.nopermitido.repository.NoPermitidoRepository;
import co.porteriaya.paquete.entity.Paquete;
import co.porteriaya.paquete.repository.PaqueteRepository;
import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.porteria.repository.IngresoRepository;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.solicitud.repository.SolicitudRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Metricas del panel (§13).
 *
 * Los dos sitios donde esto se rompe facil: la zona horaria del «hoy» y los
 * bordes de los tramos de antiguedad. Ambos se prueban con reloj fijo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    /** Mediodia del 9 de noviembre de 2026 en Bogota. */
    private static final ZonedDateTime AHORA_LOCAL = ZonedDateTime.of(
            LocalDate.of(2026, 11, 9), LocalTime.of(12, 0), ClockConfig.ZONA);

    private static final Instant AHORA = AHORA_LOCAL.toInstant();

    @Mock
    private IngresoRepository ingresos;
    @Mock
    private AutorizacionRepository autorizaciones;
    @Mock
    private SolicitudRepository solicitudes;
    @Mock
    private PaqueteRepository paquetes;
    @Mock
    private NoPermitidoRepository noPermitidos;

    private DashboardService servicio() {
        return new DashboardService(ingresos, autorizaciones, solicitudes, paquetes,
                noPermitidos, Clock.fixed(AHORA, ClockConfig.ZONA));
    }

    private Residente residente() {
        return new Residente("A", "101", "Residente A-101", null, null, AHORA);
    }

    /** Un ingreso a la hora local que se pida. */
    private Ingreso ingresoALas(int horaLocal) {
        Instant momento = AHORA_LOCAL.withHour(horaLocal).withMinute(30).toInstant();
        return Ingreso.sinAnunciar(residente(), "Visitante", "0001", "vigilante1", 120, momento);
    }

    private Paquete paqueteDeHace(Duration antiguedad) {
        return new Paquete(residente(), "Caja", null, "200001", "vigilante1",
                AHORA.minus(antiguedad));
    }

    // -------------------------------------------------------------- tarjetas

    @Test
    void lasTarjetasPasanLasCuentasDeCadaRepositorio() {
        when(ingresos.countByEstado(EstadoIngreso.DENTRO)).thenReturn(7L);
        when(ingresos.countByEstado(EstadoIngreso.SALIDA_NO_REGISTRADA)).thenReturn(2L);
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of(
                ingresoALas(8), ingresoALas(9), ingresoALas(10)));
        when(autorizaciones.countByEstado(EstadoAutorizacion.ACTIVA)).thenReturn(12L);
        when(solicitudes.countByEstado(any())).thenReturn(1L);
        when(paquetes.countByEstado(any())).thenReturn(5L);
        when(noPermitidos.countByActivoTrue()).thenReturn(3L);
        when(paquetes.sinRetirar()).thenReturn(List.of());

        EstadisticasDashboard.Tarjetas tarjetas = servicio().calcular().tarjetas();

        assertThat(tarjetas.personasDentro()).isEqualTo(7);
        assertThat(tarjetas.ingresosHoy()).isEqualTo(3);
        assertThat(tarjetas.autorizacionesActivas()).isEqualTo(12);
        assertThat(tarjetas.solicitudesPendientes()).isEqualTo(1);
        assertThat(tarjetas.paquetesPendientes()).isEqualTo(5);
        assertThat(tarjetas.noPermitidosActivos()).isEqualTo(3);
        assertThat(tarjetas.salidasNoRegistradas()).isEqualTo(2);
    }

    // ------------------------------------------------------- visitas por hora

    @Test
    void elGraficoTrae24PosicionesAunqueNoHayaVisitas() {
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of());
        when(paquetes.sinRetirar()).thenReturn(List.of());

        List<Integer> porHora = servicio().calcular().visitasPorHoraHoy();

        assertThat(porHora).hasSize(24).containsOnly(0);
    }

    @Test
    void cadaVisitaCaeEnSuHoraLocal() {
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of(
                ingresoALas(8), ingresoALas(8), ingresoALas(8),
                ingresoALas(14),
                ingresoALas(23)));
        when(paquetes.sinRetirar()).thenReturn(List.of());

        List<Integer> porHora = servicio().calcular().visitasPorHoraHoy();

        assertThat(porHora.get(8)).isEqualTo(3);
        assertThat(porHora.get(14)).isEqualTo(1);
        assertThat(porHora.get(23)).isEqualTo(1);
        assertThat(porHora.get(0)).isZero();
    }

    @Test
    void laHoraSeCuentaEnBogotaYNoEnUtc() {
        // Un ingreso a las 20:00 de Bogota es 01:00 UTC del dia siguiente. Si
        // se agrupara por hora UTC, apareceria en la posicion 1 en vez de la 20.
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of(ingresoALas(20)));
        when(paquetes.sinRetirar()).thenReturn(List.of());

        List<Integer> porHora = servicio().calcular().visitasPorHoraHoy();

        assertThat(porHora.get(20)).isEqualTo(1);
        assertThat(porHora.get(1)).isZero();
    }

    @Test
    void elGraficoEsInmutable() {
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of());
        when(paquetes.sinRetirar()).thenReturn(List.of());

        List<Integer> porHora = servicio().calcular().visitasPorHoraHoy();

        assertThat(porHora).isUnmodifiable();
    }

    // ------------------------------------------------- tramos de antiguedad

    @Test
    void losTramosSeparanLosPaquetesPorHoras() {
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of());
        when(paquetes.sinRetirar()).thenReturn(List.of(
                paqueteDeHace(Duration.ofHours(2)),
                paqueteDeHace(Duration.ofHours(30)),
                paqueteDeHace(Duration.ofDays(4)),
                paqueteDeHace(Duration.ofDays(10)),
                paqueteDeHace(Duration.ofDays(12))));

        EstadisticasDashboard.PaquetesPorAntiguedad tramos =
                servicio().calcular().paquetesPorAntiguedad();

        assertThat(tramos.menosDe24Horas()).isEqualTo(1);
        assertThat(tramos.entre24Y72Horas()).isEqualTo(1);
        assertThat(tramos.entre72HorasY7Dias()).isEqualTo(1);
        assertThat(tramos.masDe7Dias()).isEqualTo(2);
    }

    @Test
    void losBordesDeLosTramosCaenDelLadoQueTocaria() {
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of());
        when(paquetes.sinRetirar()).thenReturn(List.of(
                paqueteDeHace(Duration.ofHours(23)),   // menos de 24
                paqueteDeHace(Duration.ofHours(24)),   // ya es 24
                paqueteDeHace(Duration.ofHours(71)),   // sigue en 24-72
                paqueteDeHace(Duration.ofHours(72)),   // ya es 72
                paqueteDeHace(Duration.ofDays(7).minusHours(1)),
                paqueteDeHace(Duration.ofDays(7))));   // ya son 7 dias

        EstadisticasDashboard.PaquetesPorAntiguedad tramos =
                servicio().calcular().paquetesPorAntiguedad();

        // Cada tramo coincide con el hito de aviso: un paquete en "mas de 7
        // dias" es exactamente uno que ya se escalo a administracion
        assertThat(tramos.menosDe24Horas()).isEqualTo(1);
        assertThat(tramos.entre24Y72Horas()).isEqualTo(2);
        assertThat(tramos.entre72HorasY7Dias()).isEqualTo(2);
        assertThat(tramos.masDe7Dias()).isEqualTo(1);
    }

    @Test
    void sinPaquetesPendientesTodosLosTramosVanACero() {
        when(ingresos.findByIngresoEnGreaterThanEqual(any())).thenReturn(List.of());
        when(paquetes.sinRetirar()).thenReturn(List.of());

        EstadisticasDashboard.PaquetesPorAntiguedad tramos =
                servicio().calcular().paquetesPorAntiguedad();

        assertThat(tramos.menosDe24Horas()).isZero();
        assertThat(tramos.entre24Y72Horas()).isZero();
        assertThat(tramos.entre72HorasY7Dias()).isZero();
        assertThat(tramos.masDe7Dias()).isZero();
    }
}
