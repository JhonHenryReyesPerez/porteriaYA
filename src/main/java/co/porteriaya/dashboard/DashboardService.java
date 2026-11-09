package co.porteriaya.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.config.ClockConfig;
import co.porteriaya.nopermitido.repository.NoPermitidoRepository;
import co.porteriaya.paquete.entity.EstadoPaquete;
import co.porteriaya.paquete.entity.Paquete;
import co.porteriaya.paquete.repository.PaqueteRepository;
import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.porteria.repository.IngresoRepository;
import co.porteriaya.solicitud.entity.EstadoSolicitud;
import co.porteriaya.solicitud.repository.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Metricas agregadas del panel de administracion (§8, §13). */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int HORAS_DEL_DIA = 24;

    private final IngresoRepository ingresos;
    private final AutorizacionRepository autorizaciones;
    private final SolicitudRepository solicitudes;
    private final PaqueteRepository paquetes;
    private final NoPermitidoRepository noPermitidos;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EstadisticasDashboard calcular() {
        Instant ahora = clock.instant();
        Instant inicioDeHoy = inicioDelDiaLocal(ahora);

        List<Ingreso> ingresosDeHoy = ingresos.findByIngresoEnGreaterThanEqual(inicioDeHoy);

        return new EstadisticasDashboard(
                new EstadisticasDashboard.Tarjetas(
                        ingresos.countByEstado(EstadoIngreso.DENTRO),
                        ingresosDeHoy.size(),
                        autorizaciones.countByEstado(EstadoAutorizacion.ACTIVA),
                        solicitudes.countByEstado(EstadoSolicitud.PENDIENTE),
                        paquetes.countByEstado(EstadoPaquete.RECIBIDO),
                        noPermitidos.countByActivoTrue(),
                        ingresos.countByEstado(EstadoIngreso.SALIDA_NO_REGISTRADA)),
                visitasPorHora(ingresosDeHoy),
                paquetesPorAntiguedad(ahora));
    }

    /**
     * Medianoche local, no UTC. En America/Bogota son las 05:00 UTC, asi que
     * calcularlo en UTC movería «hoy» cinco horas y el grafico mostraria
     * visitas de ayer por la noche como si fueran de hoy.
     */
    private Instant inicioDelDiaLocal(Instant ahora) {
        return ahora.atZone(ClockConfig.ZONA).toLocalDate()
                .atStartOfDay(ClockConfig.ZONA).toInstant();
    }

    /**
     * Agrupa en Java y no en SQL a proposito: son los ingresos de un dia, un
     * puñado de filas, y extraer la hora en la zona correcta desde SQL ataria
     * la consulta a funciones propias de PostgreSQL.
     */
    private List<Integer> visitasPorHora(List<Ingreso> ingresosDeHoy) {
        List<Integer> porHora = new ArrayList<>(HORAS_DEL_DIA);
        for (int i = 0; i < HORAS_DEL_DIA; i++) {
            porHora.add(0);
        }
        for (Ingreso ingreso : ingresosDeHoy) {
            ZonedDateTime local = ingreso.getIngresoEn().atZone(ClockConfig.ZONA);
            int hora = local.getHour();
            porHora.set(hora, porHora.get(hora) + 1);
        }
        return List.copyOf(porHora);
    }

    private EstadisticasDashboard.PaquetesPorAntiguedad paquetesPorAntiguedad(Instant ahora) {
        long menos24 = 0;
        long entre24y72 = 0;
        long entre72y7dias = 0;
        long mas7dias = 0;

        for (Paquete paquete : paquetes.sinRetirar()) {
            long horas = Duration.between(paquete.getRecibidoEn(), ahora).toHours();
            if (horas < 24) {
                menos24++;
            } else if (horas < 72) {
                entre24y72++;
            } else if (horas < 24 * 7) {
                entre72y7dias++;
            } else {
                mas7dias++;
            }
        }

        return new EstadisticasDashboard.PaquetesPorAntiguedad(
                menos24, entre24y72, entre72y7dias, mas7dias);
    }
}
