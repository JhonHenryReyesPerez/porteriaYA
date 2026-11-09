package co.porteriaya.common;

/**
 * Base de las excepciones de dominio. Cada una lleva un codigo estable que la
 * UI puede interpretar, ademas del mensaje en espanol para la persona.
 */
public abstract class PorteriaException extends RuntimeException {

    private final String codigo;

    protected PorteriaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
