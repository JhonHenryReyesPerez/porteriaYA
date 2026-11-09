package co.porteriaya.solicitud.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.config.PorteriaProperties;
import co.porteriaya.evento.EventoPorteria;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.porteria.repository.IngresoRepository;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import co.porteriaya.solicitud.dto.AccionAprobacion;
import co.porteriaya.solicitud.dto.RespuestaSolicitud;
import co.porteriaya.solicitud.dto.SolicitudNoAnunciadaRequest;
import co.porteriaya.solicitud.entity.EstadoSolicitud;
import co.porteriaya.solicitud.entity.SolicitudNoAnunciada;
import co.porteriaya.solicitud.entity.TokenAprobacion;
import co.porteriaya.solicitud.repository.SolicitudRepository;
import co.porteriaya.solicitud.repository.TokenAprobacionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprobacion en vivo de visitas no anunciadas (§6). Es una de las cuatro cosas
 * que §17 marca como «nunca recortar».
 */
@Service
@RequiredArgsConstructor
public class SolicitudService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudService.class);
    private static final String ENTIDAD = "SOLICITUD";

    private final SolicitudRepository solicitudes;
    private final TokenAprobacionRepository tokens;
    private final ResidenteRepository residentes;
    private final IngresoRepository ingresos;
    private final GeneradorToken generadorToken;
    private final BitacoraService bitacora;
    private final EventoPublisher eventos;
    private final PorteriaProperties propiedades;
    private final Clock clock;

    /**
     * El vigilante registra la visita y se pide aprobacion al residente.
     *
     * No se crea ningun Ingreso aqui: hasta que haya aprobacion, esta persona
     * no esta dentro ni cuenta en «quien esta dentro».
     */
    @Transactional
    public RespuestaSolicitud crear(SolicitudNoAnunciadaRequest peticion, String vigilante) {
        Residente residente = resolverResidente(peticion.apartamento());

        Instant ahora = clock.instant();
        Instant vence = ahora.plusSeconds(60L * propiedades.aprobacion().timeoutMinutos());

        SolicitudNoAnunciada solicitud = solicitudes.save(new SolicitudNoAnunciada(
                residente, peticion.visitanteNombre(), peticion.visitanteDoc4(),
                peticion.motivo(), vigilante, ahora, vence));

        String enClaro = generadorToken.generar();
        tokens.save(new TokenAprobacion(
                generadorToken.hash(enClaro), solicitud, ahora, vence));

        log.info("Solicitud no anunciada {} creada para {}", solicitud.getId(), residente.identificador());

        bitacora.registrar(vigilante, Rol.VIGILANTE, ENTIDAD, solicitud.getId(),
                "SOLICITUD_CREADA",
                "%s pide subir al %s".formatted(peticion.visitanteNombre(), residente.identificador()));

        // El token en claro viaja aqui y en ningun otro sitio: ni en la base,
        // donde solo esta su hash, ni en los logs (§5, §6).
        eventos.publicar(TipoEvento.VISITA_NO_ANUNCIADA, EventoPorteria.datos(
                "solicitudId", solicitud.getId(),
                "apartamento", residente.identificador(),
                "correoResidente", residente.getCorreo(),
                "visitanteNombre", solicitud.getVisitanteNombre(),
                "motivo", solicitud.getMotivo(),
                "vigilante", vigilante,
                "venceEn", vence.toString(),
                "urlAprobar", enlace(enClaro, AccionAprobacion.APROBAR),
                "urlRechazar", enlace(enClaro, AccionAprobacion.RECHAZAR)));

        return RespuestaSolicitud.de(solicitud);
    }

    /** Lo que muestra la pagina de confirmacion. No consume el token. */
    @Transactional(readOnly = true)
    public RespuestaSolicitud consultarPorToken(String tokenEnClaro) {
        return RespuestaSolicitud.de(buscarToken(tokenEnClaro).getSolicitud());
    }

    /**
     * Aplica la decision del residente y quema el token.
     *
     * Si aprueba, aqui y solo aqui nace el Ingreso. Si rechaza, no nace nada, y
     * como RECHAZADA es un estado final el vigilante no puede darle la vuelta.
     */
    @Transactional
    public RespuestaSolicitud decidirConToken(String tokenEnClaro, AccionAprobacion accion) {
        TokenAprobacion token = buscarToken(tokenEnClaro);
        SolicitudNoAnunciada solicitud = token.getSolicitud();
        Instant ahora = clock.instant();

        // Lanza 409 si ya se uso o si vencio: de aqui sale el "un solo uso"
        token.usar(accion, ahora);

        EstadoSolicitud destino = accion == AccionAprobacion.APROBAR
                ? EstadoSolicitud.APROBADA
                : EstadoSolicitud.RECHAZADA;
        solicitud.resolver(destino, ahora);

        if (destino.permiteIngreso()) {
            crearIngresoDe(solicitud, ahora);
        }

        bitacora.registrar(solicitud.getResidente().identificador(), Rol.RESIDENTE, ENTIDAD,
                solicitud.getId(), "SOLICITUD_" + destino,
                "El residente decidio %s para %s".formatted(accion, solicitud.getVisitanteNombre()));

        return RespuestaSolicitud.de(solicitud);
    }

    /**
     * Decision del vigilante cuando el residente no contesto (§6).
     *
     * Solo cabe desde SIN_RESPUESTA: si el residente ya rechazo, el mapa de
     * transiciones lo impide y responde 409.
     */
    @Transactional
    public RespuestaSolicitud decidirPorVigilante(Long id, AccionAprobacion accion, String vigilante) {
        SolicitudNoAnunciada solicitud = solicitudes.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la solicitud", id));
        Instant ahora = clock.instant();

        EstadoSolicitud destino = accion == AccionAprobacion.APROBAR
                ? EstadoSolicitud.AUTORIZADA_POR_VIGILANTE
                : EstadoSolicitud.DENEGADA;
        solicitud.resolver(destino, ahora);

        if (destino.permiteIngreso()) {
            crearIngresoDe(solicitud, ahora);
        }

        bitacora.registrar(vigilante, Rol.VIGILANTE, ENTIDAD, id, "SOLICITUD_" + destino,
                "El vigilante decidio %s tras el silencio del residente".formatted(accion));

        return RespuestaSolicitud.de(solicitud);
    }

    @Transactional(readOnly = true)
    public List<RespuestaSolicitud> listarPorEstado(EstadoSolicitud estado) {
        return solicitudes.buscarPorEstado(estado).stream()
                .map(RespuestaSolicitud::de)
                .toList();
    }

    /**
     * Tarea de §9: las pendientes cuyo plazo se agoto pasan a SIN_RESPUESTA y
     * la decision queda en manos del vigilante.
     *
     * No se emite evento: §10 no define ninguno para esto, y no voy a inventar
     * un tipo nuevo porque la lista es contrato. El aviso al vigilante es que
     * la solicitud le aparece en GET /api/porteria/solicitudes?estado=SIN_RESPUESTA.
     *
     * Idempotente por el filtro de estado de la consulta.
     */
    @Transactional
    public int vencerPendientes() {
        Instant ahora = clock.instant();
        List<SolicitudNoAnunciada> vencidas = solicitudes.pendientesVencidas(ahora);

        for (SolicitudNoAnunciada solicitud : vencidas) {
            solicitud.marcarSinRespuesta(ahora);
            bitacora.registrar("sistema", Rol.VIGILANTE, ENTIDAD, solicitud.getId(),
                    "SOLICITUD_SIN_RESPUESTA",
                    "El residente de %s no respondio en el plazo".formatted(
                            solicitud.getResidente().identificador()));
        }

        if (!vencidas.isEmpty()) {
            log.info("{} solicitudes pasaron a SIN_RESPUESTA", vencidas.size());
        }
        return vencidas.size();
    }

    // ------------------------------------------------------------ utilidades

    private void crearIngresoDe(SolicitudNoAnunciada solicitud, Instant ahora) {
        int duracion = propiedades.autorizaciones()
                .duracionPorDefecto(TipoAutorizacion.VISITA);

        Ingreso ingreso = ingresos.save(Ingreso.sinAnunciar(
                solicitud.getResidente(), solicitud.getVisitanteNombre(),
                solicitud.getVisitanteDoc4(), solicitud.getVigilante(), duracion, ahora));

        solicitud.asociarIngreso(ingreso);

        eventos.publicar(TipoEvento.INGRESO_REGISTRADO, EventoPorteria.datos(
                "ingresoId", ingreso.getId(),
                "solicitudId", solicitud.getId(),
                "apartamento", ingreso.getResidente().identificador(),
                "correoResidente", ingreso.getResidente().getCorreo(),
                "visitanteNombre", ingreso.getVisitanteNombre(),
                "vigilante", ingreso.getVigilante(),
                "origen", ingreso.getOrigen().name(),
                "ingresoEn", ingreso.getIngresoEn().toString()));
    }

    private TokenAprobacion buscarToken(String tokenEnClaro) {
        return tokens.buscarPorHash(generadorToken.hash(tokenEnClaro))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ese enlace de aprobacion no existe"));
    }

    private String enlace(String tokenEnClaro, AccionAprobacion accion) {
        return "%s/api/aprobacion/%s?accion=%s".formatted(
                propiedades.baseUrl(), tokenEnClaro, accion.name().toLowerCase());
    }

    private Residente resolverResidente(String apartamento) {
        String[] partes = apartamento.split("-", 2);
        return residentes.findByTorreAndApartamento(partes[0], partes[1])
                .orElseThrow(() -> RecursoNoEncontradoException.de("el apartamento", apartamento));
    }
}
