package co.porteriaya.paquete.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de un paquete (§6): Recibido -> Entregado | Devuelto.
 *
 * Los dos cierres son finales. Entregar dos veces el mismo paquete es 409, que
 * es justo el error que hay que hacer visible en una porteria con prisa.
 */
public enum EstadoPaquete {

    RECIBIDO,
    ENTREGADO,
    DEVUELTO;

    private static final Map<EstadoPaquete, Set<EstadoPaquete>> TRANSICIONES;

    static {
        Map<EstadoPaquete, Set<EstadoPaquete>> mapa = new EnumMap<>(EstadoPaquete.class);
        mapa.put(RECIBIDO, EnumSet.of(ENTREGADO, DEVUELTO));
        mapa.put(ENTREGADO, EnumSet.noneOf(EstadoPaquete.class));
        mapa.put(DEVUELTO, EnumSet.noneOf(EstadoPaquete.class));
        TRANSICIONES = Collections.unmodifiableMap(mapa);
    }

    public boolean puedePasarA(EstadoPaquete destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public Set<EstadoPaquete> destinosPermitidos() {
        return TRANSICIONES.get(this);
    }

    public boolean esFinal() {
        return TRANSICIONES.get(this).isEmpty();
    }
}
