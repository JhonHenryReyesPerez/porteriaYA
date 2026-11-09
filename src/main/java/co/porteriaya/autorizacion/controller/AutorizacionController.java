package co.porteriaya.autorizacion.controller;

import java.util.List;

import co.porteriaya.autorizacion.dto.RespuestaAutorizacion;
import co.porteriaya.autorizacion.dto.SolicitudAutorizacion;
import co.porteriaya.autorizacion.service.AutorizacionService;
import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
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

/** Controller delgado: valida la entrada, comprueba el rol y delega (§5). */
@RestController
@RequestMapping("/api/autorizaciones")
@RequiredArgsConstructor
@Tag(name = "Autorizaciones", description = "Alta, consulta y cancelacion de autorizaciones de ingreso")
public class AutorizacionController {

    private final AutorizacionService servicio;
    private final GuardiaRoles guardia;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una autorizacion",
            description = """
                    El apartamento sale de X-Actor (formato torre-apartamento, p. ej. A-101).
                    Devuelve el codigo de 6 digitos que el residente comparte con su visitante.

                    Segun el tipo: VISITA admite usosMax (1 por defecto), DOMICILIARIO es
                    siempre de un uso, y SERVICIO_RECURRENTE y MUDANZA exigen diasSemana mas
                    franja horaria y tienen usos ilimitados dentro de ella.
                    """)
    public RespuestaAutorizacion crear(@Valid @RequestBody SolicitudAutorizacion solicitud,
                                       @RequestHeader("X-Rol") Rol rol,
                                       @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.RESIDENTE);
        return servicio.crear(solicitud, actor);
    }

    @GetMapping
    @Operation(summary = "Listar mis autorizaciones",
            description = "Las del apartamento que viaja en X-Actor, de la mas reciente a la mas antigua.")
    public List<RespuestaAutorizacion> listar(@RequestHeader("X-Rol") Rol rol,
                                              @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.RESIDENTE);
        return servicio.listarDe(actor);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar una autorizacion",
            description = """
                    Cancelacion logica: pasa a CANCELADA y la fila se conserva; no hay
                    borrados fisicos (§12). Un residente solo puede cancelar las de su
                    apartamento. El administrador puede cancelar cualquiera, con X-Admin-Pin.
                    Cancelar una ya cerrada responde 409.
                    """)
    public RespuestaAutorizacion cancelar(@PathVariable Long id,
                                          @RequestHeader("X-Rol") Rol rol,
                                          @RequestHeader("X-Actor") String actor,
                                          @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {
        if (rol == Rol.ADMINISTRADOR) {
            guardia.exigirAdministrador(rol, pin);
        } else {
            guardia.exigir(rol, Rol.RESIDENTE);
        }
        return servicio.cancelar(id, actor, rol);
    }
}
