package co.porteriaya.nopermitido.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Persona a la que no se le permite el ingreso (§6). */
@Entity
@Table(name = "no_permitido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoPermitido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String nombre;

    /** Ultimos 4 digitos del documento. Puede ser nulo: entonces solo hay coincidencia parcial. */
    @Column(length = 4)
    private String doc4;

    @Column(length = 200)
    private String motivo;

    private boolean activo;

    @Column(length = 80)
    private String registradoPor;
    private Instant creadoEn;

    public NoPermitido(String nombre, String doc4, String motivo,
                       String registradoPor, Instant creadoEn) {
        this.nombre = nombre;
        this.doc4 = doc4;
        this.motivo = motivo;
        this.activo = true;
        this.registradoPor = registradoPor;
        this.creadoEn = creadoEn;
    }

    public void desactivar() {
        this.activo = false;
    }
}
