package co.porteriaya.paquete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import co.porteriaya.ColectorDeEventos;
import co.porteriaya.IntegracionBase;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.paquete.dto.EntregarPaqueteRequest;
import co.porteriaya.paquete.dto.RecibirPaqueteRequest;
import co.porteriaya.paquete.entity.AutorizadoRetiro;
import co.porteriaya.paquete.repository.AutorizadoRetiroRepository;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Paquetes por HTTP: recepcion, las dos vias de retiro y el filtro por rol. */
@AutoConfigureMockMvc
class PaqueteIT extends IntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ResidenteRepository residentes;
    @Autowired
    private AutorizadoRetiroRepository autorizados;
    @Autowired
    private ColectorDeEventos eventos;
    @Autowired
    private Clock clock;

    private Residente residente;
    private String apartamento;

    @BeforeEach
    void prepararApartamento() {
        eventos.limpiar();
        int n = SECUENCIA.incrementAndGet();
        residente = residentes.save(new Residente(
                "PQ", "P" + n, "Residente de prueba " + n,
                "pq" + n + "@example.test", null, clock.instant()));
        apartamento = residente.identificador();
    }

    // ------------------------------------------------------------ utilidades

    private JsonNode recibir() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/paquetes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(new RecibirPaqueteRequest(
                                apartamento, "Caja mediana", "Tienda en linea"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private String codigoDelEvento() {
        return eventos.dato(TipoEvento.PAQUETE_RECIBIDO, "codigoRetiro");
    }

    // --------------------------------------------------------------- recepcion

    @Test
    void recibirUnPaqueteLoDejaEnEstadoRecibidoSinAvisos() throws Exception {
        JsonNode paquete = recibir();

        assertThat(paquete.get("estado").asText()).isEqualTo("RECIBIDO");
        assertThat(paquete.get("apartamento").asText()).isEqualTo(apartamento);
        assertThat(paquete.get("aviso24h").asBoolean()).isFalse();
        assertThat(paquete.get("escalado").asBoolean()).isFalse();
        // El codigo de retiro NO viaja en la respuesta de porteria
        assertThat(paquete.get("codigoRetiro")).isNull();
    }

    @Test
    void elCodigoDeRetiroSoloViajaEnElEventoHaciaElResidente() throws Exception {
        recibir();

        assertThat(codigoDelEvento()).matches("[0-9]{6}");
        assertThat(eventos.dato(TipoEvento.PAQUETE_RECIBIDO, "correoResidente"))
                .isEqualTo(residente.getCorreo());
    }

    @Test
    void unApartamentoQueNoExisteEs404() throws Exception {
        mockMvc.perform(post("/api/paquetes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(new RecibirPaqueteRequest(
                                "ZZ-999", "Caja", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unApartamentoInactivoNoRecibePaquetes() throws Exception {
        residente.desactivar();
        residentes.save(residente);

        // §6: mejor rechazarlo en la puerta que acumular paquetes de nadie
        mockMvc.perform(post("/api/paquetes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(new RecibirPaqueteRequest(
                                apartamento, "Caja", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    // ------------------------------------------------- retiro con el codigo

    @Test
    void conElCodigoCorrectoSeEntrega() throws Exception {
        long id = recibir().get("id").asLong();

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(codigoDelEvento(), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.entregadoEn").exists());
    }

    @Test
    void unCodigoQueNoCorrespondeEs409() throws Exception {
        long id = recibir().get("id").asLong();
        String otroCodigo = codigoDelEvento().equals("000000") ? "111111" : "000000";

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(otroCodigo, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CODIGO_RETIRO_INVALIDO"));
    }

    @Test
    void entregarDosVecesElMismoPaqueteEs409() throws Exception {
        long id = recibir().get("id").asLong();
        String codigo = codigoDelEvento();

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(codigo, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(codigo, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    // -------------------------------------- retiro por persona autorizada

    @Test
    void unaPersonaAutorizadaPuedeRetirarSinElCodigo() throws Exception {
        autorizados.save(new AutorizadoRetiro(residente, "Gloria Ramirez", "8812", clock.instant()));
        long id = recibir().get("id").asLong();

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                // Con acentos y mayusculas distintas, como lo teclearia el vigilante
                                new EntregarPaqueteRequest(null, "gloria ramírez", "8812"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.entregadoA").value("Gloria Ramirez"));
    }

    @Test
    void quienNoEstaAutorizadoNoRetiraNada() throws Exception {
        long id = recibir().get("id").asLong();

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(null, "Un Desconocido", "0000"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO_A_RETIRAR"));
    }

    @Test
    void unAutorizadoDesactivadoYaNoPuedeRetirar() throws Exception {
        AutorizadoRetiro autorizado = autorizados.save(
                new AutorizadoRetiro(residente, "Gloria Ramirez", "8812", clock.instant()));
        autorizado.desactivar();
        autorizados.save(autorizado);
        long id = recibir().get("id").asLong();

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(null, "Gloria Ramirez", "8812"))))
                .andExpect(status().isConflict());
    }

    @Test
    void siElDocumentoNoCoincideTampocoRetira() throws Exception {
        autorizados.save(new AutorizadoRetiro(residente, "Gloria Ramirez", "8812", clock.instant()));
        long id = recibir().get("id").asLong();

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(null, "Gloria Ramirez", "0000"))))
                .andExpect(status().isConflict());
    }

    @Test
    void sinCodigoNiPersonaNoSePuedeEntregar() throws Exception {
        long id = recibir().get("id").asLong();

        mockMvc.perform(post("/api/paquetes/{id}/entregar", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new EntregarPaqueteRequest(null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    // ------------------------------------------------------------- listado

    @Test
    void unResidenteSoloVeLosPaquetesDeSuApartamento() throws Exception {
        recibir();

        // Otro apartamento con su propio paquete
        Residente vecino = residentes.save(new Residente(
                "PQ", "V" + SECUENCIA.incrementAndGet(), "Vecino", null, null, clock.instant()));
        mockMvc.perform(post("/api/paquetes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(new RecibirPaqueteRequest(
                                vecino.identificador(), "Caja del vecino", null))))
                .andExpect(status().isCreated());

        // Aunque pida el apartamento del vecino, solo ve el suyo
        mockMvc.perform(get("/api/paquetes")
                        .param("apto", vecino.identificador())
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].apartamento").value(apartamento));
    }

    @Test
    void elVigilantePuedeFiltrarPorEstado() throws Exception {
        recibir();

        mockMvc.perform(get("/api/paquetes")
                        .param("estado", "RECIBIDO")
                        .param("apto", apartamento)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/paquetes")
                        .param("estado", "ENTREGADO")
                        .param("apto", apartamento)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
