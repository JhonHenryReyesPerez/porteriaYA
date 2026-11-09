package co.porteriaya.evento;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;

import co.porteriaya.config.N8nClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Lo que §18 exige demostrar: que si n8n no responde, la porteria no se entera.
 *
 * El listener corre despues del commit, asi que cuando estos metodos se
 * ejecutan el registro ya esta guardado. Lo unico que puede hacer un fallo aqui
 * es quedarse en el log, y eso es justo lo que se comprueba.
 */
class N8nEventoListenerTest {

    private static final String URL = "http://n8n.de-prueba/webhook/porteriaya";
    private static final String SECRETO = "secreto-de-prueba";

    private EventoPorteria eventoDeEjemplo() {
        return new EventoPorteria(
                TipoEvento.INGRESO_REGISTRADO,
                Instant.parse("2026-11-08T17:00:00Z"),
                EventoPorteria.datos(
                        "apartamento", "A-101",
                        "visitanteNombre", "Camilo Rojas",
                        "vigilante", "vigilante1"));
    }

    @Test
    void entregaElEventoConSuSecretoYSuPayload() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        RestClient cliente = constructor
                .baseUrl(URL)
                .defaultHeader(N8nClientConfig.CABECERA_SECRETO, SECRETO)
                .build();

        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(N8nClientConfig.CABECERA_SECRETO, SECRETO))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tipo").value("INGRESO_REGISTRADO"))
                .andExpect(jsonPath("$.ocurridoEn").value("2026-11-08T17:00:00Z"))
                .andExpect(jsonPath("$.datos.apartamento").value("A-101"))
                .andExpect(jsonPath("$.datos.visitanteNombre").value("Camilo Rojas"))
                .andRespond(withSuccess());

        new N8nEventoListener(cliente).entregar(eventoDeEjemplo());

        servidor.verify();
    }

    @Test
    void siN8nResponde500ElFalloSeQuedaEnElLog() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        RestClient cliente = constructor.baseUrl(URL).build();

        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertThatCode(() -> new N8nEventoListener(cliente).entregar(eventoDeEjemplo()))
                .doesNotThrowAnyException();
    }

    @Test
    void siN8nEstaCaidoElFalloSeQuedaEnElLog() {
        // Puerto 1: nada escucha ahi, la conexion se rechaza al instante
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(2));
        RestClient cliente = RestClient.builder()
                .baseUrl("http://127.0.0.1:1/webhook/porteriaya")
                .requestFactory(fabrica)
                .build();

        assertThatCode(() -> new N8nEventoListener(cliente).entregar(eventoDeEjemplo()))
                .doesNotThrowAnyException();
    }

    @Test
    void elPayloadNoArrastraValoresNulos() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        RestClient cliente = constructor.baseUrl(URL).build();

        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.datos.apartamento").value("A-101"))
                // El residente no tiene correo registrado: la clave no viaja
                .andExpect(jsonPath("$.datos.correoResidente").doesNotExist())
                .andRespond(withSuccess());

        EventoPorteria evento = new EventoPorteria(
                TipoEvento.AUTORIZACION_CREADA,
                Instant.parse("2026-11-08T17:00:00Z"),
                EventoPorteria.datos("apartamento", "A-101", "correoResidente", null));

        new N8nEventoListener(cliente).entregar(evento);

        servidor.verify();
    }
}
