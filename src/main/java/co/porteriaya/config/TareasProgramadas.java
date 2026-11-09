package co.porteriaya.config;

import co.porteriaya.autorizacion.service.AutorizacionService;
import co.porteriaya.paquete.service.PaqueteService;
import co.porteriaya.porteria.service.IngresoService;
import co.porteriaya.solicitud.service.SolicitudService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparador de las tareas periodicas de §9.
 *
 * Aqui no hay ni una regla de negocio: cada tarea vive en el service de su
 * dominio y esto solo las llama en orden. Si la logica estuviera aqui,
 * «paquetes» y «permanencia» acabarian siendo una clase de configuracion con
 * 300 lineas de dominio dentro.
 *
 * Todas las tareas son idempotentes: se apoyan en el estado y en banderas, no
 * en «cuando fue la ultima vez que corri». Eso es lo que permite que la pasada
 * se ejecute cada minuto, o varias veces seguidas en la demo, sin duplicar
 * avisos.
 *
 * Asume una sola instancia de la app: no hay bloqueo distribuido (§20).
 */
@Component
@RequiredArgsConstructor
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    private final AutorizacionService autorizaciones;
    private final SolicitudService solicitudes;
    private final IngresoService ingresos;
    private final PaqueteService paquetes;

    /** Qué hizo una pasada. El endpoint de demo lo devuelve para poder verlo. */
    public record ResumenPasada(
            int autorizacionesVencidas,
            int solicitudesSinRespuesta,
            int permanenciasAvisadas,
            int ingresosCerrados,
            int avisosDePaquetes
    ) {

        public int total() {
            return autorizacionesVencidas + solicitudesSinRespuesta
                    + permanenciasAvisadas + ingresosCerrados + avisosDePaquetes;
        }
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Bogota")
    public void cadaMinuto() {
        ejecutarPasada();
    }

    /**
     * Una pasada completa. Publico porque POST /api/demo/avanzar la invoca
     * justo despues de mover el reloj: el disparo de @Scheduled sigue el reloj
     * real, asi que sin esto la demo esperaria hasta un minuto para ver los
     * recordatorios (§9).
     *
     * Cada tarea va en su propia transaccion. Si una falla, las demas ya
     * confirmaron y la siguiente pasada reintentara la que fallo, porque son
     * idempotentes.
     */
    public ResumenPasada ejecutarPasada() {
        ResumenPasada resumen = new ResumenPasada(
                autorizaciones.vencerLasQueTerminaron(),
                solicitudes.vencerPendientes(),
                ingresos.avisarPermanenciasExcedidas(),
                ingresos.cerrarLosQueNoRegistraronSalida(),
                paquetes.procesarAvisos());

        if (resumen.total() > 0) {
            log.info("Pasada de tareas: {}", resumen);
        }
        return resumen;
    }
}
