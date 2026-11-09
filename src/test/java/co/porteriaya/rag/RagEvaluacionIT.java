package co.porteriaya.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import co.porteriaya.IntegracionBase;
import co.porteriaya.common.Rol;
import co.porteriaya.config.RagProperties;
import co.porteriaya.rag.documento.Audiencia;
import co.porteriaya.rag.documento.Documento;
import co.porteriaya.rag.documento.DocumentoRepository;
import co.porteriaya.rag.documento.EstadoDocumento;
import co.porteriaya.rag.documento.IngestaService;
import co.porteriaya.rag.documento.TipoDocumento;
import co.porteriaya.rag.embedding.EmbeddingClient;
import co.porteriaya.rag.vector.ResultadoBusqueda;
import co.porteriaya.rag.vector.VectorStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Evaluacion del asistente: recall@5 sobre 20 preguntas (§11, §15).
 *
 * Esto no es una prueba de regresion al uso, es el INSTRUMENTO DE CALIBRACION
 * que §11 exige: «ajustar tamano de chunk y umbral con ese numero, no a ojo».
 * Mide dos cosas que tiran en direcciones opuestas:
 *
 * 1. Recall@5 — cuantas de las 20 preguntas recuperan su documento entre los
 *    cinco chunks. Si baja, el chunking o el modelo van mal.
 * 2. Cuantas preguntas fuera de alcance quedan bajo el umbral. Si rag.umbral
 *    fuera 0, el recall seria perfecto y el asistente inventaria normas, que
 *    en un reglamento es peor que no responder.
 *
 * Mirar solo la primera cifra lleva a bajar el umbral hasta que todo "funciona".
 *
 * NECESITA Ollama y Qdrant de verdad: son justo las piezas que se estan
 * midiendo, asi que simularlas no mediria nada. Si alguna no responde, la
 * clase entera se SALTA en vez de fallar, para que `./mvnw verify` siga en
 * verde sin tener Ollama cargado en RAM. Para correrla:
 *
 *   docker compose up -d qdrant
 *   docker compose up -d ollama
 *   docker compose exec ollama ollama pull nomic-embed-text
 *   ./mvnw verify -Dit.test=RagEvaluacionIT
 *
 * Usa su propia coleccion de Qdrant y la borra al empezar, para no mezclarse
 * con los documentos de desarrollo ni arrastrar restos de una corrida anterior.
 */
@TestPropertySource(properties = "rag.vector.coleccion-reglamento=" + RagEvaluacionIT.COLECCION)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Evaluacion del RAG: recall@5 y comportamiento del umbral")
class RagEvaluacionIT extends IntegracionBase {

    static final String COLECCION = "evaluacion-pruebas";

    /**
     * Minimo exigible de recall@5. No es 20/20 a proposito: las preguntas estan
     * escritas como las haria una persona, y exigir pleno invitaria a
     * reescribirlas hasta que pasen, que es justo lo que arruina la metrica.
     * Si sube de forma estable, se sube este numero.
     */
    private static final double RECALL_MINIMO = 0.70;

    private static final Logger log = LoggerFactory.getLogger(RagEvaluacionIT.class);

    @Autowired
    private IngestaService ingesta;
    @Autowired
    private DocumentoRepository documentos;
    @Autowired
    private EmbeddingClient embeddings;
    @Autowired
    private VectorStore vectores;
    @Autowired
    private RagProperties propiedades;

    private record Caso(String pregunta, String documentoEsperado) {
    }

    // ------------------------------------------------------------- montaje

    @BeforeAll
    void indexarLosDocumentosDeEjemplo() {
        assumeTrue(respondeQdrant(), "Qdrant no responde: se salta la evaluacion del RAG");
        assumeTrue(respondeOllama(), "Ollama no responde: se salta la evaluacion del RAG");

        borrarColeccionDePruebas();
        vectores.asegurarColeccion();

        // Los mismos dos documentos que carga el perfil demo, por el mismo
        // camino de ingesta: si la evaluacion usara otro texto u otro
        // chunking, no mediria lo que la demo ensena.
        sembrar("Reglamento de Convivencia", TipoDocumento.REGLAMENTO,
                Set.of(Audiencia.TODOS), "reglamento-convivencia.md");
        sembrar("Protocolo de Porteria", TipoDocumento.PROTOCOLO,
                Set.of(Audiencia.VIGILANTE, Audiencia.ADMINISTRADOR), "protocolo-porteria.md");

        esperarIndexacion();
    }

    // ------------------------------------------------------------- pruebas

    @Test
    @DisplayName("el documento correcto aparece entre los 5 chunks recuperados")
    void recallArribaDelMinimo() {
        List<Caso> casos = leerCasos("rag/evaluacion.csv");
        assertThat(casos)
                .as("el conjunto de evaluacion de §11 son unas 20 preguntas")
                .hasSizeGreaterThanOrEqualTo(20);

        int aciertos = 0;
        int bajoUmbral = 0;
        List<String> fallos = new ArrayList<>();

        for (Caso caso : casos) {
            List<ResultadoBusqueda> top = buscar(caso.pregunta());

            boolean acierta = top.stream()
                    .anyMatch(r -> caso.documentoEsperado().equals(r.titulo()));
            double mejor = mejorSimilitud(top);

            if (acierta) {
                aciertos++;
                // Recuperar el documento correcto no sirve de nada si la
                // similitud queda bajo el umbral: el asistente respondera
                // «no encuentro esto» con la respuesta delante.
                if (mejor < propiedades.umbral()) {
                    bajoUmbral++;
                    fallos.add("  bajo umbral (%.3f) pero correcto: %s"
                            .formatted(mejor, caso.pregunta()));
                }
            } else {
                String recuperados = top.isEmpty() ? "nada"
                        : top.stream().map(ResultadoBusqueda::fuente).distinct().toList().toString();
                fallos.add("  FALLO esperaba «%s», recupero %s (mejor %.3f): %s"
                        .formatted(caso.documentoEsperado(), recuperados, mejor, caso.pregunta()));
            }
        }

        double recall = (double) aciertos / casos.size();
        log.info("""

                        ===== Evaluacion del RAG =====
                        recall@5          {}/{} = {}
                        correctos pero bajo el umbral ({}): {}
                        {}""",
                aciertos, casos.size(), "%.0f%%".formatted(recall * 100),
                propiedades.umbral(), bajoUmbral,
                fallos.isEmpty() ? "  (sin incidencias)" : String.join("\n", fallos));

        assertThat(recall)
                .as("recall@5 con chunk de %d caracteres y solape %d",
                        propiedades.chunk().caracteres(), propiedades.chunk().solape())
                .isGreaterThanOrEqualTo(RECALL_MINIMO);

        assertThat(bajoUmbral)
                .as("preguntas cuyo documento se recupera pero no pasa rag.umbral=%s; "
                                + "si esto no es 0, el umbral esta demasiado alto",
                        propiedades.umbral())
                .isZero();
    }

    @Test
    @DisplayName("lo que no esta en los documentos queda bajo el umbral")
    void loQueNoEstaNoPasaElUmbral() {
        List<String> preguntas = leerCasos("rag/evaluacion-fuera-de-alcance.csv").stream()
                .map(Caso::pregunta)
                .toList();

        List<String> colados = new ArrayList<>();
        for (String pregunta : preguntas) {
            double mejor = mejorSimilitud(buscar(pregunta));
            if (mejor >= propiedades.umbral()) {
                colados.add("  %.3f >= %s: %s".formatted(mejor, propiedades.umbral(), pregunta));
            }
        }

        assertThat(colados)
                .as("estas preguntas no las responden los documentos, pero pasarian el umbral "
                        + "y llegarian al LLM con contexto que no sirve:\n%s",
                        String.join("\n", colados))
                .isEmpty();
    }

    // ----------------------------------------------------------- utilidades

    private List<ResultadoBusqueda> buscar(String pregunta) {
        // Como administrador: recupera las dos audiencias, que es lo que hace
        // falta para medir los dos documentos con el mismo conjunto.
        return vectores.buscar(
                embeddings.paraConsulta(pregunta),
                Audiencia.visiblesPara(Rol.ADMINISTRADOR),
                propiedades.topK());
    }

    private double mejorSimilitud(List<ResultadoBusqueda> top) {
        return top.stream().mapToDouble(ResultadoBusqueda::similitud).max().orElse(0);
    }

    private void sembrar(String titulo, TipoDocumento tipo, Set<Audiencia> audiencias,
                         String archivo) {
        ingesta.subirTexto(titulo, tipo, audiencias, "1", archivo,
                leerRecurso("rag/seed/" + archivo), "evaluacion");
    }

    /** La indexacion corre en segundo plano (§11), asi que hay que esperarla. */
    private void esperarIndexacion() {
        Duration limite = Duration.ofSeconds(120);
        long hasta = System.nanoTime() + limite.toNanos();

        while (System.nanoTime() < hasta) {
            List<Documento> todos = documentos.findAll();
            boolean terminados = todos.size() == 2 && todos.stream()
                    .allMatch(d -> d.getEstado() == EstadoDocumento.INDEXADO
                            || d.getEstado() == EstadoDocumento.ERROR);
            if (terminados) {
                List<String> errores = todos.stream()
                        .filter(d -> d.getEstado() == EstadoDocumento.ERROR)
                        .map(d -> d.getTitulo() + ": " + d.getMotivoError())
                        .toList();
                assertThat(errores).as("documentos que no se pudieron indexar").isEmpty();
                log.info("Indexados {} documentos, {} chunks en total", todos.size(),
                        todos.stream().mapToInt(Documento::getChunks).sum());
                return;
            }
            dormir();
        }
        throw new AssertionError(
                "Los documentos no acabaron de indexarse en " + limite.toSeconds() + " s");
    }

    private void dormir() {
        try {
            // Unico sitio del proyecto con una espera real: aqui no se simula
            // el tiempo, se espera a que Ollama termine de calcular vectores.
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Espera interrumpida", e);
        }
    }

    /**
     * Restos de una corrida anterior falsearian el recall, asi que la
     * coleccion se borra entera. Se hace por HTTP y no por VectorStore porque
     * borrar una coleccion no es una operacion que la aplicacion necesite: no
     * va a aparecer en la interfaz solo para que esta prueba la use.
     */
    private void borrarColeccionDePruebas() {
        try {
            qdrant().delete().uri("/collections/{c}", COLECCION).retrieve().toBodilessEntity();
            log.info("Coleccion de pruebas {} borrada", COLECCION);
        } catch (Exception noExistia) {
            log.debug("La coleccion {} no existia", COLECCION);
        }
    }

    private boolean respondeQdrant() {
        try {
            qdrant().get().uri("/collections").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean respondeOllama() {
        try {
            int dimension = embeddings.paraConsulta("prueba de disponibilidad").size();
            assertThat(dimension)
                    .as("el modelo de embeddings tiene que dar la dimension de §11")
                    .isEqualTo(propiedades.embedding().dimension());
            return true;
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    private RestClient qdrant() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(propiedades.vector().url())
                .requestFactory(fabrica)
                .build();
    }

    private List<Caso> leerCasos(String recurso) {
        List<Caso> casos = new ArrayList<>();
        for (String linea : leerRecurso(recurso).lines().toList()) {
            String limpia = linea.strip();
            if (limpia.isEmpty() || limpia.startsWith("#")) {
                continue;
            }
            String[] partes = limpia.split(";", 2);
            casos.add(new Caso(partes[0].strip(),
                    partes.length > 1 ? partes[1].strip() : null));
        }
        return casos;
    }

    private String leerRecurso(String ruta) {
        try {
            return new String(new ClassPathResource(ruta).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta, e);
        }
    }
}
