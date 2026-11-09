package co.porteriaya.rag.consulta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.common.SolicitudInvalidaException;
import co.porteriaya.config.RagProperties;
import co.porteriaya.evento.EventoPorteria;
import co.porteriaya.evento.EventoPublisher;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.rag.documento.Audiencia;
import co.porteriaya.rag.embedding.EmbeddingClient;
import co.porteriaya.rag.llm.LlmClient;
import co.porteriaya.rag.vector.ResultadoBusqueda;
import co.porteriaya.rag.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** El ciclo de consulta del asistente (§11). */
@Service
@RequiredArgsConstructor
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /** Misma cadena que el prompt le pide al modelo: una sola fuente. */
    public static final String SIN_RESPUESTA = PromptRag.SIN_RESPUESTA;
    public static final String NO_DISPONIBLE =
            "El asistente no esta disponible ahora mismo. La porteria funciona con normalidad.";

    private final EmbeddingClient embeddings;
    private final VectorStore vectores;
    private final LlmClient llm;
    private final ConsultaRagRepository consultas;
    private final Sanitizador sanitizador;
    private final LimitadorConsultas limitador;
    private final EventoPublisher eventos;
    private final RagProperties propiedades;
    private final Clock clock;

    /** Lo que ve el chat: texto, fuentes como chips y si no hubo respuesta (§13). */
    public record Respuesta(
            Long consultaId,
            String texto,
            List<String> fuentes,
            boolean sinRespuesta,
            boolean disponible,
            Double mejorSimilitud
    ) {
    }

    @Transactional
    public Respuesta preguntar(String preguntaCruda, Rol rol, String actor) {
        limitador.comprobar(actor);

        String pregunta = sanitizador.limpiar(preguntaCruda);
        if (pregunta.isBlank()) {
            throw new SolicitudInvalidaException("La pregunta esta vacia");
        }

        // nanoTime y no el Clock: esto mide tiempo transcurrido real, no tiempo
        // de dominio. Con el reloj simulado del perfil demo la latencia saldria
        // siempre 0, y la latencia es justo lo que hay que vigilar aqui.
        long inicio = System.nanoTime();

        try {
            Set<Audiencia> audiencias = Audiencia.visiblesPara(rol);
            List<ResultadoBusqueda> contexto = vectores.buscar(
                    embeddings.paraConsulta(pregunta), audiencias, propiedades.topK());

            double mejor = contexto.stream()
                    .mapToDouble(ResultadoBusqueda::similitud)
                    .max()
                    .orElse(0);

            if (contexto.isEmpty() || mejor < propiedades.umbral()) {
                return sinContextoSuficiente(pregunta, rol, actor, mejor, inicio);
            }

            String texto = llm.responder(PromptRag.SISTEMA, PromptRag.usuario(pregunta, contexto));
            List<String> fuentes = contexto.stream()
                    .map(ResultadoBusqueda::fuente)
                    .distinct()
                    .toList();

            ConsultaRag guardada = consultas.save(new ConsultaRag(
                    pregunta, rol, actor, texto, String.join(" | ", fuentes),
                    redondear(mejor), false, latenciaMs(inicio), clock.instant()));

            return new Respuesta(guardada.getId(), texto, fuentes, false, true, mejor);

        } catch (Exception e) {
            // §11: si Qdrant, Ollama o el LLM fallan, la porteria sigue
            // funcionando y el asistente dice que no esta disponible.
            log.error("El asistente fallo al responder: {}", e.getMessage(), e);
            return new Respuesta(null, NO_DISPONIBLE, List.of(), false, false, null);
        }
    }

    /**
     * Bajo el umbral no se llama al LLM (§11, paso 3).
     *
     * No es solo ahorro: llamarlo con contexto malo es la via mas directa a una
     * respuesta inventada, que en un reglamento es peor que no responder.
     */
    private Respuesta sinContextoSuficiente(String pregunta, Rol rol, String actor,
                                            double mejor, long inicio) {
        ConsultaRag guardada = consultas.save(new ConsultaRag(
                pregunta, rol, actor, SIN_RESPUESTA, null,
                redondear(mejor), true, latenciaMs(inicio), clock.instant()));

        eventos.publicar(TipoEvento.RAG_SIN_RESPUESTA, EventoPorteria.datos(
                "consultaId", guardada.getId(),
                "pregunta", pregunta,
                "rol", rol.name(),
                "mejorSimilitud", mejor));

        log.info("Sin contexto suficiente (mejor similitud {}), no se llama al LLM", mejor);
        return new Respuesta(guardada.getId(), SIN_RESPUESTA, List.of(), true, true, mejor);
    }

    @Transactional
    public void votar(Long consultaId, int valor) {
        if (valor != 1 && valor != -1) {
            throw new SolicitudInvalidaException("El feedback solo puede ser 1 o -1");
        }
        ConsultaRag consulta = consultas.findById(consultaId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la consulta", consultaId));
        consulta.votar((short) valor);
    }

    @Transactional(readOnly = true)
    public List<ConsultaRag> consultasSinRespuesta(int limite) {
        return consultas.findBySinRespuestaOrderByCreadoEnDesc(true, PageRequest.of(0, limite));
    }

    /** Estado del asistente, para que la UI pueda avisar antes de preguntar. */
    public String estado() {
        return vectores.disponible()
                ? "listo con " + llm.proveedor()
                : "no disponible: Qdrant no responde";
    }

    private Integer latenciaMs(long inicioNano) {
        return (int) ((System.nanoTime() - inicioNano) / 1_000_000);
    }

    /** La columna es NUMERIC(5,4): la similitud coseno cabe de sobra. */
    private BigDecimal redondear(double similitud) {
        return BigDecimal.valueOf(similitud).setScale(4, RoundingMode.HALF_UP);
    }

    /** Para el panel: las fuentes de una consulta, ya separadas. */
    public static List<String> separarFuentes(String fuentes) {
        if (fuentes == null || fuentes.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(fuentes.split("\\|"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
