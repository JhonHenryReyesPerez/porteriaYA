package co.porteriaya.solicitud.dto;

import java.time.Instant;

import co.porteriaya.solicitud.entity.EstadoSolicitud;
import co.porteriaya.solicitud.entity.SolicitudNoAnunciada;

/** Una solicitud no anunciada tal como la ve la portería. Sin el token. */
public record RespuestaSolicitud(
        Long id,
        String apartamento,
        String visitanteNombre,
        String visitanteDoc4,
        String motivo,
        EstadoSolicitud estado,
        String vigilante,
        Instant creadoEn,
        Instant venceEn,
        Instant resueltoEn,
        Long ingresoId
) {

    public static RespuestaSolicitud de(SolicitudNoAnunciada s) {
        return new RespuestaSolicitud(
                s.getId(),
                s.getResidente().identificador(),
                s.getVisitanteNombre(),
                s.getVisitanteDoc4(),
                s.getMotivo(),
                s.getEstado(),
                s.getVigilante(),
                s.getCreadoEn(),
                s.getVenceEn(),
                s.getResueltoEn(),
                s.getIngreso() == null ? null : s.getIngreso().getId());
    }
}
