package co.porteriaya.rag;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import co.porteriaya.rag.consulta.ConsultaRag;
import co.porteriaya.rag.consulta.RagService;
import co.porteriaya.rag.documento.Audiencia;
import co.porteriaya.rag.documento.Documento;
import co.porteriaya.rag.documento.EstadoDocumento;
import co.porteriaya.rag.documento.IngestaService;
import co.porteriaya.rag.documento.TipoDocumento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Asistente sobre el reglamento y los protocolos del conjunto (§8, §11). */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "Documentos del conjunto y asistente con citas")
public class RagController {

    private static final int LIMITE_SIN_RESPUESTA = 50;

    private final IngestaService ingesta;
    private final RagService rag;
    private final GuardiaRoles guardia;

    // ------------------------------------------------------------------ DTOs

    public record RespuestaDocumento(
            Long id, String titulo, TipoDocumento tipo, List<String> audiencias, String version,
            String nombreArchivo, EstadoDocumento estado, String motivoError, int chunks,
            Instant subidoEn, Instant indexadoEn) {

        static RespuestaDocumento de(Documento d) {
            return new RespuestaDocumento(d.getId(), d.getTitulo(), d.getTipo(),
                    Audiencia.nombres(d.audiencias()), d.getVersion(), d.getNombreArchivo(),
                    d.getEstado(), d.getMotivoError(), d.getChunks(), d.getSubidoEn(),
                    d.getIndexadoEn());
        }
    }

    public record Pregunta(
            @NotBlank(message = "La pregunta es obligatoria")
            @Size(max = 1000, message = "La pregunta no puede pasar de 1000 caracteres")
            String pregunta) {
    }

    public record Feedback(Long consultaId, int valor) {
    }

    public record ConsultaResumen(Long id, String pregunta, String rol, Instant creadoEn,
                                  Double mejorSimilitud) {

        static ConsultaResumen de(ConsultaRag c) {
            return new ConsultaResumen(c.getId(), c.getPregunta(), c.getRol().name(),
                    c.getCreadoEn(),
                    c.getMejorSimilitud() == null ? null : c.getMejorSimilitud().doubleValue());
        }
    }

    // ------------------------------------------------------------ documentos

    @PostMapping(value = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir un documento",
            description = """
                    Acepta PDF, Markdown o texto plano. La indexacion corre en segundo
                    plano: la respuesta vuelve con estado SUBIDO o INDEXANDO y hay que
                    consultar GET /api/rag/documentos para ver si termino.

                    Subir dos veces el mismo contenido no reindexa: se detecta por hash.
                    Mismo titulo con contenido distinto se trata como version nueva, y los
                    chunks anteriores se borran antes de meter los nuevos, para que el
                    asistente no cite a la vez la norma vieja y la nueva.

                    La audiencia es un conjunto: TODOS, o una lista como RESIDENTE,VIGILANTE.
                    Un residente nunca recupera chunks marcados solo para vigilante.
                    """)
    public RespuestaDocumento subir(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam String titulo,
            @RequestParam TipoDocumento tipo,
            @RequestParam(defaultValue = "TODOS") String audiencias,
            @RequestParam(defaultValue = "1") String version,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {

        guardia.exigirAdministrador(rol, pin);
        Set<Audiencia> conjunto = Audiencia.parsear(audiencias);
        return RespuestaDocumento.de(
                ingesta.subir(archivo, titulo, tipo, conjunto, version, actor));
    }

    @GetMapping("/documentos")
    @Operation(summary = "Listar documentos y su estado de indexacion")
    public List<RespuestaDocumento> listarDocumentos(@RequestHeader("X-Rol") Rol rol) {
        guardia.exigir(rol, Rol.ADMINISTRADOR, Rol.VIGILANTE);
        return ingesta.listar().stream().map(RespuestaDocumento::de).toList();
    }

    @DeleteMapping("/documentos/{id}")
    @Operation(summary = "Borrar un documento y sus chunks",
            description = """
                    Aqui si hay borrado fisico, al contrario que en las entidades
                    operativas: dejar un reglamento derogado indexado seria peor que
                    borrarlo, porque el asistente seguiria citandolo.
                    """)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarDocumento(
            @PathVariable Long id,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {
        guardia.exigirAdministrador(rol, pin);
        ingesta.borrar(id, actor);
    }

    @PostMapping("/reindexar")
    @Operation(summary = "Reconstruir la coleccion vectorial",
            description = """
                    Vuelve a indexar todos los documentos desde PostgreSQL, que es la
                    fuente de verdad. Sirve si se cambio de modelo de embeddings o si la
                    coleccion de Qdrant se perdio.
                    """)
    public String reindexar(
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {
        guardia.exigirAdministrador(rol, pin);
        return "Reindexado lanzado para %d documentos".formatted(ingesta.reindexar(actor));
    }

    // --------------------------------------------------------------- consulta

    @PostMapping("/preguntar")
    @Operation(summary = "Preguntar al asistente",
            description = """
                    El rol sale de la cabecera, nunca del cuerpo: es lo que decide que
                    documentos se pueden recuperar.

                    Si la mejor similitud queda bajo rag.umbral, NO se llama al LLM y la
                    respuesta es «no encuentro esto en los documentos». Llamarlo con
                    contexto malo es la via mas directa a una respuesta inventada, y en un
                    reglamento eso es peor que no responder.

                    Si Qdrant, Ollama o el LLM fallan, responde que el asistente no esta
                    disponible. La porteria sigue funcionando igual.

                    Limitado por actor y por minuto (429).
                    """)
    public RagService.Respuesta preguntar(@Valid @RequestBody Pregunta peticion,
                                          @RequestHeader("X-Rol") Rol rol,
                                          @RequestHeader("X-Actor") String actor) {
        return rag.preguntar(peticion.pregunta(), rol, actor);
    }

    @PostMapping("/feedback")
    @Operation(summary = "Votar una respuesta",
            description = "El pulgar arriba o abajo del chat. Valor 1 o -1.")
    public void feedback(@RequestBody Feedback peticion, @RequestHeader("X-Rol") Rol rol) {
        guardia.exigir(rol, Rol.RESIDENTE, Rol.VIGILANTE, Rol.ADMINISTRADOR);
        rag.votar(peticion.consultaId(), peticion.valor());
    }

    @GetMapping("/consultas")
    @Operation(summary = "Preguntas sin respuesta",
            description = """
                    Alimenta el panel de §13. Son las preguntas que quedaron bajo el
                    umbral, es decir, los huecos del reglamento indexado: la lista de lo
                    que convendria documentar.
                    """)
    public List<ConsultaResumen> consultas(
            @RequestParam(defaultValue = "true") boolean sinRespuesta,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {
        guardia.exigirAdministrador(rol, pin);
        return (sinRespuesta ? rag.consultasSinRespuesta(LIMITE_SIN_RESPUESTA) : List.<ConsultaRag>of())
                .stream().map(ConsultaResumen::de).toList();
    }

    @GetMapping("/estado")
    @Operation(summary = "Estado del asistente",
            description = "Para que la UI pueda avisar antes de que alguien pregunte.")
    public String estado() {
        return rag.estado();
    }
}
