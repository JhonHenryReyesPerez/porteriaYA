package co.porteriaya.rag.vector;

/** Un chunk recuperado, con su similitud. */
public record ResultadoBusqueda(
        Long documentoId,
        String titulo,
        Integer pagina,
        int chunkIdx,
        String texto,
        double similitud
) {

    /** Cita corta para mostrar como chip en el chat (§13). */
    public String fuente() {
        return pagina == null ? titulo : "%s, p. %d".formatted(titulo, pagina);
    }
}
