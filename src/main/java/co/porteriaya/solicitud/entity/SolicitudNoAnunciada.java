package co.porteriaya.solicitud.entity;

import java.time.Instant;

import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.porteria.entity.Ingreso;
import co.porteriaya.residente.entity.Residente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Alguien que llega sin estar anunciado (§6).
 *
 * Es entidad propia y no un estado previo del Ingreso, y eso es lo que hace que
 * «si el residente rechaza, el vigilante no puede registrar el ingreso» se
 * cumpla por construccion: mientras no haya aprobacion no existe ningun
 * Ingreso que cerrar ni que contar en «quien esta dentro».
 */
@Entity
@Table(name = "solicitud_no_anunciada")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitudNoAnunciada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residente_id", nullable = false)
    private Residente residente;

    @Column(length = 120)
    private String visitanteNombre;

    @Column(length = 4)
    private String visitanteDoc4;

    @Column(length = 200)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EstadoSolicitud estado;

    @Column(length = 80)
    private String vigilante;

    private Instant creadoEn;
    private Instant venceEn;
    private Instant resueltoEn;

    /** Nulo hasta que la solicitud se aprueba. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingreso_id")
    private Ingreso ingreso;

    public SolicitudNoAnunciada(Residente residente, String visitanteNombre, String visitanteDoc4,
                                String motivo, String vigilante, Instant creadoEn, Instant venceEn) {
        this.residente = residente;
        this.visitanteNombre = visitanteNombre;
        this.visitanteDoc4 = visitanteDoc4;
        this.motivo = motivo;
        this.estado = EstadoSolicitud.PENDIENTE;
        this.vigilante = vigilante;
        this.creadoEn = creadoEn;
        this.venceEn = venceEn;
    }

    public boolean estaPendiente() {
        return estado == EstadoSolicitud.PENDIENTE;
    }

    public boolean haVencido(Instant ahora) {
        return ahora.isAfter(venceEn);
    }

    /** Decision del residente desde el enlace del correo. */
    public void resolver(EstadoSolicitud nuevo, Instant momento) {
        cambiarEstado(nuevo);
        this.resueltoEn = momento;
    }

    /** El plazo se agoto sin respuesta y la pelota pasa al vigilante (§9). */
    public void marcarSinRespuesta(Instant momento) {
        cambiarEstado(EstadoSolicitud.SIN_RESPUESTA);
        this.resueltoEn = momento;
    }

    /** Se ata el ingreso que nacio de esta solicitud aprobada. */
    public void asociarIngreso(Ingreso ingreso) {
        if (!estado.permiteIngreso()) {
            throw new TransicionInvalidaException("SOLICITUD_SIN_APROBAR",
                    "Una solicitud en estado %s no puede tener ingreso".formatted(estado));
        }
        this.ingreso = ingreso;
    }

    private void cambiarEstado(EstadoSolicitud nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw TransicionInvalidaException.de("la solicitud", estado, nuevo);
        }
        this.estado = nuevo;
    }
}
