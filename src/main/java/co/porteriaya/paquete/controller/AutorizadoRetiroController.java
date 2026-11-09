package co.porteriaya.paquete.controller;

import java.util.List;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import co.porteriaya.paquete.dto.AutorizadoRetiroRequest;
import co.porteriaya.paquete.dto.RespuestaAutorizadoRetiro;
import co.porteriaya.paquete.service.AutorizadoRetiroService;
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
 * Personas autorizadas a retirar los paquetes de un apartamento (§8).
 *
 * La ruta cuelga de /api/residentes porque asi lo fija el contrato, pero el
 * codigo vive en el dominio de paquetes, que es el dueno de la entidad y el
 * unico que la usa (§4, paquete por feature).
 *
 * Desviacion anotada: el contrato escribe el DELETE sobre la coleccion
 * (DELETE /api/residentes/{id}/autorizados-retiro), y asi no hay forma de
 * decir a QUIEN se retira. Se expone sobre el recurso concreto, igual que el
 * PATCH de decision de solicitudes se movio a su propia entidad.
 */
@RestController
@RequestMapping("/api/residentes/{id}/autorizados-retiro")
@RequiredArgsConstructor
@Tag(name = "Autorizados a retirar", description = "Quien puede recoger paquetes sin el codigo")
public class AutorizadoRetiroController {

    private final AutorizadoRetiroService servicio;
    private final GuardiaRoles guardia;

    @GetMapping
    @Operation(summary = "Listar los autorizados activos de un apartamento",
            description = """
                    El residente solo ve los de su propio apartamento: el id de la ruta se
                    compara contra X-Actor y un cruce responde 403. El vigilante y la
                    administracion ven cualquiera, porque lo necesitan en la puerta para
                    acreditar un retiro sin codigo.

                    Los desactivados no salen. Quien los dio de alta o de baja, y cuando,
                    esta en la bitacora.
                    """)
    public List<RespuestaAutorizadoRetiro> listar(
            @PathVariable Long id,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.RESIDENTE, Rol.VIGILANTE, Rol.ADMINISTRADOR);
        return servicio.listar(id, rol, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Autorizar a una persona a retirar",
            description = """
                    Sin esto, la segunda via de retiro de §6 («o por una persona registrada
                    como autorizada») no tenia como alimentarse.

                    El doc4 es opcional, pero conviene: el cotejo del retiro compara el
                    nombre normalizado, y sin documento dos homonimos acreditan igual.

                    Dos registros que normalizan al mismo nombre y mismo doc4 son la misma
                    persona escrita de otra forma: el segundo responde 409
                    AUTORIZADO_REPETIDO en vez de dejar la lista ambigua.
                    """)
    public RespuestaAutorizadoRetiro registrar(
            @PathVariable Long id,
            @Valid @RequestBody AutorizadoRetiroRequest peticion,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.RESIDENTE, Rol.ADMINISTRADOR);
        return servicio.registrar(id, peticion, rol, actor);
    }

    @DeleteMapping("/{autorizadoId}")
    @Operation(summary = "Retirar la autorizacion a una persona",
            description = """
                    Desactivacion logica, no borrado (§12): interesa poder responder a
                    quien dejo de poder retirar y desde cuando.

                    Desactivar dos veces es 409, igual que repetir cualquier otro cambio de
                    estado. Un autorizado de otro apartamento responde 404, no 403: desde
                    fuera no debe poder distinguirse si ese id existe.
                    """)
    public RespuestaAutorizadoRetiro desactivar(
            @PathVariable Long id,
            @PathVariable Long autorizadoId,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.RESIDENTE, Rol.ADMINISTRADOR);
        return servicio.desactivar(id, autorizadoId, rol, actor);
    }
}
