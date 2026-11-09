package co.porteriaya.rag.documento;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import co.porteriaya.config.AsyncConfig;
import co.porteriaya.evento.EventoPorteria;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.rag.embedding.EmbeddingClient;
import co.porteriaya.rag.vector.ChunkVectorizado;
import co.porteriaya.rag.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indexa un documento en segundo plano (§11, paso 6 de la ingesta).
 *
 * Va en su propio componente y no como metodo privado del service porque
 * @Async solo funciona a traves del proxy de Spring: llamarse a si mismo no
 * crea ninguna tarea asincrona, lo haria todo sincrono sin avisar.
 *
 * Si falla, el documento queda en ERROR con el motivo y la aplicacion sigue.
 * Indexar es una tarea de administracion, no parte del servicio de porteria.
 */
@Component
@RequiredArgsConstructor
public class IndexadorDocumentos {

    private static final Logger log = LoggerFactory.getLogger(IndexadorDocumentos.class);

    private final DocumentoRepository documentos;
    private final Chunker chunker;
    private final EmbeddingClient embeddings;
    private final VectorStore vectores;
    private final EventoPublisher eventos;
    private final Clock clock;

    @Async(AsyncConfig.EJECUTOR_EVENTOS)
    @Transactional
    public void indexar(Long documentoId, List<ExtractorTexto.BloqueTexto> bloques) {
        Documento documento = documentos.findById(documentoId).orElse(null);
        if (documento == null) {
            log.warn("El documento {} desaparecio antes de indexarse", documentoId);
            return;
        }

        try {
            documento.empezarIndexado();
            vectores.asegurarColeccion();

            // Version nueva: fuera los chunks anteriores antes de meter los nuevos.
            // Si no, el asistente citaria a la vez la norma vieja y la nueva.
            vectores.borrarPorDocumento(documentoId);

            List<ChunkVectorizado> aGuardar = new ArrayList<>();
            int indice = 0;

            for (ExtractorTexto.BloqueTexto bloque : bloques) {
                for (String trozo : chunker.partir(bloque.texto())) {
                    aGuardar.add(new ChunkVectorizado(
                            documentoId,
                            indice++,
                            documento.getTitulo(),
                            bloque.pagina(),
                            Audiencia.nombres(documento.audiencias()),
                            documento.getVersion(),
                            documento.getSubidoEn().toString(),
                            trozo,
                            embeddings.paraDocumento(trozo)));
                }
            }

            vectores.guardar(aGuardar);
            documento.terminarIndexado(aGuardar.size(), clock.instant());

            log.info("Documento {} indexado con {} chunks", documentoId, aGuardar.size());

            eventos.publicar(TipoEvento.DOCUMENTO_INDEXADO, EventoPorteria.datos(
                    "documentoId", documentoId,
                    "titulo", documento.getTitulo(),
                    "tipo", documento.getTipo().name(),
                    "chunks", aGuardar.size(),
                    "version", documento.getVersion()));

        } catch (Exception e) {
            log.error("Fallo al indexar el documento {}", documentoId, e);
            documento.fallarIndexado(e.getMessage());
        }
    }
}
