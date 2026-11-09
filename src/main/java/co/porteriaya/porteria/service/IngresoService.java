package co.porteriaya.porteria.service;

import java.time.Clock;
import java.util.List;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.service.ConsumoUsoService;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.evento.EventoPorteria;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.porteria.dto.RespuestaIngreso;
import co.porteriaya.porteria.dto.RespuestaValidacion;
import co.porteriaya.porteria.dto.ResultadoIngreso;
import co.porteriaya.porteria.dto.SolicitudIngreso;
import co.porteriaya.porteria.dto.SolicitudValidacion;
import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.porteria.repository.IngresoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingresos y salidas (§6, §8).
 *
 * Aqui ocurre la segunda evaluacion de la cadena de validacion, la que si
 * escribe: se vuelven a recorrer los 5 pasos y, solo si todo pasa, se descuenta
 * el uso de forma atomica y nace el Ingreso.
 */
@Service
@RequiredArgsConstructor
public class IngresoService {

    private static final String ENTIDAD = "INGRESO";

    private final IngresoRepository ingresos;
    private final ValidacionService validacion;
    private final ConsumoUsoService consumoUso;
    private final BitacoraService bitacora;
    private final EventoPublisher eventos;
    private final Clock clock;

    /**
     * Registra un ingreso con codigo.
     *
     * Devuelve un resultado en vez de lanzar en el rechazo: ver
     * {@link ResultadoIngreso} para el porque. La transaccion tiene que
     * confirmar incluso cuando se deniega, o la alerta de seguridad se perderia.
     */
    @Transactional
    public ResultadoIngreso registrar(SolicitudIngreso solicitud, String actor) {
        RespuestaValidacion resultado = validacion.validar(
                new SolicitudValidacion(solicitud.codigo(), solicitud.visitanteNombre(),
                        solicitud.visitanteDoc4()),
                actor);

        if (!resultado.permitido()) {
            return new ResultadoIngreso.Rechazado(
                    resultado.motivo(), resultado.advertencias(), resultado.detalle());
        }

        if (!resultado.advertencias().isEmpty() && !solicitud.confirmaAdvertencias()) {
            return new ResultadoIngreso.Rechazado(
                    null, resultado.advertencias(), resultado.detalle());
        }

        // Paso 3, esta vez escribiendo: si otro vigilante gano la carrera, esto
        // lanza 409 USOS_AGOTADOS y no se crea ningun ingreso.
        Autorizacion autorizacion = consumoUso.consumirUso(resultado.autorizacion().id());

        Ingreso ingreso = ingresos.save(Ingreso.conAutorizacion(
                autorizacion, solicitud.visitanteNombre(), solicitud.visitanteDoc4(),
                actor, clock.instant()));

        bitacora.registrar(actor, Rol.VIGILANTE, ENTIDAD, ingreso.getId(), "INGRESO_REGISTRADO",
                "%s entro al %s%s".formatted(
                        ingreso.getVisitanteNombre(),
                        ingreso.getResidente().identificador(),
                        solicitud.confirmaAdvertencias() ? " (con advertencias confirmadas)" : ""));

        eventos.publicar(TipoEvento.INGRESO_REGISTRADO, EventoPorteria.datos(
                "ingresoId", ingreso.getId(),
                "apartamento", ingreso.getResidente().identificador(),
                "correoResidente", ingreso.getResidente().getCorreo(),
                "visitanteNombre", ingreso.getVisitanteNombre(),
                "vigilante", actor,
                "ingresoEn", ingreso.getIngresoEn().toString(),
                "advertenciasConfirmadas", resultado.advertencias().stream().map(Enum::name).toList()));

        return new ResultadoIngreso.Registrado(RespuestaIngreso.de(ingreso));
    }

    /** Salida registrada por el vigilante. Cerrar dos veces es 409. */
    @Transactional
    public RespuestaIngreso registrarSalida(Long id, String actor) {
        Ingreso ingreso = ingresos.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el ingreso", id));

        ingreso.registrarSalida(clock.instant());

        bitacora.registrar(actor, Rol.VIGILANTE, ENTIDAD, id, "SALIDA_REGISTRADA",
                "%s salio del %s".formatted(
                        ingreso.getVisitanteNombre(), ingreso.getResidente().identificador()));

        return RespuestaIngreso.de(ingreso);
    }

    @Transactional(readOnly = true)
    public List<RespuestaIngreso> listarDentro() {
        return ingresos.buscarPorEstado(EstadoIngreso.DENTRO).stream()
                .map(RespuestaIngreso::de)
                .toList();
    }
}
