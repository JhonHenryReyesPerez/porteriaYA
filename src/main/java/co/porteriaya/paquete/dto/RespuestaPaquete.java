package co.porteriaya.paquete.dto;

import java.time.Instant;

import co.porteriaya.paquete.entity.EstadoPaquete;
import co.porteriaya.paquete.entity.Paquete;

/**
 * Un paquete tal como lo ve la porteria y el residente.
 *
 * El codigo de retiro NO viaja aqui: le llega al residente por correo y es lo
 * que acredita que puede recogerlo. Devolverlo en el listado de porteria lo
 * haria inutil.
 */
public record RespuestaPaquete(
        Long id,
        String apartamento,
        String descripcion,
        String remitente,
        EstadoPaquete estado,
        String vigilante,
        Instant recibidoEn,
        Instant entregadoEn,
        String entregadoA,
        boolean aviso24h,
        boolean aviso72h,
        boolean escalado
) {

    public static RespuestaPaquete de(Paquete p) {
        return new RespuestaPaquete(
                p.getId(),
                p.getResidente().identificador(),
                p.getDescripcion(),
                p.getRemitente(),
                p.getEstado(),
                p.getVigilante(),
                p.getRecibidoEn(),
                p.getEntregadoEn(),
                p.getEntregadoA(),
                p.isAviso24h(),
                p.isAviso72h(),
                p.isEscalado());
    }
}
