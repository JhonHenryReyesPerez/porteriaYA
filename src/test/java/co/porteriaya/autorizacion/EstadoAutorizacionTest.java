package co.porteriaya.autorizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.residente.entity.Residente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * §15 pide probar todas las transiciones validas e invalidas. El mapa del enum
 * es la unica fuente de verdad: Activa -> Agotada | Vencida | Cancelada, y los
 * tres estados finales no salen de ahi.
 */
class EstadoAutorizacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T12:00:00Z");

    private Autorizacion autorizacionActiva() {
        Residente residente = new Residente("A", "101", "Residente", null, null, AHORA);
        return new Autorizacion(residente, TipoAutorizacion.VISITA, "123456", "Visitante",
                "1234", AHORA, AHORA.plusSeconds(3600), null, 1, 120, AHORA);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoAutorizacion.class, names = {"AGOTADA", "VENCIDA", "CANCELADA"})
    void desdeActivaSePuedePasarACualquierEstadoFinal(EstadoAutorizacion destino) {
        assertThat(EstadoAutorizacion.ACTIVA.puedePasarA(destino)).isTrue();

        Autorizacion autorizacion = autorizacionActiva();
        autorizacion.cambiarEstado(destino);

        assertThat(autorizacion.getEstado()).isEqualTo(destino);
    }

    @Test
    void activaNoPuedeVolverASerActiva() {
        assertThat(EstadoAutorizacion.ACTIVA.puedePasarA(EstadoAutorizacion.ACTIVA)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoAutorizacion.class, names = {"AGOTADA", "VENCIDA", "CANCELADA"})
    void losEstadosFinalesNoAdmitenNingunaSalida(EstadoAutorizacion finalizado) {
        assertThat(finalizado.esFinal()).isTrue();
        assertThat(finalizado.destinosPermitidos()).isEmpty();

        Arrays.stream(EstadoAutorizacion.values())
                .forEach(destino -> assertThat(finalizado.puedePasarA(destino)).isFalse());
    }

    @Test
    void activaNoEsUnEstadoFinal() {
        assertThat(EstadoAutorizacion.ACTIVA.esFinal()).isFalse();
    }

    @Test
    void unSaltoInvalidoLanza409() {
        Autorizacion autorizacion = autorizacionActiva();
        autorizacion.cambiarEstado(EstadoAutorizacion.CANCELADA);

        assertThatThrownBy(() -> autorizacion.cambiarEstado(EstadoAutorizacion.AGOTADA))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("CANCELADA")
                .hasMessageContaining("AGOTADA");

        assertThat(autorizacion.getEstado()).isEqualTo(EstadoAutorizacion.CANCELADA);
    }

    @Test
    void repetirElEstadoActualTambienEs409() {
        Autorizacion autorizacion = autorizacionActiva();

        // Nada de 200 silenciosos: cancelar dos veces, o reactivar algo ya
        // activo, es un error del cliente y la UI tiene que poder mostrarlo.
        assertThatThrownBy(() -> autorizacion.cambiarEstado(EstadoAutorizacion.ACTIVA))
                .isInstanceOf(TransicionInvalidaException.class);

        autorizacion.cambiarEstado(EstadoAutorizacion.CANCELADA);

        assertThatThrownBy(() -> autorizacion.cambiarEstado(EstadoAutorizacion.CANCELADA))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void unaAutorizacionNaceActiva() {
        assertThat(autorizacionActiva().getEstado()).isEqualTo(EstadoAutorizacion.ACTIVA);
    }
}
