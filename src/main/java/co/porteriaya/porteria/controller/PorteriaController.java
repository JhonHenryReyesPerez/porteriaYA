package co.porteriaya.porteria.controller;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import co.porteriaya.porteria.dto.RespuestaValidacion;
import co.porteriaya.porteria.dto.SolicitudValidacion;
import co.porteriaya.porteria.service.ValidacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/porteria")
@RequiredArgsConstructor
@Tag(name = "Porteria", description = "Operaciones del vigilante en la entrada")
public class PorteriaController {

    private final ValidacionService servicio;
    private final GuardiaRoles guardia;

    @PostMapping("/validar")
    @Operation(summary = "Validar un codigo",
            description = """
                    Recorre los 5 pasos del contrato en orden y NO consume el uso: validar
                    es idempotente, asi que consultar un codigo no lo gasta. El descuento
                    atomico ocurre al registrar el ingreso.

                    Interpretar la respuesta:
                    - permitido=false con motivo: no se puede registrar el ingreso.
                    - permitido=true con advertencias: se puede, pero hay que confirmarlas
                      al registrar el ingreso (p. ej. POSIBLE_NO_PERMITIDO).
                    - permitido=true sin advertencias: adelante.
                    """)
    public RespuestaValidacion validar(@Valid @RequestBody SolicitudValidacion solicitud,
                                       @RequestHeader("X-Rol") Rol rol,
                                       @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE);
        return servicio.validar(solicitud, actor);
    }
}
