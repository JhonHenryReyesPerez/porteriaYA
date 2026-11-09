package co.porteriaya.common;

/**
 * 409: salto de estado no permitido por el mapa de transiciones (§6), o un
 * recurso agotado como los usos de una autorizacion.
 */
public class TransicionInvalidaException extends PorteriaException {

    public TransicionInvalidaException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    public static TransicionInvalidaException de(String entidad, Object desde, Object hasta) {
        return new TransicionInvalidaException(
                "TRANSICION_INVALIDA",
                "No se puede pasar %s de %s a %s".formatted(entidad, desde, hasta));
    }
}
