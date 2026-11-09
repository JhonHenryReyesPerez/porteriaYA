package co.porteriaya.bitacora.dto;

import java.time.Instant;

import co.porteriaya.bitacora.entity.Bitacora;
import co.porteriaya.common.Rol;

/** Una entrada de auditoria tal como la ve el panel de administracion. */
public record RespuestaBitacora(
        Long id,
        Instant fecha,
        String actor,
        Rol rol,
        String entidad,
        Long entidadId,
        String accion,
        String detalle
) {

    public static RespuestaBitacora de(Bitacora b) {
        return new RespuestaBitacora(b.getId(), b.getFecha(), b.getActor(), b.getRol(),
                b.getEntidad(), b.getEntidadId(), b.getAccion(), b.getDetalle());
    }
}
