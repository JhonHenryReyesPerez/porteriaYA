package co.porteriaya.porteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.porteria.entity.OrigenIngreso;
import co.porteriaya.residente.entity.Residente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Transiciones del ingreso (§6, §15): Dentro -> Salio | Salida no registrada. */
class EstadoIngresoTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T17:00:00Z");

    private Residente residente() {
        return new Residente("A", "101", "Residente A-101", null, null, AHORA);
    }

    private Ingreso ingresoDentro() {
        Autorizacion autorizacion = new Autorizacion(residente(), TipoAutorizacion.VISITA,
                "123456", "Camilo Rojas", "4821", AHORA.minus(Duration.ofHours(1)),
                AHORA.plus(Duration.ofHours(8)), null, 1, 120, AHORA);
        return Ingreso.conAutorizacion(autorizacion, "Camilo Rojas", "4821", "vigilante1", AHORA);
    }

    @Test
    void unIngresoNaceDentro() {
        Ingreso ingreso = ingresoDentro();

        assertThat(ingreso.getEstado()).isEqualTo(EstadoIngreso.DENTRO);
        assertThat(ingreso.estaDentro()).isTrue();
        assertThat(ingreso.getSalidaEn()).isNull();
        assertThat(ingreso.isPermanenciaAvisada()).isFalse();
    }

    @Test
    void unIngresoConCodigoHeredaLaDuracionDeLaAutorizacion() {
        assertThat(ingresoDentro().getDuracionEsperadaMin()).isEqualTo(120);
        assertThat(ingresoDentro().getOrigen()).isEqualTo(OrigenIngreso.AUTORIZACION);
    }

    @Test
    void unIngresoSinAnunciarNoTieneAutorizacionDetras() {
        Ingreso ingreso = Ingreso.sinAnunciar(residente(), "Alguien", "0001", "vigilante1", 60, AHORA);

        assertThat(ingreso.getOrigen()).isEqualTo(OrigenIngreso.NO_ANUNCIADA);
        assertThat(ingreso.getAutorizacion()).isNull();
        assertThat(ingreso.getDuracionEsperadaMin()).isEqualTo(60);
    }

    @Test
    void registrarLaSalidaCierraElIngresoYFechaLaSalida() {
        Ingreso ingreso = ingresoDentro();
        Instant salida = AHORA.plus(Duration.ofMinutes(45));

        ingreso.registrarSalida(salida);

        assertThat(ingreso.getEstado()).isEqualTo(EstadoIngreso.SALIO);
        assertThat(ingreso.getSalidaEn()).isEqualTo(salida);
        assertThat(ingreso.estaDentro()).isFalse();
    }

    @Test
    void cerrarSinSalidaEsElCaminoDelScheduler() {
        Ingreso ingreso = ingresoDentro();

        ingreso.cerrarSinSalida(AHORA.plus(Duration.ofHours(10)));

        assertThat(ingreso.getEstado()).isEqualTo(EstadoIngreso.SALIDA_NO_REGISTRADA);
    }

    @Test
    void registrarDosVecesLaSalidaEs409() {
        Ingreso ingreso = ingresoDentro();
        ingreso.registrarSalida(AHORA.plus(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> ingreso.registrarSalida(AHORA.plus(Duration.ofMinutes(20))))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("SALIO");
    }

    @Test
    void unIngresoYaCerradoNoSePuedeCerrarAutomaticamente() {
        Ingreso ingreso = ingresoDentro();
        ingreso.registrarSalida(AHORA.plus(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> ingreso.cerrarSinSalida(AHORA.plus(Duration.ofHours(10))))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoIngreso.class, names = {"SALIO", "SALIDA_NO_REGISTRADA"})
    void losEstadosFinalesNoAdmitenSalidas(EstadoIngreso finalizado) {
        assertThat(finalizado.esFinal()).isTrue();
        Arrays.stream(EstadoIngreso.values())
                .forEach(destino -> assertThat(finalizado.puedePasarA(destino)).isFalse());
    }

    @Test
    void dentroSoloPuedeIrALosDosCierres() {
        assertThat(EstadoIngreso.DENTRO.destinosPermitidos())
                .containsExactlyInAnyOrder(EstadoIngreso.SALIO, EstadoIngreso.SALIDA_NO_REGISTRADA);
        assertThat(EstadoIngreso.DENTRO.esFinal()).isFalse();
        assertThat(EstadoIngreso.DENTRO.puedePasarA(EstadoIngreso.DENTRO)).isFalse();
    }

    @Test
    void elLimiteDePermanenciaSumaDuracionMasGracia() {
        Ingreso ingreso = ingresoDentro();

        // 120 min de duracion + 30 de gracia = 150 min desde el ingreso
        assertThat(ingreso.limitePermanencia(30)).isEqualTo(AHORA.plus(Duration.ofMinutes(150)));
    }

    @Test
    void laBanderaDePermanenciaSoloSeLevanta() {
        Ingreso ingreso = ingresoDentro();

        ingreso.marcarPermanenciaAvisada();
        ingreso.marcarPermanenciaAvisada();

        assertThat(ingreso.isPermanenciaAvisada()).isTrue();
    }
}
