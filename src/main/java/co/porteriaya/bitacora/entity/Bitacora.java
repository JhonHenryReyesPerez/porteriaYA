package co.porteriaya.bitacora.entity;

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

/**
 * Registro de auditoria. Es append-only (§6): se construye una vez y no tiene
 * ningun setter publico, asi que nada en el codigo puede modificar una entrada
 * ya escrita. La API tampoco expone PUT, PATCH ni DELETE sobre ella.
 */
@Entity
@Table(name = "bitacora")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant fecha;

    @Column(length = 80)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Rol rol;

    @Column(length = 40)
    private String entidad;

    private Long entidadId;

    @Column(length = 60)
    private String accion;

    @Column(length = 500)
    private String detalle;

    public Bitacora(Instant fecha, String actor, Rol rol, String entidad,
                    Long entidadId, String accion, String detalle) {
        this.fecha = fecha;
        this.actor = actor;
        this.rol = rol;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.detalle = detalle;
    }
}
