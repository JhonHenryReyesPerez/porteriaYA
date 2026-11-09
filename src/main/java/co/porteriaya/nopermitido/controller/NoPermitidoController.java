package co.porteriaya.nopermitido.controller;

import java.util.List;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import co.porteriaya.nopermitido.dto.NoPermitidoRequest;
import co.porteriaya.nopermitido.dto.RespuestaNoPermitido;
import co.porteriaya.nopermitido.service.NoPermitidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista de personas no permitidas (§8).
 *
 * Todo exige rol de administrador Y el PIN: es la operacion con mas capacidad
 * de hacer dano de la aplicacion, porque meter a alguien en esta lista le
 * impide entrar al conjunto.
 */
@RestController
@RequestMapping("/api/no-permitidos")
@RequiredArgsConstructor
@Tag(name = "No permitidos", description = "Lista de personas a las que no se permite el ingreso")
public class NoPermitidoController {

    private final NoPermitidoService servicio;
    private final GuardiaRoles guardia;

    @GetMapping
    @Operation(summary = "Listar la lista completa",
            description = "Incluye los registros desactivados, que quedan como historial.")
    public List<RespuestaNoPermitido> listar(
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {
        guardia.exigirAdministrador(rol, pin);
        return servicio.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anadir una persona a la lista",
            description = """
                    El cotejo en porteria tiene dos niveles (§6): si se registra con doc4,
                    una coincidencia de nombre Y documento niega el ingreso. Sin doc4, solo
                    puede avisar al vigilante, nunca negar automaticamente, asi que conviene
                    registrarlo siempre que se conozca.
                    """)
    public RespuestaNoPermitido registrar(
            @Valid @RequestBody NoPermitidoRequest peticion,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {
        guardia.exigirAdministrador(rol, pin);
        return servicio.registrar(peticion, actor);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Retirar a una persona de la lista",
            description = """
                    Desactivacion logica, no borrado: §12 lo prohibe, y aqui ademas interesa
                    poder responder a quien retiro a alguien de la lista y cuando.
                    """)
    public RespuestaNoPermitido desactivar(
            @PathVariable Long id,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {
        guardia.exigirAdministrador(rol, pin);
        return servicio.desactivar(id, actor);
    }
}
