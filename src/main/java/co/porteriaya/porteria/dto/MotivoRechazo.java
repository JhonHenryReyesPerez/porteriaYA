package co.porteriaya.porteria.dto;

/**
 * Motivos de rechazo de la validacion (§6). El orden de las constantes es el
 * orden exacto de los pasos: el motivo devuelto depende de el, asi que no se
 * reordena sin pedirlo.
 */
public enum MotivoRechazo {

    /** Paso 1: el codigo no corresponde a ninguna autorizacion. */
    CODIGO_NO_EXISTE,
    /** Paso 1: existe pero fue cancelada. */
    AUTORIZACION_CANCELADA,
    /** Paso 2: fuera de la ventana de fechas. */
    FUERA_DE_VENTANA,
    /** Paso 2: recurrente, pero hoy no es dia valido o la hora cae fuera de la franja. */
    FUERA_DE_FRANJA,
    /** Paso 3: no quedan usos (filas afectadas = 0 en el UPDATE atomico). */
    USOS_AGOTADOS,
    /** Paso 4: coincidencia exacta con la lista de no permitidos. */
    PERSONA_NO_PERMITIDA
}
