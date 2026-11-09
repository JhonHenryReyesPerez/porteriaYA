package co.porteriaya.autorizacion.service;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.common.RecursoNoEncontradoException;
import co.porteriaya.common.TransicionInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paso 3 de la validacion (§6): el descuento del uso.
 *
 * Esta es la pieza que hace que dos vigilantes validando el mismo codigo de un
 * solo uso a la vez no puedan pasar los dos. Toda la exclusion la da una unica
 * sentencia UPDATE con su condicion en el WHERE; no hay leer-modificar-guardar
 * y no hace falta bloquear nada a mano.
 *
 * Lo llamara POST /api/porteria/ingresos cuando exista (paso 3 del orden de
 * construccion). Se separa de ValidacionService a proposito: validar lee y esto
 * escribe.
 */
@Service
@RequiredArgsConstructor
public class ConsumoUsoService {

    private final AutorizacionRepository autorizaciones;

    /**
     * Descuenta un uso de forma atomica.
     *
     * @throws TransicionInvalidaException 409 si no quedaban usos
     */
    @Transactional
    public Autorizacion consumirUso(Long autorizacionId) {
        int filas = autorizaciones.consumirUso(autorizacionId);

        if (filas == 0) {
            // Puede ser que no queden usos o que el id no exista: hay que
            // distinguirlo para no devolver 409 cuando el 404 es lo correcto.
            Autorizacion existente = autorizaciones.findById(autorizacionId)
                    .orElseThrow(() -> RecursoNoEncontradoException.de("la autorizacion", autorizacionId));
            throw new TransicionInvalidaException("USOS_AGOTADOS",
                    "La autorizacion ya no tiene usos disponibles (%d de %d)"
                            .formatted(existente.getUsosActuales(), existente.getUsosMax()));
        }

        // El UPDATE fue nativo, asi que la entidad en memoria estaba obsoleta:
        // el @Modifying limpia el contexto y este findById relee el valor real.
        Autorizacion autorizacion = autorizaciones.findById(autorizacionId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la autorizacion", autorizacionId));

        // El `sigue ACTIVA` no es decorativo: entre el UPDATE y esta relectura
        // otro hilo pudo haber gastado el ultimo uso y haberla marcado AGOTADA
        // ya. Sin esta comprobacion, el segundo hilo intentaria la transicion
        // AGOTADA -> AGOTADA, que es invalida, y perderia un uso que si
        // consumio. Decidir si toca cambiar es del service; validar el salto,
        // de la entidad.
        if (!autorizacion.usosIlimitados()
                && autorizacion.getEstado() == EstadoAutorizacion.ACTIVA
                && autorizacion.getUsosActuales() >= autorizacion.getUsosMax()) {
            autorizacion.cambiarEstado(EstadoAutorizacion.AGOTADA);
        }

        return autorizacion;
    }
}
