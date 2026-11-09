package co.porteriaya.dashboard;

import java.util.List;

/**
 * Lo que alimenta el panel de administracion (§13).
 *
 * Son datos agregados: cuentas y totales, nunca nombres de visitantes ni
 * documentos. Un panel de metricas no necesita saber quien, solo cuantos (§6).
 */
public record EstadisticasDashboard(
        Tarjetas tarjetas,
        /** 24 posiciones, de la hora 0 a la 23 en America/Bogota. */
        List<Integer> visitasPorHoraHoy,
        PaquetesPorAntiguedad paquetesPorAntiguedad
) {

    /** Las cifras grandes de la cabecera del panel. */
    public record Tarjetas(
            long personasDentro,
            long ingresosHoy,
            long autorizacionesActivas,
            long solicitudesPendientes,
            long paquetesPendientes,
            long noPermitidosActivos,
            long salidasNoRegistradas
    ) {
    }

    /**
     * Los tramos coinciden con los hitos de aviso de §6, a proposito: asi el
     * panel y los recordatorios cuentan la misma historia y un paquete en el
     * tramo «mas de 7 dias» es exactamente uno que ya se escalo.
     */
    public record PaquetesPorAntiguedad(
            long menosDe24Horas,
            long entre24Y72Horas,
            long entre72HorasY7Dias,
            long masDe7Dias
    ) {
    }
}
