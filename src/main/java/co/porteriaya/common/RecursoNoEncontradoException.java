package co.porteriaya.common;

/** 404: la entidad referida no existe. */
public class RecursoNoEncontradoException extends PorteriaException {

    public RecursoNoEncontradoException(String mensaje) {
        super("NO_ENCONTRADO", mensaje);
    }

    public static RecursoNoEncontradoException de(String entidad, Object id) {
        return new RecursoNoEncontradoException("No existe %s con id %s".formatted(entidad, id));
    }
}
