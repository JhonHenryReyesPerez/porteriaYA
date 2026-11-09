package co.porteriaya.evento;

import java.util.LinkedHashMap;
import java.util.Map;

import co.porteriaya.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

/**
 * Entrega los eventos al webhook de n8n (§10).
 *
 * - AFTER_COMMIT: el aviso sale cuando el hecho ya esta confirmado en la base.
 * - fallbackExecution: los eventos del scheduler pueden nacer sin transaccion
 *   alrededor, y tambien tienen que salir.
 * - @Async: la peticion HTTP no bloquea a quien registro el ingreso.
 * - try/catch que se lo traga todo: si n8n esta caido, el registro de porteria
 *   ya esta guardado y el fallo solo queda en logs. La porteria no se detiene
 *   porque una automatizacion no responda.
 */
@Component
@RequiredArgsConstructor
public class N8nEventoListener {

    private static final Logger log = LoggerFactory.getLogger(N8nEventoListener.class);

    private final RestClient clienteN8n;

    @Async(AsyncConfig.EJECUTOR_EVENTOS)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void entregar(EventoPorteria evento) {
        try {
            clienteN8n.post()
                    .body(cuerpo(evento))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Evento {} entregado a n8n", evento.tipo());
        } catch (Exception e) {
            // Deliberadamente no se reintenta ni se propaga: el evento es un
            // aviso, no parte del registro. Lo que importa ya esta en la base.
            log.warn("No se pudo entregar el evento {} a n8n: {}", evento.tipo(), e.getMessage());
        }
    }

    private Map<String, Object> cuerpo(EventoPorteria evento) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", evento.tipo().name());
        cuerpo.put("ocurridoEn", evento.ocurridoEn().toString());
        cuerpo.put("datos", evento.datos());
        return cuerpo;
    }
}
