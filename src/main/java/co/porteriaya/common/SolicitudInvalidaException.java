package co.porteriaya.common;

/**
 * 400: la peticion es sintacticamente valida pero rompe una regla de negocio,
 * p. ej. un servicio recurrente sin franja horaria. Lo que Bean Validation no
 * puede expresar por si solo.
 */
public class SolicitudInvalidaException extends PorteriaException {

    public SolicitudInvalidaException(String mensaje) {
        super("SOLICITUD_INVALIDA", mensaje);
    }
}
