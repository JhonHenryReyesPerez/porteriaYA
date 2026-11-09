package co.porteriaya.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import co.porteriaya.rag.documento.Audiencia;
import co.porteriaya.rag.documento.DocumentoRepository;
import co.porteriaya.rag.documento.IngestaService;
import co.porteriaya.rag.documento.TipoDocumento;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Carga los documentos de ejemplo del perfil demo (§12).
 *
 * Es idempotente por hash: relanzar la app no duplica nada.
 *
 * Si Ollama o Qdrant no estan arriba, la subida se guarda igual y la
 * indexacion queda en ERROR, visible en el panel de documentos. Eso es
 * deliberado: Ollama va en el perfil `rag` de Compose y arrancar la demo no
 * deberia exigir tenerlo levantado.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class SembradorDocumentos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradorDocumentos.class);

    private final IngestaService ingesta;
    private final DocumentoRepository documentos;

    @Override
    public void run(ApplicationArguments args) {
        if (!documentos.findAll().isEmpty()) {
            log.info("Documentos de demo ya cargados");
            return;
        }

        // El reglamento lo ven los tres roles; el protocolo es solo para
        // vigilancia y administracion, y ese filtro se prueba en la demo:
        // un residente preguntando por protocolos no recupera nada.
        sembrar("Reglamento de Convivencia", TipoDocumento.REGLAMENTO,
                Set.of(Audiencia.TODOS), "reglamento-convivencia.md");

        sembrar("Protocolo de Porteria", TipoDocumento.PROTOCOLO,
                Set.of(Audiencia.VIGILANTE, Audiencia.ADMINISTRADOR), "protocolo-porteria.md");
    }

    private void sembrar(String titulo, TipoDocumento tipo, Set<Audiencia> audiencias,
                         String archivo) {
        try {
            String contenido = new String(
                    new ClassPathResource("rag/seed/" + archivo).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

            ingesta.subirTexto(titulo, tipo, audiencias, "1", archivo, contenido, "sembrador-demo");
            log.info("Documento de demo cargado: {}", titulo);

        } catch (IOException e) {
            log.warn("No se pudo leer la semilla {}: {}", archivo, e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo sembrar {}: {}", titulo, e.getMessage());
        }
    }
}
