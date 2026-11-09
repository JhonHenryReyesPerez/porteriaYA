package co.porteriaya.rag.documento;

import java.util.List;

/**
 * Peticion interna de indexacion, publicada en el contexto de Spring.
 *
 * Existe para que la indexacion no arranque hasta que el documento este
 * confirmado en la base. Antes {@link IngestaService} llamaba directamente al
 * indexador desde dentro de su propia transaccion: el hilo asincrono salia
 * corriendo, hacia findById sobre una fila que todavia no estaba confirmada,
 * no la encontraba y se iba con un aviso en el log. El documento se quedaba en
 * SUBIDO para siempre y el asistente no podia citarlo.
 *
 * Es el mismo razonamiento que el de los eventos hacia n8n (§10): el trabajo
 * que depende de un hecho no se lanza hasta que el hecho es cierto.
 *
 * No es un evento de dominio de §10: no sale de la aplicacion y no tiene tipo
 * en {@code TipoEvento}. El que sale es DOCUMENTO_INDEXADO, cuando termina.
 */
public record DocumentoParaIndexar(Long documentoId, List<ExtractorTexto.BloqueTexto> bloques) {
}
