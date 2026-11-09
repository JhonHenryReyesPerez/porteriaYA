package co.porteriaya.porteria.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de un ingreso (§6) con su mapa de transiciones.
 *
 * Dentro -> Salio | Salida no registrada. Los dos finales no salen de ahi:
 * una salida ya cerrada no se reabre, y un cierre automatico de medianoche
 * tampoco se deshace desde la API.
 */
public enum EstadoIngreso {

    DENTRO,
    SALIO,
    SALIDA_NO_REGISTRADA;

    private static final Map<EstadoIngreso, Set<EstadoIngreso>> TRANSICIONES;

    static {
        Map<EstadoIngreso, Set<EstadoIngreso>> mapa = new EnumMap<>(EstadoIngreso.class);
        mapa.put(DENTRO, EnumSet.of(SALIO, SALIDA_NO_REGISTRADA));
        mapa.put(SALIO, EnumSet.noneOf(EstadoIngreso.class));
        mapa.put(SALIDA_NO_REGISTRADA, EnumSet.noneOf(EstadoIngreso.class));
        TRANSICIONES = Collections.unmodifiableMap(mapa);
    }

    public boolean puedePasarA(EstadoIngreso destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public Set<EstadoIngreso> destinosPermitidos() {
        return TRANSICIONES.get(this);
    }

    public boolean esFinal() {
        return TRANSICIONES.get(this).isEmpty();
    }
}
