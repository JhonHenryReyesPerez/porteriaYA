package co.porteriaya.bitacora.controller;

import java.time.Instant;
import java.util.List;

import co.porteriaya.bitacora.dto.RespuestaBitacora;
import co.porteriaya.bitacora.repository.BitacoraRepository;
import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la bitacora de auditoria (§8).
 *
 * Solo GET, y eso es deliberado: §6 exige que la bitacora no se pueda editar ni
 * borrar desde la API. No hay POST, PUT, PATCH ni DELETE aqui, y la entidad
 * tampoco tiene setters publicos, asi que la restriccion no depende de que
 * nadie se acuerde de respetarla.
 */
@RestController
@RequestMapping("/api/bitacora")
@RequiredArgsConstructor
@Validated
@Tag(name = "Bitacora", description = "Auditoria. Solo lectura, por contrato")
public class BitacoraController {

    private static final int LIMITE_POR_DEFECTO = 100;
    private static final int LIMITE_MAXIMO = 500;

    private final BitacoraRepository repositorio;
    private final GuardiaRoles guardia;

    @GetMapping
    @Operation(summary = "Consultar la bitacora",
            description = """
                    Filtros opcionales por rango de fechas, actor y entidad. De la entrada
                    mas reciente a la mas antigua.

                    Hay limite y tope porque la bitacora solo crece: pedirla entera con
                    datos de varios meses tumbaria la respuesta.
                    """)
    public List<RespuestaBitacora> consultar(
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String entidad,
            @RequestParam(defaultValue = "" + LIMITE_POR_DEFECTO)
            @Positive(message = "El limite debe ser positivo")
            @Max(value = LIMITE_MAXIMO, message = "El limite maximo es 500") int limite,
            @RequestHeader("X-Rol") Rol rol,
            @RequestHeader(value = "X-Admin-Pin", required = false) String pin) {

        guardia.exigirAdministrador(rol, pin);

        return repositorio.buscar(desde, hasta, actor, entidad, PageRequest.of(0, limite)).stream()
                .map(RespuestaBitacora::de)
                .toList();
    }
}
