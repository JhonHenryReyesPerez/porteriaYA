package co.porteriaya.autorizacion.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;

import co.porteriaya.common.TransicionInvalidaException;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Autorizacion de ingreso (§6). Sin logica de negocio: solo datos y las
 * invariantes que protegen sus propios campos. Quien decide cuando cambia de
 * estado es el service.
 */
@Entity
@Table(name = "autorizacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Autorizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residente_id", nullable = false)
    private Residente residente;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TipoAutorizacion tipo;

    @Column(length = 6)
    private String codigo;

    @Column(length = 120)
    private String visitanteNombre;

    /** Solo los ultimos 4 digitos del documento (Ley 1581 de 2012). */
    @Column(length = 4)
    private String visitanteDoc4;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoAutorizacion estado;

    private Instant validaDesde;
    private Instant validaHasta;

    /** Dias ISO separados por coma, "1,3,5". Nulo si no es recurrente. */
    @Column(length = 20)
    private String diasSemana;
    private LocalTime horaDesde;
    private LocalTime horaHasta;

    /** Nulo = usos ilimitados dentro de la franja (§6). */
    private Integer usosMax;

    private int usosActuales;
    private int duracionEsperadaMin;
    private Instant creadoEn;

    @SuppressWarnings("java:S107")
    public Autorizacion(Residente residente, TipoAutorizacion tipo, String codigo,
                        String visitanteNombre, String visitanteDoc4,
                        Instant validaDesde, Instant validaHasta,
                        Recurrencia recurrencia, Integer usosMax,
                        int duracionEsperadaMin, Instant creadoEn) {
        this.residente = residente;
        this.tipo = tipo;
        this.codigo = codigo;
        this.visitanteNombre = visitanteNombre;
        this.visitanteDoc4 = visitanteDoc4;
        this.estado = EstadoAutorizacion.ACTIVA;
        this.validaDesde = validaDesde;
        this.validaHasta = validaHasta;
        if (recurrencia != null) {
            this.diasSemana = recurrencia.serializarDias();
            this.horaDesde = recurrencia.desde();
            this.horaHasta = recurrencia.hasta();
        }
        this.usosMax = usosMax;
        this.usosActuales = 0;
        this.duracionEsperadaMin = duracionEsperadaMin;
        this.creadoEn = creadoEn;
    }

    public Optional<Recurrencia> recurrencia() {
        return Recurrencia.de(diasSemana, horaDesde, horaHasta);
    }

    public boolean esRecurrente() {
        return diasSemana != null && !diasSemana.isBlank();
    }

    public boolean usosIlimitados() {
        return usosMax == null;
    }

    /**
     * Unico camino para cambiar de estado: valida contra el mapa de
     * transiciones del enum y lanza 409 si el salto no existe (§6).
     *
     * Es estricto a proposito, incluso para repetir el estado actual: cancelar
     * dos veces la misma autorizacion es un error del cliente y tiene que
     * verse como 409, no colarse como un 200 silencioso. Quien decide SI toca
     * cambiar de estado es el service; esto solo comprueba que el salto existe.
     */
    public void cambiarEstado(EstadoAutorizacion nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw TransicionInvalidaException.de("la autorizacion", estado, nuevo);
        }
        this.estado = nuevo;
    }
}
