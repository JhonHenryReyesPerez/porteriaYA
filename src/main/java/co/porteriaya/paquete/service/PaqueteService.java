package co.porteriaya.paquete.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.Normalizador;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.common.SolicitudInvalidaException;
import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.config.PorteriaProperties;
import co.porteriaya.evento.EventoPorteria;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.paquete.dto.EntregarPaqueteRequest;
import co.porteriaya.paquete.dto.RecibirPaqueteRequest;
import co.porteriaya.paquete.dto.RespuestaPaquete;
import co.porteriaya.paquete.entity.AutorizadoRetiro;
import co.porteriaya.paquete.entity.EstadoPaquete;
import co.porteriaya.paquete.entity.Paquete;
import co.porteriaya.paquete.repository.AutorizadoRetiroRepository;
import co.porteriaya.paquete.repository.PaqueteRepository;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recepcion y entrega de paquetes (§6, §8). */
@Service
@RequiredArgsConstructor
public class PaqueteService {

    private static final Logger log = LoggerFactory.getLogger(PaqueteService.class);
    private static final String ENTIDAD = "PAQUETE";
    private static final int LIMITE_CODIGO = 1_000_000;
    private static final int INTENTOS_CODIGO = 20;

    private final PaqueteRepository paquetes;
    private final AutorizadoRetiroRepository autorizados;
    private final ResidenteRepository residentes;
    private final BitacoraService bitacora;
    private final EventoPublisher eventos;
    private final PorteriaProperties propiedades;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * Recibe un paquete.
     *
     * Un apartamento inexistente o inactivo NO se registra (§6): es mejor que
     * el vigilante lo rechace en la puerta que acumular paquetes de nadie.
     */
    @Transactional
    public RespuestaPaquete recibir(RecibirPaqueteRequest peticion, String vigilante) {
        Residente residente = resolverResidente(peticion.apartamento());

        if (!residente.isActivo()) {
            throw new SolicitudInvalidaException(
                    "El apartamento %s esta inactivo: no se le registran paquetes"
                            .formatted(peticion.apartamento()));
        }

        Paquete paquete = paquetes.save(new Paquete(
                residente, peticion.descripcion(), peticion.remitente(),
                generarCodigoRetiro(residente), vigilante, clock.instant()));

        log.info("Paquete {} recibido para {}", paquete.getId(), residente.identificador());

        bitacora.registrar(vigilante, Rol.VIGILANTE, ENTIDAD, paquete.getId(), "PAQUETE_RECIBIDO",
                "%s para %s".formatted(paquete.getDescripcion(), residente.identificador()));

        // El codigo va en el payload porque el flujo 3 tiene que enviarselo al
        // residente; en logs no aparece (§5).
        eventos.publicar(TipoEvento.PAQUETE_RECIBIDO, EventoPorteria.datos(
                "paqueteId", paquete.getId(),
                "apartamento", residente.identificador(),
                "correoResidente", residente.getCorreo(),
                "descripcion", paquete.getDescripcion(),
                "remitente", paquete.getRemitente(),
                "codigoRetiro", paquete.getCodigoRetiro(),
                "recibidoEn", paquete.getRecibidoEn().toString()));

        return RespuestaPaquete.de(paquete);
    }

    /**
     * Entrega el paquete por una de las dos vias de §6: el codigo del correo o
     * una persona registrada como autorizada a retirar.
     */
    @Transactional
    public RespuestaPaquete entregar(Long id, EntregarPaqueteRequest peticion, String vigilante) {
        Paquete paquete = paquetes.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el paquete", id));

        if (!peticion.traeCodigo() && !peticion.traePersona()) {
            throw new SolicitudInvalidaException(
                    "Para entregar hace falta el codigo de retiro o el nombre de una persona autorizada");
        }

        String aQuien = acreditar(paquete, peticion);
        paquete.entregar(aQuien, clock.instant());

        bitacora.registrar(vigilante, Rol.VIGILANTE, ENTIDAD, id, "PAQUETE_ENTREGADO",
                "Entregado a %s en %s".formatted(aQuien, paquete.getResidente().identificador()));

        return RespuestaPaquete.de(paquete);
    }

    @Transactional(readOnly = true)
    public List<RespuestaPaquete> listar(EstadoPaquete estado, String apartamento) {
        String torre = null;
        String numero = null;
        if (apartamento != null && apartamento.contains("-")) {
            String[] partes = apartamento.split("-", 2);
            torre = partes[0];
            numero = partes[1];
        }
        return paquetes.buscar(estado, torre, numero).stream()
                .map(RespuestaPaquete::de)
                .toList();
    }

    /**
     * Tarea de §9: recordatorios a las 24 h y 72 h, y escalamiento a los 7 dias.
     *
     * Los tres hitos se evaluan de forma INDEPENDIENTE, cada uno con su
     * bandera, no como un if-else en cascada. Eso importa en dos sitios:
     *
     * - En la demo, donde el reloj salta varios dias de golpe: §19 espera ver
     *   aparecer los recordatorios Y el escalamiento, no solo el ultimo hito.
     * - Para que el orden nunca se invierta. Con un if-else, un paquete de 8
     *   dias escalaria primero y mandaria el recordatorio de 72 h despues, en
     *   la pasada siguiente.
     *
     * En tiempo real, con pasadas de un minuto, solo se cruza un hito cada vez.
     */
    @Transactional
    public int procesarAvisos() {
        Instant ahora = clock.instant();
        PorteriaProperties.Paquete config = propiedades.paquete();
        int avisos = 0;

        for (Paquete paquete : paquetes.sinRetirar()) {
            long horas = paquete.horasSinRetirar(ahora);

            if (!paquete.isAviso24h() && horas >= config.primerRecordatorioHoras()) {
                paquete.marcarAviso24h();
                publicarRecordatorio(paquete, config.primerRecordatorioHoras(), horas);
                avisos++;
            }
            if (!paquete.isAviso72h() && horas >= config.segundoRecordatorioHoras()) {
                paquete.marcarAviso72h();
                publicarRecordatorio(paquete, config.segundoRecordatorioHoras(), horas);
                avisos++;
            }
            if (!paquete.isEscalado() && horas >= config.escalamientoDias() * 24L) {
                paquete.marcarEscalado();
                avisos++;

                eventos.publicar(TipoEvento.PAQUETE_ESCALADO, EventoPorteria.datos(
                        "paqueteId", paquete.getId(),
                        "apartamento", paquete.getResidente().identificador(),
                        "descripcion", paquete.getDescripcion(),
                        "diasSinRetirar", horas / 24,
                        "recibidoEn", paquete.getRecibidoEn().toString()));

                bitacora.registrar("sistema", Rol.ADMINISTRADOR, ENTIDAD, paquete.getId(),
                        "PAQUETE_ESCALADO",
                        "%s dias sin retirar en %s".formatted(
                                horas / 24, paquete.getResidente().identificador()));
            }
        }

        if (avisos > 0) {
            log.info("{} avisos de paquetes emitidos", avisos);
        }
        return avisos;
    }

    private void publicarRecordatorio(Paquete paquete, int hito, long horas) {
        eventos.publicar(TipoEvento.PAQUETE_RECORDATORIO, EventoPorteria.datos(
                "paqueteId", paquete.getId(),
                "apartamento", paquete.getResidente().identificador(),
                "correoResidente", paquete.getResidente().getCorreo(),
                "descripcion", paquete.getDescripcion(),
                "hitoHoras", hito,
                "horasSinRetirar", horas,
                "recibidoEn", paquete.getRecibidoEn().toString()));
    }

    // ------------------------------------------------------------ utilidades

    /** Devuelve a nombre de quien queda la entrega, o lanza 409 si no acredita. */
    private String acreditar(Paquete paquete, EntregarPaqueteRequest peticion) {
        if (peticion.traeCodigo()) {
            if (!paquete.getCodigoRetiro().equals(peticion.codigoRetiro())) {
                throw new TransicionInvalidaException("CODIGO_RETIRO_INVALIDO",
                        "El codigo de retiro no corresponde a este paquete");
            }
            return peticion.traePersona() ? peticion.retiradoPor() : "Residente con codigo";
        }

        // Sin codigo: tiene que estar en la lista de autorizados del apartamento
        Optional<AutorizadoRetiro> encontrado = buscarAutorizado(
                paquete.getResidente().getId(), peticion.retiradoPor(), peticion.doc4());

        return encontrado
                .map(AutorizadoRetiro::getNombre)
                .orElseThrow(() -> new TransicionInvalidaException("NO_AUTORIZADO_A_RETIRAR",
                        "%s no esta autorizado a retirar paquetes de %s".formatted(
                                peticion.retiradoPor(), paquete.getResidente().identificador())));
    }

    private Optional<AutorizadoRetiro> buscarAutorizado(Long residenteId, String nombre, String doc4) {
        String buscado = Normalizador.nombre(nombre);
        return autorizados.findByResidenteIdAndActivoTrue(residenteId).stream()
                .filter(a -> buscado.equals(Normalizador.nombre(a.getNombre())))
                // Si el registro tiene documento, tiene que coincidir
                .filter(a -> a.getDoc4() == null || a.getDoc4().equals(doc4))
                .findFirst();
    }

    /**
     * Codigo de retiro de 6 digitos, unico entre los paquetes sin retirar del
     * mismo apartamento: dos codigos iguales a la vez harian ambigua la entrega.
     */
    private String generarCodigoRetiro(Residente residente) {
        List<String> enUso = paquetes
                .buscar(EstadoPaquete.RECIBIDO, residente.getTorre(), residente.getApartamento())
                .stream()
                .map(Paquete::getCodigoRetiro)
                .toList();

        for (int i = 0; i < INTENTOS_CODIGO; i++) {
            String candidato = "%06d".formatted(aleatorio.nextInt(LIMITE_CODIGO));
            if (!enUso.contains(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un codigo de retiro libre");
    }

    private Residente resolverResidente(String apartamento) {
        String[] partes = apartamento.split("-", 2);
        return residentes.findByTorreAndApartamento(partes[0], partes[1])
                .orElseThrow(() -> RecursoNoEncontradoException.de("el apartamento", apartamento));
    }
}
