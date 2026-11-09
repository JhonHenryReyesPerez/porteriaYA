package co.porteriaya.evento;

import java.time.Clock;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Punto unico por el que el dominio emite eventos (§10).
 *
 * Publicar aqui es sincrono y baratisimo: solo deja el evento en el contexto de
 * Spring. Quien habla con n8n es {@link N8nEventoListener}, y lo hace despues
 * de que la transaccion haga commit.
 *
 * Ese orden es la razon de existir de esta separacion. Si el envio ocurriera
 * dentro de la transaccion, un rollback posterior dejaria a un residente con un
 * correo de un ingreso que nunca se registro. Asi el aviso solo sale cuando el
 * hecho ya es cierto, y si n8n esta caido el registro de porteria se guarda
 * igual (§10).
 */
@Component
@RequiredArgsConstructor
public class EventoPublisher {

    private final ApplicationEventPublisher publicador;
    private final Clock clock;

    public void publicar(TipoEvento tipo, Map<String, Object> datos) {
        publicador.publishEvent(new EventoPorteria(tipo, clock.instant(), datos));
    }
}
