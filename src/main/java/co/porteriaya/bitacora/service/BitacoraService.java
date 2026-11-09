package co.porteriaya.bitacora.service;

import java.time.Clock;

import co.porteriaya.bitacora.entity.Bitacora;
import co.porteriaya.bitacora.repository.BitacoraRepository;
import co.porteriaya.common.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe la bitacora de auditoria (§6). Solo da de alta; no hay forma de
 * editar ni borrar una entrada.
 *
 * Nunca recibe datos personales completos: el detalle lleva nombres y los 4
 * ultimos digitos como maximo, nunca un documento entero ni un codigo completo.
 */
@Service
@RequiredArgsConstructor
public class BitacoraService {

    private final BitacoraRepository repositorio;
    private final Clock clock;

    @Transactional
    public Bitacora registrar(String actor, Rol rol, String entidad, Long entidadId,
                              String accion, String detalle) {
        return repositorio.save(
                new Bitacora(clock.instant(), actor, rol, entidad, entidadId, accion, detalle));
    }
}
