package co.porteriaya.common;

/** 403: el rol no puede usar el endpoint, o el PIN de administrador no coincide (§7). */
public class AccesoDenegadoException extends PorteriaException {

    public AccesoDenegadoException(String mensaje) {
        super("ACCESO_DENEGADO", mensaje);
    }
}
