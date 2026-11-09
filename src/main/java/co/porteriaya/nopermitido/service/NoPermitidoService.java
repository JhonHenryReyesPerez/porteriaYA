package co.porteriaya.nopermitido.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.Normalizador;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.nopermitido.dto.NoPermitidoRequest;
import co.porteriaya.nopermitido.dto.RespuestaNoPermitido;
import co.porteriaya.nopermitido.entity.NoPermitido;
import co.porteriaya.nopermitido.repository.NoPermitidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Paso 4 de la validacion (§6): cotejo con la lista de no permitidos. */
@Service
@RequiredArgsConstructor
public class NoPermitidoService {

    private static final String ENTIDAD = "NO_PERMITIDO";

    private final NoPermitidoRepository repositorio;
    private final BitacoraService bitacora;
    private final Clock clock;

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

    /** Alta en la lista. Solo administracion, con PIN (§7). */
    @Transactional
    public RespuestaNoPermitido registrar(NoPermitidoRequest peticion, String actor) {
        NoPermitido registro = repositorio.save(new NoPermitido(
                peticion.nombre(), peticion.doc4(), peticion.motivo(), actor, clock.instant()));

        bitacora.registrar(actor, Rol.ADMINISTRADOR, ENTIDAD, registro.getId(),
                "NO_PERMITIDO_REGISTRADO", "Motivo: " + peticion.motivo());

        return RespuestaNoPermitido.de(registro);
    }

    /** Incluye los desactivados: la lista tambien es historial para administracion. */
    @Transactional(readOnly = true)
    public List<RespuestaNoPermitido> listar() {
        return repositorio.findAllByOrderByActivoDescCreadoEnDesc().stream()
                .map(RespuestaNoPermitido::de)
                .toList();
    }

    /**
     * Desactiva un registro. No borra: §12 prohibe borrados fisicos de
     * entidades operativas, y aqui ademas interesa poder responder a «quien
     * quito a esta persona de la lista y cuando».
     */
    @Transactional
    public RespuestaNoPermitido desactivar(Long id, String actor) {
        NoPermitido registro = repositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el registro", id));

        registro.desactivar();

        bitacora.registrar(actor, Rol.ADMINISTRADOR, ENTIDAD, id,
                "NO_PERMITIDO_DESACTIVADO", "Retirado de la lista por " + actor);

        return RespuestaNoPermitido.de(registro);
    }

    private boolean coincideDocumento(NoPermitido registro, String doc4) {
        return registro.getDoc4() != null
                && doc4 != null
                && Objects.equals(registro.getDoc4().trim(), doc4.trim());
    }
}
