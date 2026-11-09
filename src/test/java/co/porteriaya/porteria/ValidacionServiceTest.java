package co.porteriaya.porteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.Optional;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.entity.Recurrencia;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.Rol;
import co.porteriaya.config.ClockConfig;
import co.porteriaya.nopermitido.entity.NoPermitido;
import co.porteriaya.nopermitido.service.Coincidencia;
import co.porteriaya.nopermitido.service.NoPermitidoService;
import co.porteriaya.porteria.dto.Advertencia;
import co.porteriaya.porteria.dto.MotivoRechazo;
import co.porteriaya.porteria.dto.RespuestaValidacion;
import co.porteriaya.porteria.dto.SolicitudValidacion;
import co.porteriaya.porteria.service.ValidacionService;
import co.porteriaya.residente.entity.Residente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * La cadena de validacion de §6, paso a paso y en orden.
 *
 * Lo que mas importa aqui no es que cada paso rechace, sino que rechace EN SU
 * TURNO: el motivo que ve el vigilante depende del orden, asi que hay un test
 * por cada frontera entre pasos.
 *
 * Reloj fijo siempre, nunca Thread.sleep (§9).
 */
@ExtendWith(MockitoExtension.class)
class ValidacionServiceTest {

    private static final String ACTOR = "vigilante1";
    private static final String CODIGO = "100001";

    /** Un lunes a las 10:00 en America/Bogota, para que la recurrencia sea determinista. */
    private static final ZonedDateTime LUNES_10AM = ZonedDateTime.of(
            LocalDate.of(2026, 11, 9).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)),
            LocalTime.of(10, 0),
            ClockConfig.ZONA);

    private static final Instant AHORA = LUNES_10AM.toInstant();

    @Mock
    private AutorizacionRepository autorizaciones;
    @Mock
    private NoPermitidoService noPermitidos;
    @Mock
    private BitacoraService bitacora;

    private ValidacionService servicio;

    @BeforeEach
    void prepararServicio() {
        Clock reloj = Clock.fixed(AHORA, ClockConfig.ZONA);
        servicio = new ValidacionService(autorizaciones, noPermitidos, bitacora, reloj);
    }

    // ------------------------------------------------------------ utilidades

    private Residente residente() {
        return new Residente("A", "101", "Residente A-101", null, null, AHORA);
    }

    private Autorizacion visita(Integer usosMax) {
        return new Autorizacion(residente(), TipoAutorizacion.VISITA, CODIGO, "Camilo Rojas",
                "4821", AHORA.minus(Duration.ofHours(1)), AHORA.plus(Duration.ofHours(8)),
                null, usosMax, 120, AHORA);
    }

    private Autorizacion recurrente(LocalTime desde, LocalTime hasta, DayOfWeek... dias) {
        Recurrencia recurrencia = new Recurrencia(EnumSet.copyOf(java.util.List.of(dias)), desde, hasta);
        return new Autorizacion(residente(), TipoAutorizacion.SERVICIO_RECURRENTE, CODIGO,
                "Marta Quintero", "5567", AHORA.minus(Duration.ofDays(10)),
                AHORA.plus(Duration.ofDays(30)), recurrencia, null, 300, AHORA);
    }

    /**
     * usosActuales solo crece con el UPDATE atomico de la base, asi que para
     * simular una autorizacion ya gastada hay que ponerlo por reflexion. La
     * alternativa seria un constructor que solo existiria para los tests.
     */
    private void gastarUsos(Autorizacion autorizacion, int usados) {
        ReflectionTestUtils.setField(autorizacion, "usosActuales", usados);
    }

    private void enEstado(Autorizacion autorizacion, EstadoAutorizacion estado) {
        ReflectionTestUtils.setField(autorizacion, "estado", estado);
    }

    private SolicitudValidacion solicitud() {
        return new SolicitudValidacion(CODIGO, "Camilo Rojas", "4821");
    }

    private RespuestaValidacion validar() {
        return servicio.validar(solicitud(), ACTOR);
    }

    // --------------------------------------------------- paso 1: existe/cancelada

    @Test
    void pasoUnoRechazaUnCodigoQueNoExiste() {
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.empty());

        RespuestaValidacion respuesta = validar();

        assertThat(respuesta.permitido()).isFalse();
        assertThat(respuesta.motivo()).isEqualTo(MotivoRechazo.CODIGO_NO_EXISTE);
        verifyNoInteractions(noPermitidos);
    }

    @Test
    void pasoUnoRechazaUnaAutorizacionCancelada() {
        Autorizacion autorizacion = visita(1);
        enEstado(autorizacion, EstadoAutorizacion.CANCELADA);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        RespuestaValidacion respuesta = validar();

        assertThat(respuesta.motivo()).isEqualTo(MotivoRechazo.AUTORIZACION_CANCELADA);
    }

    // --------------------------------------------------------- paso 2: ventana

    @Test
    void pasoDosRechazaAntesDeQueEmpieceLaVentana() {
        Autorizacion autorizacion = new Autorizacion(residente(), TipoAutorizacion.VISITA, CODIGO,
                "Camilo Rojas", "4821", AHORA.plus(Duration.ofHours(1)),
                AHORA.plus(Duration.ofHours(5)), null, 1, 120, AHORA);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.FUERA_DE_VENTANA);
    }

    @Test
    void pasoDosRechazaDespuesDeQueTermineLaVentana() {
        Autorizacion autorizacion = new Autorizacion(residente(), TipoAutorizacion.VISITA, CODIGO,
                "Camilo Rojas", "4821", AHORA.minus(Duration.ofDays(2)),
                AHORA.minus(Duration.ofDays(1)), null, 1, 120, AHORA);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.FUERA_DE_VENTANA);
    }

    @Test
    void pasoDosRechazaUnRecurrenteEnUnDiaQueNoLeToca() {
        // Hoy es lunes; la autorizacion solo vale martes y jueves
        Autorizacion autorizacion = recurrente(
                LocalTime.of(7, 0), LocalTime.of(12, 0), DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.FUERA_DE_FRANJA);
    }

    @Test
    void pasoDosRechazaUnRecurrenteFueraDeLaFranjaHoraria() {
        // Hoy es lunes a las 10:00; la franja es de 14:00 a 18:00
        Autorizacion autorizacion = recurrente(
                LocalTime.of(14, 0), LocalTime.of(18, 0), DayOfWeek.MONDAY);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.FUERA_DE_FRANJA);
    }

    @Test
    void pasoDosAceptaUnRecurrenteDentroDeSuFranja() {
        Autorizacion autorizacion = recurrente(
                LocalTime.of(7, 0), LocalTime.of(12, 0), DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));
        when(noPermitidos.evaluar(anyString(), anyString())).thenReturn(Coincidencia.ninguna());

        RespuestaValidacion respuesta = validar();

        assertThat(respuesta.permitido()).isTrue();
        // Ilimitado: no hay usos restantes que mostrar
        assertThat(respuesta.autorizacion().usosRestantes()).isNull();
    }

    // ------------------------------------------------------------ paso 3: usos

    @Test
    void pasoTresRechazaCuandoNoQuedanUsos() {
        Autorizacion autorizacion = visita(2);
        gastarUsos(autorizacion, 2);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.USOS_AGOTADOS);
    }

    @Test
    void pasoTresNoFrenaLosUsosIlimitados() {
        Autorizacion autorizacion = recurrente(
                LocalTime.of(7, 0), LocalTime.of(12, 0), DayOfWeek.MONDAY);
        gastarUsos(autorizacion, 500);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));
        when(noPermitidos.evaluar(anyString(), anyString())).thenReturn(Coincidencia.ninguna());

        assertThat(validar().permitido()).isTrue();
    }

    // --------------------------------------------------- paso 4: no permitidos

    @Test
    void pasoCuatroNiegaUnaCoincidenciaExactaYLaRegistra() {
        Autorizacion autorizacion = visita(1);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));
        NoPermitido registro = new NoPermitido("Camilo Rojas", "4821",
                "Agresion al personal", "admin1", AHORA);
        when(noPermitidos.evaluar(anyString(), anyString()))
                .thenReturn(new Coincidencia(Coincidencia.Nivel.EXACTA, registro));

        RespuestaValidacion respuesta = validar();

        assertThat(respuesta.permitido()).isFalse();
        assertThat(respuesta.motivo()).isEqualTo(MotivoRechazo.PERSONA_NO_PERMITIDA);
        assertThat(respuesta.detalle()).isEqualTo("Agresion al personal");
        // Es una alerta de seguridad: queda en bitacora aunque el vigilante no siga
        verify(bitacora).registrar(any(), any(Rol.class), anyString(), any(), anyString(), anyString());
    }

    @Test
    void pasoCuatroAvisaDeUnaCoincidenciaParcialSinNegar() {
        Autorizacion autorizacion = visita(1);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));
        NoPermitido registro = new NoPermitido("Camilo Rojas", "0000",
                "Hurto en zonas comunes", "admin1", AHORA);
        when(noPermitidos.evaluar(anyString(), anyString()))
                .thenReturn(new Coincidencia(Coincidencia.Nivel.PARCIAL, registro));

        RespuestaValidacion respuesta = validar();

        assertThat(respuesta.permitido()).isTrue();
        assertThat(respuesta.motivo()).isNull();
        assertThat(respuesta.advertencias()).containsExactly(Advertencia.POSIBLE_NO_PERMITIDO);
        assertThat(respuesta.detalle()).isEqualTo("Hurto en zonas comunes");
        verifyNoInteractions(bitacora);
    }

    // ------------------------------------------------------- el orden importa

    @Test
    void unaCanceladaConPersonaNoPermitidaDevuelveElMotivoDelPasoUno() {
        Autorizacion autorizacion = visita(1);
        enEstado(autorizacion, EstadoAutorizacion.CANCELADA);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.AUTORIZACION_CANCELADA);
        // El paso 4 no llega a ejecutarse
        verifyNoInteractions(noPermitidos);
    }

    @Test
    void fueraDeVentanaYSinUsosDevuelveElMotivoDelPasoDos() {
        Autorizacion autorizacion = new Autorizacion(residente(), TipoAutorizacion.VISITA, CODIGO,
                "Camilo Rojas", "4821", AHORA.minus(Duration.ofDays(5)),
                AHORA.minus(Duration.ofDays(4)), null, 1, 120, AHORA);
        gastarUsos(autorizacion, 1);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.FUERA_DE_VENTANA);
    }

    @Test
    void sinUsosYConPersonaNoPermitidaDevuelveElMotivoDelPasoTres() {
        Autorizacion autorizacion = visita(1);
        gastarUsos(autorizacion, 1);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));

        assertThat(validar().motivo()).isEqualTo(MotivoRechazo.USOS_AGOTADOS);
        verifyNoInteractions(noPermitidos);
    }

    // ------------------------------------------------------- validar no escribe

    @Test
    void validarNuncaConsumeUnUso() {
        Autorizacion autorizacion = visita(1);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));
        when(noPermitidos.evaluar(anyString(), anyString())).thenReturn(Coincidencia.ninguna());

        validar();
        validar();
        validar();

        // Esta es la decision de §6: el descuento atomico vive en /ingresos,
        // asi que consultar un codigo tres veces no lo gasta.
        verify(autorizaciones, never()).consumirUso(anyLong());
        assertThat(autorizacion.getUsosActuales()).isZero();
    }

    @Test
    void unaValidacionLimpiaDevuelveElResumenParaElVigilante() {
        Autorizacion autorizacion = visita(3);
        gastarUsos(autorizacion, 1);
        when(autorizaciones.findByCodigo(CODIGO)).thenReturn(Optional.of(autorizacion));
        when(noPermitidos.evaluar(anyString(), anyString())).thenReturn(Coincidencia.ninguna());

        RespuestaValidacion respuesta = validar();

        assertThat(respuesta.permitido()).isTrue();
        assertThat(respuesta.advertencias()).isEmpty();
        assertThat(respuesta.autorizacion().apartamento()).isEqualTo("A-101");
        assertThat(respuesta.autorizacion().visitanteNombre()).isEqualTo("Camilo Rojas");
        assertThat(respuesta.autorizacion().usosRestantes()).isEqualTo(2);
        assertThat(respuesta.autorizacion().duracionEsperadaMin()).isEqualTo(120);
    }
}
