package co.porteriaya.autorizacion.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import co.porteriaya.autorizacion.dto.RespuestaAutorizacion;
import co.porteriaya.autorizacion.dto.SolicitudAutorizacion;
import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.entity.Recurrencia;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.common.SolicitudInvalidaException;
import co.porteriaya.config.PorteriaProperties;
import co.porteriaya.evento.EventoPorteria;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta, consulta y cancelacion de autorizaciones (§6, §8). */
@Service
@RequiredArgsConstructor
public class AutorizacionService {

    private static final Logger log = LoggerFactory.getLogger(AutorizacionService.class);
    private static final String ENTIDAD = "AUTORIZACION";

    private final AutorizacionRepository autorizaciones;
    private final ResidenteRepository residentes;
    private final GeneradorCodigo generadorCodigo;
    private final BitacoraService bitacora;
    private final EventoPublisher eventos;
    private final PorteriaProperties propiedades;
    private final Clock clock;

    @Transactional
    public RespuestaAutorizacion crear(SolicitudAutorizacion solicitud, String actor) {
        Residente residente = resolverResidente(actor);
        validarCoherencia(solicitud);

        Recurrencia recurrencia = construirRecurrencia(solicitud);
        Integer usosMax = calcularUsosMax(solicitud);
        int duracion = solicitud.duracionEsperadaMin() != null
                ? solicitud.duracionEsperadaMin()
                : propiedades.autorizaciones().duracionPorDefecto(solicitud.tipo());

        Autorizacion autorizacion = new Autorizacion(
                residente,
                solicitud.tipo(),
                generadorCodigo.generar(),
                solicitud.visitanteNombre(),
                solicitud.visitanteDoc4(),
                solicitud.validaDesde(),
                solicitud.validaHasta(),
                recurrencia,
                usosMax,
                duracion,
                clock.instant());

        Autorizacion guardada = autorizaciones.save(autorizacion);

        // Sin el codigo en el log, nunca (§5)
        log.info("Autorizacion {} creada para {} tipo {}",
                guardada.getId(), residente.identificador(), guardada.getTipo());

        bitacora.registrar(actor, Rol.RESIDENTE, ENTIDAD, guardada.getId(), "AUTORIZACION_CREADA",
                "Tipo %s para %s".formatted(guardada.getTipo(), guardada.getVisitanteNombre()));

        // El codigo SI va en el payload: el flujo 1 de n8n tiene que enviarselo
        // al residente por correo (§14). En logs nunca aparece.
        eventos.publicar(TipoEvento.AUTORIZACION_CREADA, EventoPorteria.datos(
                "autorizacionId", guardada.getId(),
                "apartamento", residente.identificador(),
                "correoResidente", residente.getCorreo(),
                "tipo", guardada.getTipo().name(),
                "codigo", guardada.getCodigo(),
                "visitanteNombre", guardada.getVisitanteNombre(),
                "validaDesde", guardada.getValidaDesde().toString(),
                "validaHasta", guardada.getValidaHasta().toString()));

        return RespuestaAutorizacion.de(guardada);
    }

    @Transactional(readOnly = true)
    public List<RespuestaAutorizacion> listarDe(String actor) {
        Residente residente = resolverResidente(actor);
        return autorizaciones.findByResidenteIdOrderByCreadoEnDesc(residente.getId()).stream()
                .map(RespuestaAutorizacion::de)
                .toList();
    }

    /**
     * El DELETE del contrato es una cancelacion logica: pasa a CANCELADA y la
     * fila se queda (§12). Un salto desde un estado final es 409.
     */
    @Transactional
    public RespuestaAutorizacion cancelar(Long id, String actor, Rol rol) {
        Autorizacion autorizacion = autorizaciones.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la autorizacion", id));

        if (rol == Rol.RESIDENTE
                && !autorizacion.getResidente().identificador().equals(actor)) {
            throw RecursoNoEncontradoException.de("la autorizacion", id);
        }

        autorizacion.cambiarEstado(EstadoAutorizacion.CANCELADA);

        bitacora.registrar(actor, rol, ENTIDAD, id, "AUTORIZACION_CANCELADA",
                "Cancelada por " + rol);

        return RespuestaAutorizacion.de(autorizacion);
    }

    private Residente resolverResidente(String actor) {
        if (actor == null || !actor.contains("-")) {
            throw new SolicitudInvalidaException(
                    "X-Actor debe venir como torre-apartamento, por ejemplo A-101");
        }
        String[] partes = actor.split("-", 2);
        return residentes.findByTorreAndApartamento(partes[0], partes[1])
                .orElseThrow(() -> RecursoNoEncontradoException.de("el apartamento", actor));
    }

    /** Reglas de §6 que Bean Validation no puede expresar sola. */
    private void validarCoherencia(SolicitudAutorizacion solicitud) {
        if (!solicitud.validaHasta().isAfter(solicitud.validaDesde())) {
            throw new SolicitudInvalidaException("La fecha de fin debe ser posterior a la de inicio");
        }

        TipoAutorizacion tipo = solicitud.tipo();
        boolean traeRecurrencia = solicitud.traeRecurrencia() || solicitud.traeFranja();

        if (tipo.admiteRecurrencia()) {
            if (!solicitud.traeRecurrencia() || !solicitud.traeFranja()) {
                throw new SolicitudInvalidaException(
                        "Un %s necesita dias de la semana y franja horaria completa".formatted(tipo));
            }
            if (solicitud.horaHasta().isBefore(solicitud.horaDesde())) {
                throw new SolicitudInvalidaException("horaHasta no puede ser anterior a horaDesde");
            }
        } else if (traeRecurrencia) {
            throw new SolicitudInvalidaException(
                    "Un %s no admite dias de la semana ni franja horaria".formatted(tipo));
        }

        if (tipo.usosIlimitados() && solicitud.usosMax() != null) {
            throw new SolicitudInvalidaException(
                    "Un %s tiene usos ilimitados dentro de la franja: no lleva usosMax".formatted(tipo));
        }
        if (tipo == TipoAutorizacion.DOMICILIARIO
                && solicitud.usosMax() != null && solicitud.usosMax() != 1) {
            throw new SolicitudInvalidaException("Un domiciliario es de un solo uso");
        }
    }

    private Recurrencia construirRecurrencia(SolicitudAutorizacion solicitud) {
        if (!solicitud.tipo().admiteRecurrencia()) {
            return null;
        }
        Set<DayOfWeek> dias = solicitud.diasSemana().stream()
                .map(DayOfWeek::of)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        return new Recurrencia(dias, solicitud.horaDesde(), solicitud.horaHasta());
    }

    /** usosMax nulo en la base significa ilimitado (§6). */
    private Integer calcularUsosMax(SolicitudAutorizacion solicitud) {
        TipoAutorizacion tipo = solicitud.tipo();
        if (tipo.usosIlimitados()) {
            return null;
        }
        if (tipo == TipoAutorizacion.DOMICILIARIO) {
            return 1;
        }
        return solicitud.usosMax() != null ? solicitud.usosMax() : 1;
    }
}
