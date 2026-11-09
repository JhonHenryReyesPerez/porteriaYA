package co.porteriaya.solicitud.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de una visita no anunciada (§6).
 *
 * Pendiente -> Aprobada | Rechazada | Sin respuesta.
 * Desde Sin respuesta decide el vigilante: Autorizada por vigilante | Denegada.
 *
 * Que RECHAZADA sea final es la regla del contrato: si el residente rechaza, el
 * vigilante no puede darle la vuelta. Solo la administracion puede anular, y
 * eso todavia esta por definir (§20).
 */
public enum EstadoSolicitud {

    PENDIENTE,
    APROBADA,
    RECHAZADA,
    SIN_RESPUESTA,
    AUTORIZADA_POR_VIGILANTE,
    DENEGADA;

    private static final Map<EstadoSolicitud, Set<EstadoSolicitud>> TRANSICIONES;

    static {
        Map<EstadoSolicitud, Set<EstadoSolicitud>> mapa = new EnumMap<>(EstadoSolicitud.class);
        mapa.put(PENDIENTE, EnumSet.of(APROBADA, RECHAZADA, SIN_RESPUESTA));
        mapa.put(SIN_RESPUESTA, EnumSet.of(AUTORIZADA_POR_VIGILANTE, DENEGADA));
        mapa.put(APROBADA, EnumSet.noneOf(EstadoSolicitud.class));
        mapa.put(RECHAZADA, EnumSet.noneOf(EstadoSolicitud.class));
        mapa.put(AUTORIZADA_POR_VIGILANTE, EnumSet.noneOf(EstadoSolicitud.class));
        mapa.put(DENEGADA, EnumSet.noneOf(EstadoSolicitud.class));
        TRANSICIONES = Collections.unmodifiableMap(mapa);
    }

    public boolean puedePasarA(EstadoSolicitud destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public Set<EstadoSolicitud> destinosPermitidos() {
        return TRANSICIONES.get(this);
    }

    public boolean esFinal() {
        return TRANSICIONES.get(this).isEmpty();
    }

    /** Los dos estados con los que el visitante puede entrar. */
    public boolean permiteIngreso() {
        return this == APROBADA || this == AUTORIZADA_POR_VIGILANTE;
    }
}
