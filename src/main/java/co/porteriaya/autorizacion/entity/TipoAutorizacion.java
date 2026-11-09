package co.porteriaya.autorizacion.entity;

/** Tipos de §6. Cada uno define si admite recurrencia y si los usos son ilimitados. */
public enum TipoAutorizacion {

    /** Visita: 1 o varios usos, sin franja horaria. */
    VISITA(false, false),
    /** Domiciliario o proveedor: pocas horas, un solo uso. */
    DOMICILIARIO(false, false),
    /** Servicio recurrente: dias y horas fijos, usos ilimitados dentro de la franja. */
    SERVICIO_RECURRENTE(true, true),
    /** Mudanza o trabajo: varios dias, ilimitados dentro de la franja. */
    MUDANZA(true, true);

    private final boolean admiteRecurrencia;
    private final boolean usosIlimitados;

    TipoAutorizacion(boolean admiteRecurrencia, boolean usosIlimitados) {
        this.admiteRecurrencia = admiteRecurrencia;
        this.usosIlimitados = usosIlimitados;
    }

    /** Si es true, dias de la semana y franja horaria son obligatorios. */
    public boolean admiteRecurrencia() {
        return admiteRecurrencia;
    }

    /** Si es true, usosMax va nulo en la base: ilimitado dentro de la franja (§6). */
    public boolean usosIlimitados() {
        return usosIlimitados;
    }
}
