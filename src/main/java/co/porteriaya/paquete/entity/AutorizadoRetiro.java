package co.porteriaya.paquete.entity;

import java.time.Instant;

import co.porteriaya.residente.entity.Residente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Persona a la que el residente autoriza a retirar sus paquetes (§6).
 *
 * Es la segunda via de retiro, junto al codigo del correo: sirve para la
 * empleada domestica o el familiar que recoge sin tener el codigo.
 */
@Entity
@Table(name = "autorizado_retiro")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutorizadoRetiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residente_id", nullable = false)
    private Residente residente;

    @Column(length = 120)
    private String nombre;

    @Column(length = 4)
    private String doc4;

    private boolean activo;
    private Instant creadoEn;

    public AutorizadoRetiro(Residente residente, String nombre, String doc4, Instant creadoEn) {
        this.residente = residente;
        this.nombre = nombre;
        this.doc4 = doc4;
        this.activo = true;
        this.creadoEn = creadoEn;
    }

    public void desactivar() {
        this.activo = false;
    }
}
