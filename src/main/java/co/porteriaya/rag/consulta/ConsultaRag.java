package co.porteriaya.rag.consulta;

import java.math.BigDecimal;
import java.time.Instant;

import co.porteriaya.common.Rol;
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
 * Una pregunta al asistente y su respuesta (§11).
 *
 * La pregunta se guarda YA SANITIZADA: sin nombres, documentos, correos ni
 * telefonos. El historial del chat no es sitio para datos personales (§6).
 */
@Entity
@Table(name = "consulta_rag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultaRag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String pregunta;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Rol rol;

    @Column(length = 80)
    private String actor;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String respuesta;

    @Column(length = 1000)
    private String fuentes;

    @Column(precision = 5, scale = 4)
    private BigDecimal mejorSimilitud;

    private boolean sinRespuesta;
    private Integer latenciaMs;

    /** +1 o -1 desde los botones del chat (§13). Nulo mientras nadie vote. */
    private Short feedback;

    private Instant creadoEn;

    public ConsultaRag(String pregunta, Rol rol, String actor, String respuesta, String fuentes,
                       BigDecimal mejorSimilitud, boolean sinRespuesta, Integer latenciaMs,
                       Instant creadoEn) {
        this.pregunta = pregunta;
        this.rol = rol;
        this.actor = actor;
        this.respuesta = respuesta;
        this.fuentes = fuentes;
        this.mejorSimilitud = mejorSimilitud;
        this.sinRespuesta = sinRespuesta;
        this.latenciaMs = latenciaMs;
        this.creadoEn = creadoEn;
    }

    public void votar(short valor) {
        this.feedback = valor;
    }
}
