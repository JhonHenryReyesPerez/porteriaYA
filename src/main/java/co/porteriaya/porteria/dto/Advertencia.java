package co.porteriaya.porteria.dto;

/**
 * Avisos que NO impiden el ingreso pero el vigilante debe ver (§6). Para
 * registrar un ingreso con advertencias hay que confirmarlas explicitamente.
 */
public enum Advertencia {

    /**
     * Paso 4: coincide el nombre con la lista de no permitidos, pero no el
     * documento, o el registro de la lista no tiene documento. El vigilante
     * confirma o descarta.
     */
    POSIBLE_NO_PERMITIDO,

    /** Paso 5: la persona ya figura como Dentro con la misma autorizacion. */
    YA_DENTRO
}
