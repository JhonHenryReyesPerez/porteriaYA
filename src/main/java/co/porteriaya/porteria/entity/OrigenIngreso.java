package co.porteriaya.porteria.entity;

/**
 * De donde viene un ingreso. La base lo refuerza con un CHECK: si el origen es
 * AUTORIZACION tiene que haber autorizacion_id, y si es NO_ANUNCIADA no puede
 * haberlo. Asi la regla no depende solo del service.
 */
public enum OrigenIngreso {

    /** Entro con un codigo de 6 digitos. */
    AUTORIZACION,

    /** Llego sin anunciarse y alguien aprobo su entrada. */
    NO_ANUNCIADA
}
