package co.porteriaya.nopermitido.service;

import co.porteriaya.nopermitido.entity.NoPermitido;

/**
 * Resultado de cotejar una persona con la lista de no permitidos (§6).
 *
 * Como del visitante solo se guardan nombre y los ultimos 4 digitos, hay dos
 * niveles: la coincidencia EXACTA niega el ingreso, la PARCIAL solo avisa.
 */
public record Coincidencia(Nivel nivel, NoPermitido registro) {

    public enum Nivel {
        /** Nada que reportar. */
        NINGUNA,
        /** Coincide el nombre pero no el documento, o el registro no tiene documento. */
        PARCIAL,
        /** Coinciden nombre y documento: se niega. */
        EXACTA
    }

    public static Coincidencia ninguna() {
        return new Coincidencia(Nivel.NINGUNA, null);
    }

    public boolean niega() {
        return nivel == Nivel.EXACTA;
    }

    public boolean avisa() {
        return nivel == Nivel.PARCIAL;
    }

    /** Motivo del registro, para que el vigilante sepa por que esta en la lista. */
    public String motivo() {
        return registro == null ? null : registro.getMotivo();
    }
}
