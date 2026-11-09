package co.porteriaya.nopermitido.dto;

import java.time.Instant;

import co.porteriaya.nopermitido.entity.NoPermitido;

/**
 * Un registro de la lista. Lleva el doc4 porque es lo que el vigilante necesita
 * para distinguir a un homonimo de la persona vetada de verdad.
 */
public record RespuestaNoPermitido(
        Long id,
        String nombre,
        String doc4,
        String motivo,
        boolean activo,
        String registradoPor,
        Instant creadoEn
) {

    public static RespuestaNoPermitido de(NoPermitido n) {
        return new RespuestaNoPermitido(n.getId(), n.getNombre(), n.getDoc4(), n.getMotivo(),
                n.isActivo(), n.getRegistradoPor(), n.getCreadoEn());
    }
}
