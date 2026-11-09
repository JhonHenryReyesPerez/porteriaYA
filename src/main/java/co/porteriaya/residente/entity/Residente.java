package co.porteriaya.residente.entity;

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

/** Un registro por apartamento: alimenta el selector de torre y apartamento (§7). */
@Entity
@Table(name = "residente")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Residente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10)
    private String torre;

    @Column(length = 10)
    private String apartamento;

    @Column(length = 120)
    private String nombre;

    @Column(length = 160)
    private String correo;

    @Column(length = 30)
    private String telefono;
    private boolean activo;
    private Instant creadoEn;

    public Residente(String torre, String apartamento, String nombre, String correo,
                     String telefono, Instant creadoEn) {
        this.torre = torre;
        this.apartamento = apartamento;
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.activo = true;
        this.creadoEn = creadoEn;
    }

    /** Identificador que el vigilante y el residente ven en pantalla: "A-101". */
    public String identificador() {
        return torre + "-" + apartamento;
    }

    public void desactivar() {
        this.activo = false;
    }
}
