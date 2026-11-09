package co.porteriaya.autorizacion.service;

import java.security.SecureRandom;

import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.config.PorteriaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Genera el codigo de 6 digitos con reintento ante colision (§12).
 *
 * La unicidad solo aplica mientras la autorizacion esta ACTIVA, asi que un
 * codigo de una autorizacion ya cerrada puede reutilizarse. La comprobacion de
 * aqui evita el reintento costoso; la garantia real la da el indice unico
 * parcial de la base.
 */
@Component
@RequiredArgsConstructor
public class GeneradorCodigo {

    private static final int LIMITE = 1_000_000;

    private final AutorizacionRepository repositorio;
    private final PorteriaProperties propiedades;
    private final SecureRandom aleatorio = new SecureRandom();

    public String generar() {
        int intentos = propiedades.autorizaciones().intentosGeneracionCodigo();
        for (int i = 0; i < intentos; i++) {
            String candidato = "%06d".formatted(aleatorio.nextInt(LIMITE));
            if (!repositorio.existsByCodigoAndEstado(candidato, EstadoAutorizacion.ACTIVA)) {
                return candidato;
            }
        }
        throw new IllegalStateException(
                "No se pudo generar un codigo libre en %d intentos".formatted(intentos));
    }
}
