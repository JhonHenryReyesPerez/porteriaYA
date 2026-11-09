package co.porteriaya.paquete.entity;

import java.time.Instant;

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
 * Un paquete recibido en porteria (§6).
 *
 * Las tres banderas de aviso son lo que hace idempotente el escalamiento: el
 * scheduler corre cada minuto, y sin ellas el residente recibiria el mismo
 * recordatorio 1440 veces al dia.
 */
@Entity
@Table(name = "paquete")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Paquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residente_id", nullable = false)
    private Residente residente;

    @Column(length = 200)
    private String descripcion;

    @Column(length = 120)
    private String remitente;

    /** Codigo que viaja en el correo al residente; sirve para retirarlo. */
    @Column(length = 6)
    private String codigoRetiro;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoPaquete estado;

    @Column(length = 80)
    private String vigilante;

    private Instant recibidoEn;
    private Instant entregadoEn;

    @Column(length = 120)
    private String entregadoA;

    @Column(name = "aviso_24h")
    private boolean aviso24h;

    @Column(name = "aviso_72h")
    private boolean aviso72h;

    private boolean escalado;

    public Paquete(Residente residente, String descripcion, String remitente, String codigoRetiro,
                   String vigilante, Instant recibidoEn) {
        this.residente = residente;
        this.descripcion = descripcion;
        this.remitente = remitente;
        this.codigoRetiro = codigoRetiro;
        this.estado = EstadoPaquete.RECIBIDO;
        this.vigilante = vigilante;
        this.recibidoEn = recibidoEn;
    }

    public boolean sigueSinRetirar() {
        return estado == EstadoPaquete.RECIBIDO;
    }

    public void entregar(String aQuien, Instant momento) {
        cambiarEstado(EstadoPaquete.ENTREGADO);
        this.entregadoA = aQuien;
        this.entregadoEn = momento;
    }

    public void devolver(Instant momento) {
        cambiarEstado(EstadoPaquete.DEVUELTO);
        this.entregadoEn = momento;
    }

    public void marcarAviso24h() {
        this.aviso24h = true;
    }

    public void marcarAviso72h() {
        this.aviso72h = true;
    }

    public void marcarEscalado() {
        this.escalado = true;
    }

    /** Horas que lleva sin retirar, para decidir que aviso toca (§9). */
    public long horasSinRetirar(Instant ahora) {
        return java.time.Duration.between(recibidoEn, ahora).toHours();
    }

    private void cambiarEstado(EstadoPaquete nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw TransicionInvalidaException.de("el paquete", estado, nuevo);
        }
        this.estado = nuevo;
    }
}
