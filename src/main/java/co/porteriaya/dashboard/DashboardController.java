package co.porteriaya.dashboard;

import co.porteriaya.common.GuardiaRoles;
import co.porteriaya.common.Rol;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Metricas agregadas para el panel de administracion")
public class DashboardController {

    private final DashboardService servicio;
    private final GuardiaRoles guardia;

    @GetMapping("/stats")
    @Operation(summary = "Metricas del panel",
            description = """
                    Solo datos agregados: cuentas y totales, nunca nombres de visitantes ni
                    documentos. Un panel de metricas no necesita saber quien, solo cuantos.

                    visitasPorHoraHoy son 24 posiciones, de la hora 0 a la 23 en
                    America/Bogota. Los tramos de paquetesPorAntiguedad coinciden con los
                    hitos de aviso, asi que un paquete en «mas de 7 dias» es exactamente uno
                    que ya se escalo a administracion.

                    Lo consultan administrador y vigilante: el vigilante necesita ver
                    cuantos paquetes pendientes y cuanta gente dentro tiene en su turno.
                    """)
    public EstadisticasDashboard stats(@RequestHeader("X-Rol") Rol rol) {
        guardia.exigir(rol, Rol.ADMINISTRADOR, Rol.VIGILANTE);
        return servicio.calcular();
    }
}
