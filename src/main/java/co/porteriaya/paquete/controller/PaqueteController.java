package co.porteriaya.paquete.controller;

import java.util.List;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import co.porteriaya.paquete.dto.EntregarPaqueteRequest;
import co.porteriaya.paquete.dto.RecibirPaqueteRequest;
import co.porteriaya.paquete.dto.RespuestaPaquete;
import co.porteriaya.paquete.entity.EstadoPaquete;
import co.porteriaya.paquete.service.PaqueteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paquetes")
@RequiredArgsConstructor
@Tag(name = "Paquetes", description = "Recepcion, entrega y seguimiento de paquetes")
public class PaqueteController {

    private final PaqueteService servicio;
    private final GuardiaRoles guardia;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recibir un paquete",
            description = """
                    Genera el codigo de retiro que le llega al residente por correo. Un
                    apartamento inexistente o inactivo NO se registra (§6): responde 404 o
                    400 respectivamente, porque es mejor rechazarlo en la puerta que
                    acumular paquetes de nadie.

                    El codigo de retiro no aparece en la respuesta ni en el listado: viaja
                    solo al residente, y es lo que acredita que puede recogerlo.
                    """)
    public RespuestaPaquete recibir(@Valid @RequestBody RecibirPaqueteRequest peticion,
                                    @RequestHeader("X-Rol") Rol rol,
                                    @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE);
        return servicio.recibir(peticion, actor);
    }

    @PostMapping("/{id}/entregar")
    @Operation(summary = "Entregar un paquete",
            description = """
                    Dos vias, y hace falta al menos una: el codigo del correo, o el nombre
                    de una persona que el residente registro como autorizada a retirar.

                    Codigo que no corresponde: 409 CODIGO_RETIRO_INVALIDO.
                    Persona que no esta en la lista: 409 NO_AUTORIZADO_A_RETIRAR.
                    Paquete ya entregado: 409 TRANSICION_INVALIDA.
                    """)
    public RespuestaPaquete entregar(@PathVariable Long id,
                                     @Valid @RequestBody EntregarPaqueteRequest peticion,
                                     @RequestHeader("X-Rol") Rol rol,
                                     @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE);
        return servicio.entregar(id, peticion, actor);
    }

    @GetMapping
    @Operation(summary = "Listar paquetes",
            description = """
                    Filtros opcionales por estado y por apartamento (formato torre-apartamento).

                    Un residente solo ve los paquetes de SU apartamento: el filtro se
                    sobrescribe con X-Actor y se ignora el que haya pedido. Quien recibe que
                    cosa es un dato de sus vecinos, no suyo.
                    """)
    public List<RespuestaPaquete> listar(@RequestParam(required = false) EstadoPaquete estado,
                                         @RequestParam(required = false) String apto,
                                         @RequestHeader("X-Rol") Rol rol,
                                         @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE, Rol.ADMINISTRADOR, Rol.RESIDENTE);

        String filtro = rol == Rol.RESIDENTE ? actor : apto;
        return servicio.listar(estado, filtro);
    }
}
