package co.porteriaya.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.porteriaya.PropiedadesDePrueba;
import co.porteriaya.config.PorteriaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * El PIN de administrador es lo UNICO que protege de verdad las operaciones
 * destructivas (§7, §16), porque X-Rol es autodeclarado. Merece sus propias
 * pruebas y no quedarse cubierto de refilon por los tests de integracion.
 */
class GuardiaRolesTest {

    private GuardiaRoles guardiaConPin(String pin) {
        PorteriaProperties base = PropiedadesDePrueba.porteria();
        PorteriaProperties propiedades = new PorteriaProperties(
                base.baseUrl(),
                new PorteriaProperties.Admin(pin),
                base.cors(),
                base.autorizaciones(),
                base.aprobacion(),
                base.permanencia(),
                base.paquete(),
                base.n8n());
        return new GuardiaRoles(propiedades);
    }

    private GuardiaRoles guardia() {
        return guardiaConPin("pin-correcto");
    }

    // ------------------------------------------------------------ roles

    @Test
    void elRolPermitidoPasa() {
        assertThatCode(() -> guardia().exigir(Rol.VIGILANTE, Rol.VIGILANTE))
                .doesNotThrowAnyException();
    }

    @Test
    void cualquieraDeLosRolesPermitidosPasa() {
        assertThatCode(() -> guardia().exigir(Rol.ADMINISTRADOR, Rol.VIGILANTE, Rol.ADMINISTRADOR))
                .doesNotThrowAnyException();
    }

    @Test
    void unRolQueNoEstaEnLaListaEs403() {
        assertThatThrownBy(() -> guardia().exigir(Rol.RESIDENTE, Rol.VIGILANTE))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("VIGILANTE");
    }

    @Test
    void sinRolEs403() {
        assertThatThrownBy(() -> guardia().exigir(null, Rol.VIGILANTE))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void unaListaVaciaDePermitidosNoDejaPasarANadie() {
        for (Rol rol : Rol.values()) {
            assertThatThrownBy(() -> guardia().exigir(rol))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
    }

    // ----------------------------------------------- PIN de administrador

    @Test
    void elAdministradorConSuPinPasa() {
        assertThatCode(() -> guardia().exigirAdministrador(Rol.ADMINISTRADOR, "pin-correcto"))
                .doesNotThrowAnyException();
    }

    @Test
    void elAdministradorSinPinEs403() {
        assertThatThrownBy(() -> guardia().exigirAdministrador(Rol.ADMINISTRADOR, null))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("invalido");
    }

    @Test
    void elAdministradorConElPinEquivocadoEs403() {
        assertThatThrownBy(() -> guardia().exigirAdministrador(Rol.ADMINISTRADOR, "otro"))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void elPinNoEsSensibleAlEspacioPorAccidente() {
        // No se hace trim: un PIN con espacios es un PIN distinto, y adivinar
        // que el usuario "queria decir" otra cosa en una comprobacion de
        // seguridad es exactamente lo que no hay que hacer.
        assertThatThrownBy(() -> guardia().exigirAdministrador(Rol.ADMINISTRADOR, " pin-correcto "))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Rol.class, names = {"RESIDENTE", "VIGILANTE"})
    void elPinCorrectoNoAscendeAUnRolQueNoEsAdministrador(Rol rol) {
        // Tener el PIN no te hace administrador: primero el rol, luego el PIN
        assertThatThrownBy(() -> guardia().exigirAdministrador(rol, "pin-correcto"))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    // ------------------------------------------- PIN mal configurado

    @Test
    void siNoHayPinConfiguradoNadieEsAdministrador() {
        // Falla cerrado: un .env sin ADMIN_PIN no debe dejar la puerta abierta
        assertThatThrownBy(() -> guardiaConPin("").exigirAdministrador(Rol.ADMINISTRADOR, ""))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("ADMIN_PIN");

        assertThatThrownBy(() -> guardiaConPin(null).exigirAdministrador(Rol.ADMINISTRADOR, "lo-que-sea"))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("ADMIN_PIN");
    }

    @Test
    void siElPinConfiguradoEstaEnBlancoTampocoValeMandarBlanco() {
        assertThatThrownBy(() -> guardiaConPin("   ").exigirAdministrador(Rol.ADMINISTRADOR, "   "))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void elCodigoDelErrorEsEstableParaLaUi() {
        assertThatThrownBy(() -> guardia().exigir(Rol.RESIDENTE, Rol.VIGILANTE))
                .isInstanceOf(AccesoDenegadoException.class)
                .extracting(e -> ((AccesoDenegadoException) e).codigo())
                .isEqualTo("ACCESO_DENEGADO");
    }
}
