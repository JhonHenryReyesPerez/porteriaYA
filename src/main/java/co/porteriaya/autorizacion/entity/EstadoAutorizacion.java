package co.porteriaya.autorizacion.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de §6 con su mapa de transiciones permitidas. Todo cambio pasa por
 * {@link #puedePasarA(EstadoAutorizacion)}; un salto invalido es un 409.
 *
 * Activa -> Agotada | Vencida | Cancelada. Los tres finales no salen de ahi.
 */
public enum EstadoAutorizacion {

    ACTIVA,
    AGOTADA,
    VENCIDA,
    CANCELADA;

    private static final Map<EstadoAutorizacion, Set<EstadoAutorizacion>> TRANSICIONES;

    static {
        Map<EstadoAutorizacion, Set<EstadoAutorizacion>> mapa = new EnumMap<>(EstadoAutorizacion.class);
        mapa.put(ACTIVA, EnumSet.of(AGOTADA, VENCIDA, CANCELADA));
        mapa.put(AGOTADA, EnumSet.noneOf(EstadoAutorizacion.class));
        mapa.put(VENCIDA, EnumSet.noneOf(EstadoAutorizacion.class));
        mapa.put(CANCELADA, EnumSet.noneOf(EstadoAutorizacion.class));
        TRANSICIONES = Collections.unmodifiableMap(mapa);
    }

    public boolean puedePasarA(EstadoAutorizacion destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public Set<EstadoAutorizacion> destinosPermitidos() {
        return TRANSICIONES.get(this);
    }

    /** Un estado final no admite mas cambios. */
    public boolean esFinal() {
        return TRANSICIONES.get(this).isEmpty();
    }
}
