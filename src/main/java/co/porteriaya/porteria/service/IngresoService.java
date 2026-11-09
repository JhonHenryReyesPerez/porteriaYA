package co.porteriaya.porteria.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.service.ConsumoUsoService;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.config.ClockConfig;
import co.porteriaya.config.PorteriaProperties;
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
    private final PorteriaProperties propiedades;
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

    /**
     * Tarea de §9: avisa de las permanencias excedidas, UNA SOLA VEZ.
     *
     * La bandera permanenciaAvisada es lo que lo garantiza, y por eso la
     * consulta ya filtra por ella: el scheduler corre cada minuto y sin esto
     * el residente recibiria el mismo aviso 1440 veces al dia.
     */
    @Transactional
    public int avisarPermanenciasExcedidas() {
        Instant ahora = clock.instant();
        int gracia = propiedades.permanencia().graciaMinutos();
        int avisados = 0;

        for (Ingreso ingreso : ingresos.dentroSinAvisarDePermanencia()) {
            if (ahora.isAfter(ingreso.limitePermanencia(gracia))) {
                ingreso.marcarPermanenciaAvisada();
                avisados++;

                eventos.publicar(TipoEvento.PERMANENCIA_EXCEDIDA, EventoPorteria.datos(
                        "ingresoId", ingreso.getId(),
                        "apartamento", ingreso.getResidente().identificador(),
                        "visitanteNombre", ingreso.getVisitanteNombre(),
                        "duracionEsperadaMin", ingreso.getDuracionEsperadaMin(),
                        "ingresoEn", ingreso.getIngresoEn().toString()));

                bitacora.registrar("sistema", Rol.VIGILANTE, ENTIDAD, ingreso.getId(),
                        "PERMANENCIA_EXCEDIDA",
                        "%s lleva mas de lo esperado en %s".formatted(
                                ingreso.getVisitanteNombre(),
                                ingreso.getResidente().identificador()));
            }
        }
        return avisados;
    }

    /**
     * Tarea de §9: cierra los ingresos que siguen DENTRO al terminar la ventana
     * de su autorizacion o a medianoche, lo que ocurra primero.
     *
     * Medianoche se calcula en America/Bogota, no en UTC: un ingreso de las
     * 20:00 hora local debe cerrarse en la madrugada siguiente local, que en
     * UTC es el mismo dia a las 05:00.
     */
    @Transactional
    public int cerrarLosQueNoRegistraronSalida() {
        Instant ahora = clock.instant();
        int cerrados = 0;

        for (Ingreso ingreso : ingresos.dentro()) {
            if (ahora.isAfter(limiteDePermanencia(ingreso))) {
                ingreso.cerrarSinSalida(ahora);
                cerrados++;

                eventos.publicar(TipoEvento.SALIDA_NO_REGISTRADA, EventoPorteria.datos(
                        "ingresoId", ingreso.getId(),
                        "apartamento", ingreso.getResidente().identificador(),
                        "visitanteNombre", ingreso.getVisitanteNombre(),
                        "ingresoEn", ingreso.getIngresoEn().toString()));

                bitacora.registrar("sistema", Rol.VIGILANTE, ENTIDAD, ingreso.getId(),
                        "SALIDA_NO_REGISTRADA",
                        "Cierre automatico de %s en %s".formatted(
                                ingreso.getVisitanteNombre(),
                                ingreso.getResidente().identificador()));
            }
        }
        return cerrados;
    }

    /** Fin de la ventana o medianoche local, lo que llegue antes. */
    private Instant limiteDePermanencia(Ingreso ingreso) {
        ZonedDateTime entrada = ingreso.getIngresoEn().atZone(ClockConfig.ZONA);
        Instant medianoche = entrada.toLocalDate().plusDays(1)
                .atStartOfDay(ClockConfig.ZONA).toInstant();

        if (ingreso.getAutorizacion() == null) {
            // Visita no anunciada: no hay ventana, solo medianoche
            return medianoche;
        }
        Instant finVentana = ingreso.getAutorizacion().getValidaHasta();
        return finVentana.isBefore(medianoche) ? finVentana : medianoche;
    }

    @Transactional(readOnly = true)
    public List<RespuestaIngreso> listarDentro() {
        return ingresos.buscarPorEstado(EstadoIngreso.DENTRO).stream()
                .map(RespuestaIngreso::de)
                .toList();
    }
}
