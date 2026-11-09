package co.porteriaya;

import java.util.List;
import java.util.Map;

import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.config.PorteriaProperties;

/**
 * Propiedades reales (no un mock) para las pruebas unitarias.
 *
 * Es un record, asi que construirlo cuesta lo mismo que simularlo y ademas deja
 * los valores a la vista: cuando un test habla de «72 horas», se ve de donde
 * sale ese 72.
 */
public final class PropiedadesDePrueba {

    private PropiedadesDePrueba() {
    }

    /** Los mismos valores que application.yml. */
    public static PorteriaProperties porteria() {
        return new PorteriaProperties(
                "http://localhost:8080",
                new PorteriaProperties.Admin("pin-de-prueba"),
                new PorteriaProperties.Cors(List.of("http://localhost:8080")),
                new PorteriaProperties.Autorizaciones(
                        Map.of(
                                TipoAutorizacion.VISITA, 180,
                                TipoAutorizacion.DOMICILIARIO, 30,
                                TipoAutorizacion.SERVICIO_RECURRENTE, 300,
                                TipoAutorizacion.MUDANZA, 480),
                        10),
                new PorteriaProperties.Aprobacion(3),
                new PorteriaProperties.Permanencia(30),
                new PorteriaProperties.Paquete(24, 72, 7),
                new PorteriaProperties.N8n("http://127.0.0.1:1/webhook", "secreto", 1, 1));
    }
}
