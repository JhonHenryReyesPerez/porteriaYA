package co.porteriaya.solicitud.entity;

import java.time.Instant;

import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.solicitud.dto.AccionAprobacion;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token de un solo uso con vencimiento para aprobar desde el correo (§6).
 *
 * Se guarda el SHA-256, nunca el token en claro: si alguien lee la base, no
 * puede aprobar nada. Y el token en claro solo existe en el momento de crearlo
 * y en el enlace que viaja al residente, nunca en logs (§5).
 */
@Entity
@Table(name = "token_aprobacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenAprobacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private SolicitudNoAnunciada solicitud;

    /** La decision que se aplico con este token. Auditoria. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AccionAprobacion accion;

    private Instant creadoEn;
    private Instant venceEn;
    private Instant usadoEn;

    public TokenAprobacion(String tokenHash, SolicitudNoAnunciada solicitud,
                           Instant creadoEn, Instant venceEn) {
        this.tokenHash = tokenHash;
        this.solicitud = solicitud;
        this.creadoEn = creadoEn;
        this.venceEn = venceEn;
    }

    public boolean estaUsado() {
        return usadoEn != null;
    }

    public boolean haVencido(Instant ahora) {
        return ahora.isAfter(venceEn);
    }

    /**
     * Quema el token. Un segundo intento con el mismo token es 409, y eso es lo
     * que convierte el enlace del correo en algo de un solo uso.
     */
    public void usar(AccionAprobacion accion, Instant momento) {
        if (estaUsado()) {
            throw new TransicionInvalidaException("TOKEN_YA_USADO",
                    "Este enlace de aprobacion ya se uso");
        }
        if (haVencido(momento)) {
            throw new TransicionInvalidaException("TOKEN_VENCIDO",
                    "Este enlace de aprobacion ya vencio");
        }
        this.accion = accion;
        this.usadoEn = momento;
    }
}
