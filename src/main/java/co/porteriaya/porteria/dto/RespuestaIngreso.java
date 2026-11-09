package co.porteriaya.porteria.dto;

import java.time.Instant;

import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.porteria.entity.OrigenIngreso;

/** Un ingreso tal como lo ve la portería. Sin el codigo de la autorizacion. */
public record RespuestaIngreso(
        Long id,
        OrigenIngreso origen,
        String apartamento,
        String visitanteNombre,
        String visitanteDoc4,
        EstadoIngreso estado,
        String vigilante,
        int duracionEsperadaMin,
        boolean permanenciaAvisada,
        Instant ingresoEn,
        Instant salidaEn
) {

    public static RespuestaIngreso de(Ingreso i) {
        return new RespuestaIngreso(
                i.getId(),
                i.getOrigen(),
                i.getResidente().identificador(),
                i.getVisitanteNombre(),
                i.getVisitanteDoc4(),
                i.getEstado(),
                i.getVigilante(),
                i.getDuracionEsperadaMin(),
                i.isPermanenciaAvisada(),
                i.getIngresoEn(),
                i.getSalidaEn());
    }
}
