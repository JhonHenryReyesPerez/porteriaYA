package co.porteriaya.solicitud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.solicitud.entity.EstadoSolicitud;
import co.porteriaya.solicitud.entity.SolicitudNoAnunciada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Transiciones de la visita no anunciada (§6, §15).
 *
 * Lo que de verdad hay que blindar aqui: que un rechazo del residente sea
 * final. Si desde RECHAZADA se pudiera llegar a AUTORIZADA_POR_VIGILANTE, el
 * vigilante podria dar la vuelta a la decision del residente, que es justo lo
 * que el contrato prohibe.
 */
class EstadoSolicitudTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T17:00:00Z");

    private SolicitudNoAnunciada solicitudPendiente() {
        Residente residente = new Residente("A", "101", "Residente A-101", null, null, AHORA);
        return new SolicitudNoAnunciada(residente, "Visitante", "0001", "Entrega",
                "vigilante1", AHORA, AHORA.plus(Duration.ofMinutes(3)));
    }

    @Test
    void unaSolicitudNacePendiente() {
        SolicitudNoAnunciada solicitud = solicitudPendiente();

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(solicitud.estaPendiente()).isTrue();
        assertThat(solicitud.getResueltoEn()).isNull();
        assertThat(solicitud.getIngreso()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSolicitud.class, names = {"APROBADA", "RECHAZADA", "SIN_RESPUESTA"})
    void desdePendienteSeLlegaALosTresDestinos(EstadoSolicitud destino) {
        assertThat(EstadoSolicitud.PENDIENTE.puedePasarA(destino)).isTrue();
    }

    @Test
    void desdeSinRespuestaDecideElVigilante() {
        assertThat(EstadoSolicitud.SIN_RESPUESTA.destinosPermitidos())
                .containsExactlyInAnyOrder(
                        EstadoSolicitud.AUTORIZADA_POR_VIGILANTE, EstadoSolicitud.DENEGADA);
    }

    @Test
    void unRechazoDelResidenteEsFinalYElVigilanteNoLoPuedeRevertir() {
        assertThat(EstadoSolicitud.RECHAZADA.esFinal()).isTrue();
        assertThat(EstadoSolicitud.RECHAZADA.puedePasarA(EstadoSolicitud.AUTORIZADA_POR_VIGILANTE))
                .isFalse();

        SolicitudNoAnunciada solicitud = solicitudPendiente();
        solicitud.resolver(EstadoSolicitud.RECHAZADA, AHORA.plus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> solicitud.resolver(
                EstadoSolicitud.AUTORIZADA_POR_VIGILANTE, AHORA.plus(Duration.ofMinutes(2))))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSolicitud.class,
            names = {"APROBADA", "RECHAZADA", "AUTORIZADA_POR_VIGILANTE", "DENEGADA"})
    void losCuatroEstadosDeCierreNoAdmitenSalidas(EstadoSolicitud cerrado) {
        assertThat(cerrado.esFinal()).isTrue();
        Arrays.stream(EstadoSolicitud.values())
                .forEach(destino -> assertThat(cerrado.puedePasarA(destino)).isFalse());
    }

    @Test
    void soloAprobadaYAutorizadaPorVigilantePermitenIngreso() {
        assertThat(EstadoSolicitud.APROBADA.permiteIngreso()).isTrue();
        assertThat(EstadoSolicitud.AUTORIZADA_POR_VIGILANTE.permiteIngreso()).isTrue();

        assertThat(EstadoSolicitud.PENDIENTE.permiteIngreso()).isFalse();
        assertThat(EstadoSolicitud.RECHAZADA.permiteIngreso()).isFalse();
        assertThat(EstadoSolicitud.DENEGADA.permiteIngreso()).isFalse();
        assertThat(EstadoSolicitud.SIN_RESPUESTA.permiteIngreso()).isFalse();
    }

    @Test
    void resolverFechaLaDecision() {
        SolicitudNoAnunciada solicitud = solicitudPendiente();
        Instant decision = AHORA.plus(Duration.ofMinutes(2));

        solicitud.resolver(EstadoSolicitud.APROBADA, decision);

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(solicitud.getResueltoEn()).isEqualTo(decision);
        assertThat(solicitud.estaPendiente()).isFalse();
    }

    @Test
    void elVencimientoSeMideContraElPlazo() {
        SolicitudNoAnunciada solicitud = solicitudPendiente();

        assertThat(solicitud.haVencido(AHORA.plus(Duration.ofMinutes(2)))).isFalse();
        assertThat(solicitud.haVencido(AHORA.plus(Duration.ofMinutes(3)))).isFalse();
        assertThat(solicitud.haVencido(AHORA.plus(Duration.ofMinutes(4)))).isTrue();
    }

    @Test
    void marcarSinRespuestaSoloCabeDesdePendiente() {
        SolicitudNoAnunciada solicitud = solicitudPendiente();
        solicitud.marcarSinRespuesta(AHORA.plus(Duration.ofMinutes(4)));

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.SIN_RESPUESTA);

        // Una segunda pasada del scheduler no debe poder repetirlo
        assertThatThrownBy(() -> solicitud.marcarSinRespuesta(AHORA.plus(Duration.ofMinutes(5))))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void unaSolicitudSinAprobarNoPuedeTenerIngreso() {
        SolicitudNoAnunciada solicitud = solicitudPendiente();

        assertThatThrownBy(() -> solicitud.asociarIngreso(null))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("PENDIENTE");
    }
}
