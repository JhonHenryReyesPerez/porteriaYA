package co.porteriaya.solicitud.controller;

import java.util.List;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import co.porteriaya.solicitud.dto.AccionAprobacion;
import co.porteriaya.solicitud.dto.RespuestaSolicitud;
import co.porteriaya.solicitud.dto.SolicitudNoAnunciadaRequest;
import co.porteriaya.solicitud.entity.EstadoSolicitud;
import co.porteriaya.solicitud.service.SolicitudService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Visitas no anunciadas desde el lado de la porteria (§8). */
@RestController
@RequestMapping("/api/porteria/solicitudes")
@RequiredArgsConstructor
@Tag(name = "Visitas no anunciadas", description = "Registro y decision de visitas sin anunciar")
public class SolicitudController {

    private final SolicitudService servicio;
    private final GuardiaRoles guardia;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una visita no anunciada",
            description = """
                    El vigilante la registra y al residente le llega un enlace para aprobar
                    o rechazar. NO se crea ningun ingreso aqui: hasta que haya aprobacion,
                    esta persona no esta dentro ni aparece en GET /api/porteria/dentro.

                    El plazo de respuesta sale de porteria.aprobacion.timeout-minutos. Si se
                    agota, el scheduler pasa la solicitud a SIN_RESPUESTA y la decision
                    queda en manos del vigilante.
                    """)
    public RespuestaSolicitud crear(@Valid @RequestBody SolicitudNoAnunciadaRequest peticion,
                                    @RequestHeader("X-Rol") Rol rol,
                                    @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE);
        return servicio.crear(peticion, actor);
    }

    @GetMapping
    @Operation(summary = "Listar solicitudes por estado",
            description = "Por defecto las PENDIENTE, que son las que el vigilante tiene en pantalla.")
    public List<RespuestaSolicitud> listar(
            @RequestParam(defaultValue = "PENDIENTE") EstadoSolicitud estado,
            @RequestHeader("X-Rol") Rol rol) {
        guardia.exigir(rol, Rol.VIGILANTE, Rol.ADMINISTRADOR);
        return servicio.listarPorEstado(estado);
    }

    @PatchMapping("/{id}/decision")
    @Operation(summary = "Decision del vigilante tras el silencio del residente",
            description = """
                    Solo cabe desde SIN_RESPUESTA. Si el residente ya rechazo, el mapa de
                    transiciones lo impide y la respuesta es 409: el vigilante no puede dar
                    la vuelta a un rechazo (§6).

                    Reemplaza a PATCH /api/porteria/ingresos/{id}/decision del contrato
                    original, porque la solicitud es entidad propia y el ingreso todavia no
                    existe cuando se decide.
                    """)
    public RespuestaSolicitud decidir(@PathVariable Long id,
                                      @RequestParam AccionAprobacion accion,
                                      @RequestHeader("X-Rol") Rol rol,
                                      @RequestHeader("X-Actor") String actor) {
        guardia.exigir(rol, Rol.VIGILANTE);
        return servicio.decidirPorVigilante(id, accion, actor);
    }
}
