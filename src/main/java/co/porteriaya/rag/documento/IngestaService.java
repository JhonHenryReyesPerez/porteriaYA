package co.porteriaya.rag.documento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.common.SolicitudInvalidaException;
import co.porteriaya.config.RagProperties;
import co.porteriaya.rag.vector.VectorStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Ingesta de documentos (§11). Solo administracion, con PIN (§7). */
@Service
@RequiredArgsConstructor
public class IngestaService {

    private static final Logger log = LoggerFactory.getLogger(IngestaService.class);
    private static final String ENTIDAD = "DOCUMENTO";

    private final DocumentoRepository documentos;
    private final ExtractorTexto extractor;
    private final ApplicationEventPublisher publicador;
    private final VectorStore vectores;
    private final BitacoraService bitacora;
    private final RagProperties propiedades;
    private final ObjectMapper json;
    private final Clock clock;

    /**
     * Sube un documento y lanza su indexacion en segundo plano.
     *
     * Si ya existe uno con el mismo hash, no se reindexa: subir dos veces el
     * mismo archivo no deberia costar una tanda de embeddings (§11, paso 2).
     */
    @Transactional
    public Documento subir(MultipartFile archivo, String titulo, TipoDocumento tipo,
                           Set<Audiencia> audiencias, String version, String actor) {
        validarTamano(archivo);

        List<ExtractorTexto.BloqueTexto> bloques = extractor.extraer(archivo);
        String contenido = bloques.stream()
                .map(ExtractorTexto.BloqueTexto::texto)
                .reduce("", (a, b) -> a + "\n" + b);

        if (contenido.isBlank()) {
            throw new SolicitudInvalidaException(
                    "No se pudo extraer texto del archivo. Si es un PDF escaneado, hace falta OCR");
        }

        String hash = sha256(contenido);
        Optional<Documento> mismoContenido = documentos.findByHashContenido(hash);
        if (mismoContenido.isPresent()) {
            log.info("El documento ya estaba subido con el mismo contenido: {}",
                    mismoContenido.get().getId());
            return mismoContenido.get();
        }

        Documento documento = documentos.findByTituloIgnoreCase(titulo)
                .map(existente -> {
                    // Mismo titulo, contenido distinto: es una version nueva
                    existente.nuevaVersion(version, hash, archivo.getOriginalFilename());
                    return existente;
                })
                .orElseGet(() -> documentos.save(new Documento(
                        titulo, tipo, audiencias, version,
                        archivo.getOriginalFilename(), hash, clock.instant())));

        documento.guardarContenido(serializar(bloques));

        bitacora.registrar(actor, Rol.ADMINISTRADOR, ENTIDAD, documento.getId(),
                "DOCUMENTO_SUBIDO",
                "%s (%s) version %s".formatted(titulo, tipo, version));

        publicador.publishEvent(new DocumentoParaIndexar(documento.getId(), bloques));
        return documento;
    }

    /**
     * Alta desde texto ya en memoria, para la semilla de demo (§12).
     *
     * Comparte todo el camino con la subida de un archivo salvo la extraccion,
     * para que lo que se indexa en la demo sea exactamente lo que se indexaria
     * en produccion.
     */
    @Transactional
    public Documento subirTexto(String titulo, TipoDocumento tipo, Set<Audiencia> audiencias,
                                String version, String nombreArchivo, String contenido,
                                String actor) {
        String hash = sha256(contenido);
        Optional<Documento> yaEsta = documentos.findByHashContenido(hash);
        if (yaEsta.isPresent()) {
            return yaEsta.get();
        }

        List<ExtractorTexto.BloqueTexto> bloques = extractor.desdeTexto(contenido);
        Documento documento = documentos.save(new Documento(
                titulo, tipo, audiencias, version, nombreArchivo, hash, clock.instant()));
        documento.guardarContenido(serializar(bloques));

        bitacora.registrar(actor, Rol.ADMINISTRADOR, ENTIDAD, documento.getId(),
                "DOCUMENTO_SUBIDO", "%s (semilla de demo)".formatted(titulo));

        publicador.publishEvent(new DocumentoParaIndexar(documento.getId(), bloques));
        return documento;
    }

    @Transactional(readOnly = true)
    public List<Documento> listar() {
        return documentos.findAllByOrderBySubidoEnDesc();
    }

    /**
     * Borra el documento y sus chunks.
     *
     * Aqui si hay borrado fisico, al contrario que en las entidades operativas
     * de §12: un documento no es un registro de porteria, y dejar un reglamento
     * derogado indexado seria peor que borrarlo, porque el asistente seguiria
     * citandolo.
     */
    @Transactional
    public void borrar(Long id, String actor) {
        Documento documento = documentos.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el documento", id));

        try {
            vectores.borrarPorDocumento(id);
        } catch (Exception e) {
            // Qdrant es una copia derivada: si no responde, el documento se
            // borra igual y `reindexar` deja la coleccion coherente despues.
            log.warn("No se pudieron borrar los chunks de {} en Qdrant: {}", id, e.getMessage());
        }

        bitacora.registrar(actor, Rol.ADMINISTRADOR, ENTIDAD, id, "DOCUMENTO_BORRADO",
                documento.getTitulo());
        documentos.delete(documento);
    }

    /**
     * Reconstruye la coleccion de Qdrant desde PostgreSQL (§11).
     *
     * Funciona porque el texto extraido se guarda en documento.contenido. Sin
     * esa columna esto habria exigido volver a subir cada archivo a mano.
     *
     * Los documentos subidos antes de la migracion V3 no tienen contenido y se
     * saltan: se avisa en el log en vez de fallar la operacion entera.
     */
    @Transactional
    public int reindexar(String actor) {
        List<Documento> todos = documentos.findAll();
        int lanzados = 0;

        for (Documento documento : todos) {
            if (documento.getContenido() == null) {
                log.warn("El documento {} no tiene contenido guardado: hay que volver a subirlo",
                        documento.getId());
                continue;
            }
            publicador.publishEvent(new DocumentoParaIndexar(
                    documento.getId(), deserializar(documento.getContenido())));
            lanzados++;
        }

        bitacora.registrar(actor, Rol.ADMINISTRADOR, ENTIDAD, null, "REINDEXADO",
                "%d de %d documentos puestos a reindexar".formatted(lanzados, todos.size()));

        log.info("Reindexado lanzado para {} documentos", lanzados);
        return lanzados;
    }

    private List<ExtractorTexto.BloqueTexto> deserializar(String contenidoJson) {
        try {
            return json.readValue(contenidoJson, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer el contenido guardado", e);
        }
    }

    private String serializar(List<ExtractorTexto.BloqueTexto> bloques) {
        try {
            return json.writeValueAsString(bloques);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el contenido extraido", e);
        }
    }

    private void validarTamano(MultipartFile archivo) {
        long maximo = (long) propiedades.subida().maxMb() * 1024 * 1024;
        if (archivo.isEmpty()) {
            throw new SolicitudInvalidaException("El archivo esta vacio");
        }
        if (archivo.getSize() > maximo) {
            throw new SolicitudInvalidaException(
                    "El archivo supera el maximo de %d MB".formatted(propiedades.subida().maxMb()));
        }
    }

    private String sha256(String contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 deberia estar siempre disponible", e);
        }
    }
}
