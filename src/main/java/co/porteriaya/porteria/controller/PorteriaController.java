package co.porteriaya.porteria.controller;

import java.util.List;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import co.porteriaya.common.TransicionInvalidaException;
import co.porteriaya.porteria.dto.RespuestaIngreso;
import co.porteriaya.porteria.dto.RespuestaValidacion;
import co.porteriaya.porteria.dto.ResultadoIngreso;
import co.porteriaya.porteria.dto.SolicitudIngreso;
import co.porteriaya.porteria.dto.SolicitudValidacion;
import co.porteriaya.porteria.service.IngresoService;
import co.porteriaya.porteria.service.ValidacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/porteria")
@RequiredArgsConstructor
@Tag(name = "Porteria", description = "Operaciones del vigilante en la entrada")
public class PorteriaController {

    private final ValidacionService servicio;
    private final IngresoService ingresos;
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

    @PostMapping("/ingresos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un ingreso",
            description = """
                    Vuelve a recorrer los 5 pasos y esta vez SI escribe: descuenta el uso
                    con una sola sentencia atomica. Si dos vigilantes registran el mismo
                    codigo de un solo uso a la vez, solo uno lo consigue; el otro recibe
                    409 USOS_AGOTADOS.

                    Si la validacion devolvio advertencias, hay que repetirlas aceptadas con
                    confirmaAdvertencias=true. Sin eso la respuesta es 409
                    ADVERTENCIAS_SIN_CONFIRMAR, para que nadie se salte el aviso llamando
                    directo a este endpoint.

                    Un rechazo responde 409 con el motivo en el campo `codigo`.
                    """)
    public RespuestaIngreso registrarIngreso(@Valid @RequestBody SolicitudIngreso solicitud,
                                             @RequestHeader("X-Rol") Rol rol,
                                             @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE);

        ResultadoIngreso resultado = ingresos.registrar(solicitud, actor);

        // El rechazo se traduce aqui, con la transaccion del service ya
        // confirmada: asi la bitacora del intento y la alerta de seguridad
        // sobreviven al 409 (ver ResultadoIngreso).
        if (resultado instanceof ResultadoIngreso.Rechazado rechazo) {
            throw new TransicionInvalidaException(rechazo.codigo(), rechazo.mensaje());
        }
        return ((ResultadoIngreso.Registrado) resultado).ingreso();
    }

    @PatchMapping("/ingresos/{id}/salida")
    @Operation(summary = "Registrar la salida de un ingreso",
            description = "Cerrar un ingreso ya cerrado responde 409: no hay 200 silenciosos.")
    public RespuestaIngreso registrarSalida(@PathVariable Long id,
                                            @RequestHeader("X-Rol") Rol rol,
                                            @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE);
        return ingresos.registrarSalida(id, actor);
    }

    @GetMapping("/dentro")
    @Operation(summary = "Quien esta dentro ahora mismo",
            description = "Ingresos en estado DENTRO, del mas antiguo al mas reciente.")
    public List<RespuestaIngreso> dentro(@RequestHeader("X-Rol") Rol rol) {
        guardia.exigir(rol, Rol.VIGILANTE, Rol.ADMINISTRADOR);
        return ingresos.listarDentro();
    }
}
