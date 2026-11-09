package co.porteriaya.demo;

import java.time.Duration;
import java.time.Instant;

import co.porteriaya.config.RelojDemo;
import co.porteriaya.config.TareasProgramadas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adelanta el reloj simulado (§8, §9). Solo existe en el perfil `demo`: en dev
 * o en produccion este bean no se crea y la ruta devuelve 404.
 */
@RestController
@RequestMapping("/api/demo")
@Profile("demo")
@Validated
@RequiredArgsConstructor
@Tag(name = "Demo", description = "Control del reloj simulado. Solo en perfil demo")
public class DemoController {

    /** Tope de seguridad: un ano de un tiron ya es un dedo resbalado, no una demo. */
    private static final long MAXIMO_MINUTOS = 525_600;

    private final RelojDemo reloj;
    private final TareasProgramadas tareas;

    public record RespuestaAvance(
            Instant ahora,
            long desfaseTotalMinutos,
            TareasProgramadas.ResumenPasada efectos
    ) {
    }

    @PostMapping("/avanzar")
    @Operation(summary = "Adelantar el reloj simulado",
            description = """
                    Mueve el reloj y acto seguido ejecuta una pasada completa del
                    scheduler, devolviendo lo que provoco: autorizaciones vencidas,
                    solicitudes sin respuesta, permanencias avisadas, ingresos cerrados y
                    avisos de paquetes.

                    Ejecutar la pasada aqui no es un atajo: el disparo de @Scheduled sigue
                    el reloj real del sistema, asi que adelantar el reloj simulado 24 horas
                    no lo dispara 1440 veces. Sin esta llamada, la demo tendria que esperar
                    hasta un minuto para ver cualquier efecto. Como las tareas son
                    idempotentes, ejecutarlas de mas es inofensivo.

                    El reloj nunca retrocede.
                    """)
    public RespuestaAvance avanzar(
            @RequestParam @Positive(message = "Los minutos deben ser positivos")
            @Max(value = MAXIMO_MINUTOS, message = "Demasiado: el tope es un ano") long minutos) {

        Duration desfase = reloj.avanzar(Duration.ofMinutes(minutos));
        TareasProgramadas.ResumenPasada efectos = tareas.ejecutarPasada();

        return new RespuestaAvance(
                reloj.instant(), desfase.toMinutes(), efectos);
    }
}
