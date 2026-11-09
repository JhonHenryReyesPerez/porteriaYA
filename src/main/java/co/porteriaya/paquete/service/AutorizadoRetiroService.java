package co.porteriaya.paquete.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import co.porteriaya.bitacora.service.BitacoraService;
import co.porteriaya.common.AccesoDenegadoException;
import co.porteriaya.common.Normalizador;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.Rol;
import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.paquete.dto.AutorizadoRetiroRequest;
import co.porteriaya.paquete.dto.RespuestaAutorizadoRetiro;
import co.porteriaya.paquete.entity.AutorizadoRetiro;
import co.porteriaya.paquete.repository.AutorizadoRetiroRepository;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quien puede retirar los paquetes de un apartamento sin el codigo del correo (§6).
 *
 * Esta es la segunda via de retiro, y hasta ahora la lista solo se podia
 * sembrar a mano en la base: {@link PaqueteService} ya la consultaba al
 * entregar, pero nadie podia darla de alta.
 */
@Service
@RequiredArgsConstructor
public class AutorizadoRetiroService {

    private static final String ENTIDAD = "AutorizadoRetiro";

    private final AutorizadoRetiroRepository autorizados;
    private final ResidenteRepository residentes;
    private final BitacoraService bitacora;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RespuestaAutorizadoRetiro> listar(Long residenteId, Rol rol, String actor) {
        Residente residente = exigirAcceso(residenteId, rol, actor);
        return autorizados.findByResidenteIdAndActivoTrue(residente.getId()).stream()
                .map(RespuestaAutorizadoRetiro::de)
                .toList();
    }

    @Transactional
    public RespuestaAutorizadoRetiro registrar(Long residenteId, AutorizadoRetiroRequest peticion,
                                               Rol rol, String actor) {
        Residente residente = exigirAcceso(residenteId, rol, actor);

        // Un apartamento inactivo no recibe paquetes (§6), asi que autorizar a
        // alguien a retirarlos no lleva a ninguna parte.
        if (!residente.isActivo()) {
            throw new TransicionInvalidaException("APARTAMENTO_INACTIVO",
                    "El apartamento %s esta inactivo".formatted(residente.identificador()));
        }

        // El cotejo del retiro compara el nombre normalizado, asi que dos
        // registros que normalizan igual son el mismo autorizado con otra
        // ortografia: el segundo no anade nada y vuelve la lista ambigua.
        String buscado = Normalizador.nombre(peticion.nombre());
        boolean repetido = autorizados.findByResidenteIdAndActivoTrue(residente.getId()).stream()
                .anyMatch(a -> buscado.equals(Normalizador.nombre(a.getNombre()))
                        && Objects.equals(a.getDoc4(), peticion.doc4()));
        if (repetido) {
            throw new TransicionInvalidaException("AUTORIZADO_REPETIDO",
                    "%s ya esta autorizado a retirar los paquetes de %s".formatted(
                            peticion.nombre(), residente.identificador()));
        }

        AutorizadoRetiro guardado = autorizados.save(new AutorizadoRetiro(
                residente, peticion.nombre().strip(), peticion.doc4(), clock.instant()));

        bitacora.registrar(actor, rol, ENTIDAD, guardado.getId(), "AUTORIZADO_RETIRO_ALTA",
                "%s queda autorizado a retirar paquetes de %s".formatted(
                        guardado.getNombre(), residente.identificador()));

        return RespuestaAutorizadoRetiro.de(guardado);
    }

    /** Desactivacion logica, no borrado (§12). */
    @Transactional
    public RespuestaAutorizadoRetiro desactivar(Long residenteId, Long autorizadoId,
                                                Rol rol, String actor) {
        Residente residente = exigirAcceso(residenteId, rol, actor);

        AutorizadoRetiro autorizado = autorizados.findById(autorizadoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el autorizado", autorizadoId));

        // Sin esta comprobacion, el id de la ruta seria decorativo y cualquier
        // residente podria quitar a un autorizado de otro apartamento.
        if (!autorizado.getResidente().getId().equals(residente.getId())) {
            throw new RecursoNoEncontradoException(
                    "El autorizado %s no pertenece al apartamento %s".formatted(
                            autorizadoId, residente.identificador()));
        }

        if (!autorizado.isActivo()) {
            throw new TransicionInvalidaException("YA_DESACTIVADO",
                    "Ese autorizado ya estaba desactivado");
        }

        autorizado.desactivar();

        bitacora.registrar(actor, rol, ENTIDAD, autorizado.getId(), "AUTORIZADO_RETIRO_BAJA",
                "%s deja de poder retirar paquetes de %s".formatted(
                        autorizado.getNombre(), residente.identificador()));

        return RespuestaAutorizadoRetiro.de(autorizado);
    }

    /**
     * El id de la ruta es el del residente; el rol y el actor vienen de las
     * cabeceras. Un residente solo toca su propio apartamento: sin esto, el
     * selector de la UI seria la unica barrera, y §7 dice que no lo es.
     *
     * El vigilante y la administracion si pueden consultar cualquiera, porque
     * lo necesitan en la puerta para acreditar un retiro.
     */
    private Residente exigirAcceso(Long residenteId, Rol rol, String actor) {
        Residente residente = residentes.findById(residenteId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el apartamento", residenteId));

        if (rol == Rol.RESIDENTE && !residente.identificador().equals(actor)) {
            throw new AccesoDenegadoException(
                    "Un residente solo puede gestionar los autorizados de su propio apartamento");
        }
        return residente;
    }
}
