package co.porteriaya.rag.llm;

/**
 * El modelo que redacta la respuesta (§11).
 *
 * Dos parametros y nada mas: el prompt de sistema, que fija los guardrails, y
 * el del usuario, que lleva el contexto recuperado y la pregunta. La
 * temperatura y el limite de tokens son del proveedor, no del llamador, para
 * que nadie pueda subir la temperatura desde el dominio.
 */
public interface LlmClient {

    String responder(String promptSistema, String promptUsuario);

    /** Nombre del proveedor, para logs y para el panel. */
    String proveedor();
}
