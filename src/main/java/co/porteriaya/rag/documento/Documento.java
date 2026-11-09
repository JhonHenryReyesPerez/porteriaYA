package co.porteriaya.rag.documento;

import java.time.Instant;
import java.util.Set;

import co.porteriaya.common.TransicionInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un documento del conjunto indexado para el asistente (§11).
 *
 * PostgreSQL es la fuente de verdad: Qdrant es una copia derivada que
 * `reindexar` puede reconstruir desde aqui.
 */
@Entity
@Table(name = "documento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoDocumento tipo;

    /** Conjunto de audiencias separado por comas: "TODOS" o "RESIDENTE,VIGILANTE". */
    @Column(length = 40)
    private String audiencia;

    @Column(length = 20)
    private String version;

    @Column(length = 255)
    private String nombreArchivo;

    /** SHA-256 del contenido: si ya existe con el mismo hash, no se reindexa. */
    @Column(length = 64)
    private String hashContenido;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoDocumento estado;

    @Column(length = 500)
    private String motivoError;

    private int chunks;
    private Instant subidoEn;
    private Instant indexadoEn;

    /**
     * Texto extraido, en JSON con sus paginas. Es lo que hace que `reindexar`
     * pueda reconstruir Qdrant sin volver a subir el archivo (§11).
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String contenido;

    public Documento(String titulo, TipoDocumento tipo, Set<Audiencia> audiencias, String version,
                     String nombreArchivo, String hashContenido, Instant subidoEn) {
        this.titulo = titulo;
        this.tipo = tipo;
        this.audiencia = Audiencia.serializar(audiencias);
        this.version = version;
        this.nombreArchivo = nombreArchivo;
        this.hashContenido = hashContenido;
        this.estado = EstadoDocumento.SUBIDO;
        this.subidoEn = subidoEn;
    }

    public void guardarContenido(String contenidoJson) {
        this.contenido = contenidoJson;
    }

    public Set<Audiencia> audiencias() {
        return Audiencia.parsear(audiencia);
    }

    public void empezarIndexado() {
        cambiarEstado(EstadoDocumento.INDEXANDO);
        this.motivoError = null;
    }

    public void terminarIndexado(int chunks, Instant momento) {
        cambiarEstado(EstadoDocumento.INDEXADO);
        this.chunks = chunks;
        this.indexadoEn = momento;
    }

    /** Si la indexacion falla, el estado queda en Error con el motivo (§11). */
    public void fallarIndexado(String motivo) {
        cambiarEstado(EstadoDocumento.ERROR);
        this.motivoError = motivo == null ? "Error desconocido" : motivo.substring(
                0, Math.min(motivo.length(), 500));
    }

    /** Version nueva del mismo documento: se reindexa desde cero. */
    public void nuevaVersion(String version, String hashContenido, String nombreArchivo) {
        this.version = version;
        this.hashContenido = hashContenido;
        this.nombreArchivo = nombreArchivo;
        this.chunks = 0;
        this.indexadoEn = null;
    }

    private void cambiarEstado(EstadoDocumento nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw TransicionInvalidaException.de("el documento", estado, nuevo);
        }
        this.estado = nuevo;
    }
}
