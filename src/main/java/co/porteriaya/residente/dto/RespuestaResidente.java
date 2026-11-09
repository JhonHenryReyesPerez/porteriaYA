package co.porteriaya.residente.dto;

import co.porteriaya.residente.entity.Residente;

/**
 * Lo que alimenta el selector de torre y apartamento (§7).
 *
 * No lleva correo ni telefono: es una lista publica dentro de la aplicacion y
 * esos son datos de contacto de los vecinos.
 */
public record RespuestaResidente(
        Long id,
        String torre,
        String apartamento,
        String identificador,
        String nombre
) {

    public static RespuestaResidente de(Residente r) {
        return new RespuestaResidente(r.getId(), r.getTorre(), r.getApartamento(),
                r.identificador(), r.getNombre());
    }
}
