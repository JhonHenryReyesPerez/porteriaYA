package co.porteriaya.paquete.dto;

import java.time.Instant;

import co.porteriaya.paquete.entity.AutorizadoRetiro;

/**
 * Una persona autorizada a retirar.
 *
 * Lleva el doc4 porque es justo lo que el vigilante compara en la puerta: sin
 * el, acreditar se reduce a creerse un nombre.
 */
public record RespuestaAutorizadoRetiro(
        Long id,
        String apartamento,
        String nombre,
        String doc4,
        boolean activo,
        Instant creadoEn
) {

    public static RespuestaAutorizadoRetiro de(AutorizadoRetiro a) {
        return new RespuestaAutorizadoRetiro(a.getId(), a.getResidente().identificador(),
                a.getNombre(), a.getDoc4(), a.isActivo(), a.getCreadoEn());
    }
}
