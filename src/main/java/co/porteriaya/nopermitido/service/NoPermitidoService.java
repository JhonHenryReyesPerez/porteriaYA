package co.porteriaya.nopermitido.service;

import java.util.List;
import java.util.Objects;

import co.porteriaya.common.Normalizador;
import co.porteriaya.nopermitido.entity.NoPermitido;
import co.porteriaya.nopermitido.repository.NoPermitidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Paso 4 de la validacion (§6): cotejo con la lista de no permitidos. */
@Service
@RequiredArgsConstructor
public class NoPermitidoService {

    private final NoPermitidoRepository repositorio;

    /**
     * Coteja nombre y documento contra la lista activa.
     *
     * - Nombre normalizado y doc4 iguales -> EXACTA, se niega el ingreso.
     * - Solo el nombre igual, o el registro de la lista sin doc4 -> PARCIAL,
     *   se avisa al vigilante y el decide.
     *
     * Si varios registros coinciden, gana el mas grave: una EXACTA siempre
     * pesa mas que una PARCIAL.
     */
    @Transactional(readOnly = true)
    public Coincidencia evaluar(String nombre, String doc4) {
        String nombreBuscado = Normalizador.nombre(nombre);
        if (nombreBuscado == null || nombreBuscado.isEmpty()) {
            return Coincidencia.ninguna();
        }

        List<NoPermitido> candidatos = repositorio.findByActivoTrue().stream()
                .filter(registro -> nombreBuscado.equals(Normalizador.nombre(registro.getNombre())))
                .toList();

        if (candidatos.isEmpty()) {
            return Coincidencia.ninguna();
        }

        return candidatos.stream()
                .filter(registro -> coincideDocumento(registro, doc4))
                .findFirst()
                .map(registro -> new Coincidencia(Coincidencia.Nivel.EXACTA, registro))
                .orElseGet(() -> new Coincidencia(Coincidencia.Nivel.PARCIAL, candidatos.get(0)));
    }

    private boolean coincideDocumento(NoPermitido registro, String doc4) {
        return registro.getDoc4() != null
                && doc4 != null
                && Objects.equals(registro.getDoc4().trim(), doc4.trim());
    }
}
