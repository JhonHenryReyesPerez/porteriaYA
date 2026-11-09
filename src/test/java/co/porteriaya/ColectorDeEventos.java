package co.porteriaya;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import co.porteriaya.evento.EventoPorteria;
import co.porteriaya.evento.TipoEvento;
import org.springframework.context.event.EventListener;

/**
 * Captura los eventos que el dominio publica, para poder afirmar sobre ellos
 * en las pruebas de integracion.
 *
 * Usa @EventListener a secas, no @TransactionalEventListener: asi recoge el
 * evento en el momento de publicarse, sin depender del commit. Eso es
 * deliberado, porque algunas pruebas necesitan leer el payload (por ejemplo el
 * token en claro de una aprobacion, que no esta en la base: alli solo hay su
 * hash).
 */
public class ColectorDeEventos {

    private final List<EventoPorteria> eventos = new CopyOnWriteArrayList<>();

    @EventListener
    public void capturar(EventoPorteria evento) {
        eventos.add(evento);
    }

    public void limpiar() {
        eventos.clear();
    }

    public List<EventoPorteria> todos() {
        return List.copyOf(eventos);
    }

    public List<EventoPorteria> de(TipoEvento tipo) {
        return eventos.stream().filter(e -> e.tipo() == tipo).toList();
    }

    public Optional<EventoPorteria> ultimoDe(TipoEvento tipo) {
        List<EventoPorteria> encontrados = de(tipo);
        return encontrados.isEmpty()
                ? Optional.empty()
                : Optional.of(encontrados.get(encontrados.size() - 1));
    }

    /** Un dato del ultimo evento de ese tipo, como texto. */
    public String dato(TipoEvento tipo, String clave) {
        return ultimoDe(tipo)
                .map(e -> e.datos().get(clave))
                .map(String::valueOf)
                .orElseThrow(() -> new AssertionError(
                        "No hay evento %s con el dato %s".formatted(tipo, clave)));
    }
}
