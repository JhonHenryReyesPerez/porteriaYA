package co.porteriaya.porteria.entity;

import java.time.Instant;

import co.porteriaya.autorizacion.entity.Autorizacion;
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
 * Una persona dentro del conjunto (§6).
 *
 * La duracion esperada se COPIA de la autorizacion al entrar en vez de leerse
 * por referencia: si el residente la cambia despues, los ingresos ya
 * registrados no deben moverse, porque la alerta de permanencia se calculo con
 * el valor que habia en ese momento.
 */
@Entity
@Table(name = "ingreso")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigenIngreso origen;

    /** Nulo cuando el origen es NO_ANUNCIADA; la base lo refuerza con un CHECK. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizacion_id")
    private Autorizacion autorizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residente_id", nullable = false)
    private Residente residente;

    @Column(length = 120)
    private String visitanteNombre;

    @Column(length = 4)
    private String visitanteDoc4;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private EstadoIngreso estado;

    @Column(length = 80)
    private String vigilante;

    private int duracionEsperadaMin;

    /** Bandera de PERMANENCIA_EXCEDIDA: el aviso se emite una sola vez (§6). */
    private boolean permanenciaAvisada;

    private Instant ingresoEn;
    private Instant salidaEn;

    private Ingreso(OrigenIngreso origen, Autorizacion autorizacion, Residente residente,
                    String visitanteNombre, String visitanteDoc4, String vigilante,
                    int duracionEsperadaMin, Instant ingresoEn) {
        this.origen = origen;
        this.autorizacion = autorizacion;
        this.residente = residente;
        this.visitanteNombre = visitanteNombre;
        this.visitanteDoc4 = visitanteDoc4;
        this.estado = EstadoIngreso.DENTRO;
        this.vigilante = vigilante;
        this.duracionEsperadaMin = duracionEsperadaMin;
        this.permanenciaAvisada = false;
        this.ingresoEn = ingresoEn;
    }

    /** Ingreso con codigo: la duracion esperada viene de la autorizacion. */
    public static Ingreso conAutorizacion(Autorizacion autorizacion, String visitanteNombre,
                                          String visitanteDoc4, String vigilante, Instant ahora) {
        return new Ingreso(OrigenIngreso.AUTORIZACION, autorizacion, autorizacion.getResidente(),
                visitanteNombre, visitanteDoc4, vigilante,
                autorizacion.getDuracionEsperadaMin(), ahora);
    }

    /** Ingreso de una visita no anunciada ya aprobada: no hay autorizacion detras. */
    public static Ingreso sinAnunciar(Residente residente, String visitanteNombre,
                                      String visitanteDoc4, String vigilante,
                                      int duracionEsperadaMin, Instant ahora) {
        return new Ingreso(OrigenIngreso.NO_ANUNCIADA, null, residente,
                visitanteNombre, visitanteDoc4, vigilante, duracionEsperadaMin, ahora);
    }

    /**
     * Registra la salida. Estricto igual que en la autorizacion: cerrar dos
     * veces el mismo ingreso es 409, no un 200 silencioso.
     */
    public void registrarSalida(Instant momento) {
        cambiarEstado(EstadoIngreso.SALIO);
        this.salidaEn = momento;
    }

    /** Cierre automatico del scheduler (§9): la salida nunca se registro. */
    public void cerrarSinSalida(Instant momento) {
        cambiarEstado(EstadoIngreso.SALIDA_NO_REGISTRADA);
        this.salidaEn = momento;
    }

    /** Marca que ya se aviso de la permanencia, para no repetir el aviso (§6). */
    public void marcarPermanenciaAvisada() {
        this.permanenciaAvisada = true;
    }

    public boolean estaDentro() {
        return estado == EstadoIngreso.DENTRO;
    }

    /** Instante a partir del cual la permanencia se considera excedida. */
    public Instant limitePermanencia(int graciaMinutos) {
        return ingresoEn.plusSeconds((long) (duracionEsperadaMin + graciaMinutos) * 60);
    }

    private void cambiarEstado(EstadoIngreso nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw TransicionInvalidaException.de("el ingreso", estado, nuevo);
        }
        this.estado = nuevo;
    }
}
