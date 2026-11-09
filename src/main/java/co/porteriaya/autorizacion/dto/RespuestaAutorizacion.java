package co.porteriaya.autorizacion.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.entity.Recurrencia;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;

/**
 * Lo que el residente ve de su autorizacion. Aqui si va el codigo completo:
 * es lo que tiene que compartir con su visitante y lo que la UI convierte en QR.
 * En logs, en cambio, el codigo nunca aparece (§5).
 */
public record RespuestaAutorizacion(
        Long id,
        TipoAutorizacion tipo,
        String codigo,
        String apartamento,
        String visitanteNombre,
        String visitanteDoc4,
        EstadoAutorizacion estado,
        Instant validaDesde,
        Instant validaHasta,
        List<Integer> diasSemana,
        LocalTime horaDesde,
        LocalTime horaHasta,
        Integer usosMax,
        int usosActuales,
        Integer usosRestantes,
        int duracionEsperadaMin,
        Instant creadoEn
) {

    public static RespuestaAutorizacion de(Autorizacion a) {
        List<Integer> dias = a.recurrencia()
                .map(Recurrencia::dias)
                .map(conjunto -> conjunto.stream().map(d -> d.getValue()).sorted().toList())
                .orElse(null);

        Integer restantes = a.usosIlimitados() ? null : a.getUsosMax() - a.getUsosActuales();

        return new RespuestaAutorizacion(
                a.getId(),
                a.getTipo(),
                a.getCodigo(),
                a.getResidente().identificador(),
                a.getVisitanteNombre(),
                a.getVisitanteDoc4(),
                a.getEstado(),
                a.getValidaDesde(),
                a.getValidaHasta(),
                dias,
                a.getHoraDesde(),
                a.getHoraHasta(),
                a.getUsosMax(),
                a.getUsosActuales(),
                restantes,
                a.getDuracionEsperadaMin(),
                a.getCreadoEn());
    }
}
