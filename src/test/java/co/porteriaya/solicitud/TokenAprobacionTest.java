package co.porteriaya.solicitud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.solicitud.dto.AccionAprobacion;
import co.porteriaya.solicitud.entity.SolicitudNoAnunciada;
import co.porteriaya.solicitud.entity.TokenAprobacion;
import co.porteriaya.solicitud.service.GeneradorToken;
import org.junit.jupiter.api.Test;

/**
 * §15 lo pide explicito: el token de aprobacion es de un solo uso y vence.
 * Es lo unico que protege el enlace que viaja por correo.
 */
class TokenAprobacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T17:00:00Z");
    private static final Instant VENCE = AHORA.plus(Duration.ofMinutes(3));

    private final GeneradorToken generador = new GeneradorToken();

    private TokenAprobacion token() {
        Residente residente = new Residente("A", "101", "Residente A-101", null, null, AHORA);
        SolicitudNoAnunciada solicitud = new SolicitudNoAnunciada(
                residente, "Visitante", "0001", "Entrega", "vigilante1", AHORA, VENCE);
        return new TokenAprobacion("a".repeat(64), solicitud, AHORA, VENCE);
    }

    // ------------------------------------------------------------- un solo uso

    @Test
    void unTokenNaceSinUsar() {
        TokenAprobacion token = token();

        assertThat(token.estaUsado()).isFalse();
        assertThat(token.getUsadoEn()).isNull();
        assertThat(token.getAccion()).isNull();
    }

    @Test
    void usarloLoQuemaYDejaConstanciaDeLaDecision() {
        TokenAprobacion token = token();
        Instant uso = AHORA.plus(Duration.ofMinutes(1));

        token.usar(AccionAprobacion.APROBAR, uso);

        assertThat(token.estaUsado()).isTrue();
        assertThat(token.getUsadoEn()).isEqualTo(uso);
        assertThat(token.getAccion()).isEqualTo(AccionAprobacion.APROBAR);
    }

    @Test
    void elSegundoUsoDelMismoEnlaceEs409() {
        TokenAprobacion token = token();
        token.usar(AccionAprobacion.APROBAR, AHORA.plus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> token.usar(
                AccionAprobacion.RECHAZAR, AHORA.plus(Duration.ofMinutes(2))))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("ya se uso");

        // Y la decision original no se sobreescribe
        assertThat(token.getAccion()).isEqualTo(AccionAprobacion.APROBAR);
    }

    // ---------------------------------------------------------- vencimiento

    @Test
    void justoEnElVencimientoTodaviaSirve() {
        TokenAprobacion token = token();

        assertThat(token.haVencido(VENCE)).isFalse();
        token.usar(AccionAprobacion.APROBAR, VENCE);

        assertThat(token.estaUsado()).isTrue();
    }

    @Test
    void unSegundoDespuesDelVencimientoYaNo() {
        TokenAprobacion token = token();
        Instant tarde = VENCE.plusSeconds(1);

        assertThat(token.haVencido(tarde)).isTrue();
        assertThatThrownBy(() -> token.usar(AccionAprobacion.APROBAR, tarde))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("vencio");
    }

    @Test
    void unTokenVencidoNoSeConsumeAlIntentarUsarlo() {
        TokenAprobacion token = token();

        assertThatThrownBy(() -> token.usar(AccionAprobacion.APROBAR, VENCE.plusSeconds(60)))
                .isInstanceOf(TransicionInvalidaException.class);

        assertThat(token.estaUsado()).isFalse();
    }

    // ------------------------------------------------------------- generador

    @Test
    void elHashTieneLaLongitudQueEsperaLaColumna() {
        // La columna es VARCHAR(64): SHA-256 en hexadecimal
        assertThat(generador.hash(generador.generar())).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void elHashEsDeterministaPeroNoReversibleAVistaDeOjo() {
        String enClaro = generador.generar();

        assertThat(generador.hash(enClaro)).isEqualTo(generador.hash(enClaro));
        assertThat(generador.hash(enClaro)).isNotEqualTo(enClaro);
        assertThat(generador.hash(enClaro)).doesNotContain(enClaro);
    }

    @Test
    void dosTokensDistintosDanHashesDistintos() {
        assertThat(generador.hash("uno")).isNotEqualTo(generador.hash("dos"));
    }

    @Test
    void losTokensGeneradosNoSeRepiten() {
        var distintos = IntStream.range(0, 500)
                .mapToObj(i -> generador.generar())
                .collect(Collectors.toSet());

        assertThat(distintos).hasSize(500);
    }

    @Test
    void elTokenEsSeguroEnUnaUrl() {
        String enClaro = generador.generar();

        // Base64 url-safe sin relleno: nada que escapar en el enlace del correo
        assertThat(enClaro).matches("[A-Za-z0-9_-]+");
        assertThat(enClaro).doesNotContain("+").doesNotContain("/").doesNotContain("=");
    }
}
