package co.porteriaya.porteria.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.Rol;
import co.porteriaya.nopermitido.service.Coincidencia;
import co.porteriaya.nopermitido.service.NoPermitidoService;
import co.porteriaya.porteria.dto.Advertencia;
import co.porteriaya.porteria.dto.MotivoRechazo;
import co.porteriaya.porteria.dto.RespuestaValidacion;
import co.porteriaya.porteria.dto.SolicitudValidacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadena de validacion de un codigo (§6), en el orden exacto del contrato. El
 * motivo devuelto depende de ese orden, asi que no se reordena.
 *
 * Esta clase NO consume usos: solo lee. El descuento atomico ocurre al
 * registrar el ingreso, en POST /api/porteria/ingresos. La unica escritura que
 * hace es la entrada en bitacora cuando salta PERSONA_NO_PERMITIDA, porque esa
 * es una alerta de seguridad y debe quedar registrada aunque el vigilante no
 * siga adelante.
 */
@Service
@RequiredArgsConstructor
public class ValidacionService {

    private static final String ENTIDAD = "AUTORIZACION";

    private final AutorizacionRepository autorizaciones;
    private final NoPermitidoService noPermitidos;
    private final BitacoraService bitacora;
    private final Clock clock;

    @Transactional
    public RespuestaValidacion validar(SolicitudValidacion solicitud, String actor) {
        // --- Paso 1: el codigo existe y la autorizacion no esta cancelada ---
        Optional<Autorizacion> encontrada = autorizaciones.findByCodigo(solicitud.codigo());
        if (encontrada.isEmpty()) {
            return RespuestaValidacion.rechazo(MotivoRechazo.CODIGO_NO_EXISTE);
        }
        Autorizacion autorizacion = encontrada.get();
        if (autorizacion.getEstado() == EstadoAutorizacion.CANCELADA) {
            return RespuestaValidacion.rechazo(MotivoRechazo.AUTORIZACION_CANCELADA);
        }

        // --- Paso 2: fecha y hora dentro de la ventana; si es recurrente, dia y franja ---
        Instant ahora = clock.instant();
        if (ahora.isBefore(autorizacion.getValidaDesde()) || ahora.isAfter(autorizacion.getValidaHasta())) {
            return RespuestaValidacion.rechazo(MotivoRechazo.FUERA_DE_VENTANA);
        }
        if (autorizacion.esRecurrente() && !cubreLaFranja(autorizacion)) {
            return RespuestaValidacion.rechazo(MotivoRechazo.FUERA_DE_FRANJA);
        }

        // --- Paso 3: quedan usos. Aqui solo se LEE; el descuento es en /ingresos ---
        if (!autorizacion.usosIlimitados()
                && autorizacion.getUsosActuales() >= autorizacion.getUsosMax()) {
            return RespuestaValidacion.rechazo(MotivoRechazo.USOS_AGOTADOS);
        }

        // --- Paso 4: lista de no permitidos, con los dos niveles de coincidencia ---
        List<Advertencia> advertencias = new ArrayList<>();
        Coincidencia coincidencia = noPermitidos.evaluar(
                solicitud.visitanteNombre(), solicitud.visitanteDoc4());

        if (coincidencia.niega()) {
            bitacora.registrar(actor, Rol.VIGILANTE, ENTIDAD, autorizacion.getId(),
                    "INGRESO_NEGADO_NO_PERMITIDO",
                    "Coincidencia exacta con la lista de no permitidos. Motivo del registro: "
                            + coincidencia.motivo());
            // El evento PERSONA_NO_PERMITIDA de §10 se emite aqui cuando exista
            // el publicador de eventos (paso 3 del orden de construccion).
            return RespuestaValidacion.rechazo(MotivoRechazo.PERSONA_NO_PERMITIDA, coincidencia.motivo());
        }
        if (coincidencia.avisa()) {
            advertencias.add(Advertencia.POSIBLE_NO_PERMITIDO);
        }

        // --- Paso 5: ya figura como Dentro con la misma autorizacion ---
        // Necesita el repositorio de ingresos, que llega en el paso 3. Cuando
        // exista, aqui se anade Advertencia.YA_DENTRO.

        return RespuestaValidacion.permitido(resumir(autorizacion), advertencias,
                coincidencia.avisa() ? coincidencia.motivo() : null);
    }

    private boolean cubreLaFranja(Autorizacion autorizacion) {
        ZonedDateTime ahora = ZonedDateTime.now(clock);
        return autorizacion.recurrencia().map(r -> r.cubre(ahora)).orElse(true);
    }

    private RespuestaValidacion.ResumenAutorizacion resumir(Autorizacion autorizacion) {
        Integer restantes = autorizacion.usosIlimitados()
                ? null
                : autorizacion.getUsosMax() - autorizacion.getUsosActuales();
        return new RespuestaValidacion.ResumenAutorizacion(
                autorizacion.getId(),
                autorizacion.getTipo(),
                autorizacion.getResidente().identificador(),
                autorizacion.getVisitanteNombre(),
                restantes,
                autorizacion.getDuracionEsperadaMin(),
                autorizacion.getValidaHasta());
    }
}
