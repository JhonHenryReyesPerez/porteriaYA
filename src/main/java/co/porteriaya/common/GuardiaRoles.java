package co.porteriaya.common;

import java.util.Arrays;

import co.porteriaya.config.PorteriaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Valida el rol en servidor, endpoint por endpoint (§7). La UI no es la barrera.
 *
 * Limitacion conocida y asumida (§16): X-Rol y X-Actor son autodeclarados, asi
 * que esto evita errores de uso, no es autenticacion. Lo unico que protege de
 * verdad las operaciones de administracion es el PIN.
 */
@Component
@RequiredArgsConstructor
public class GuardiaRoles {

    private final PorteriaProperties propiedades;

    public void exigir(Rol rol, Rol... permitidos) {
        if (rol == null || !Arrays.asList(permitidos).contains(rol)) {
            throw new AccesoDenegadoException(
                    "Este endpoint solo admite los roles %s".formatted(Arrays.toString(permitidos)));
        }
    }

    /** Administracion: rol correcto Y PIN valido. */
    public void exigirAdministrador(Rol rol, String pin) {
        exigir(rol, Rol.ADMINISTRADOR);
        String esperado = propiedades.admin().pin();
        if (esperado == null || esperado.isBlank()) {
            throw new AccesoDenegadoException(
                    "No hay PIN de administrador configurado: revisa ADMIN_PIN en el .env");
        }
        if (pin == null || !esperado.equals(pin)) {
            throw new AccesoDenegadoException("PIN de administrador invalido");
        }
    }
}
