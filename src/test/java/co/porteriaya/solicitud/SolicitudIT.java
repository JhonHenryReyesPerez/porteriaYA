package co.porteriaya.solicitud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import co.porteriaya.ColectorDeEventos;
import co.porteriaya.IntegracionBase;
import co.porteriaya.evento.TipoEvento;
import co.porteriaya.porteria.repository.IngresoRepository;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import co.porteriaya.solicitud.dto.SolicitudNoAnunciadaRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Aprobacion en vivo de punta a punta (§6, §15).
 *
 * El token en claro no esta en la base, solo su hash, asi que se saca del
 * payload del evento VISITA_NO_ANUNCIADA, que es exactamente por donde le
 * llega al residente.
 */
@AutoConfigureMockMvc
class SolicitudIT extends IntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ResidenteRepository residentes;
    @Autowired
    private IngresoRepository ingresos;
    @Autowired
    private ColectorDeEventos eventos;
    @Autowired
    private Clock clock;

    private String apartamento;

    @BeforeEach
    void prepararApartamento() {
        eventos.limpiar();
        int n = SECUENCIA.incrementAndGet();
        Residente residente = residentes.save(new Residente(
                "SO", "S" + n, "Residente de prueba " + n, null, null, clock.instant()));
        apartamento = residente.identificador();
    }

    // ------------------------------------------------------------ utilidades

    private JsonNode crear(String nombre) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/porteria/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(new SolicitudNoAnunciadaRequest(
                                apartamento, nombre, "0001", "Trae un domicilio"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    /** Saca el token en claro del enlace que viajo en el evento. */
    private String tokenDelEvento() {
        String url = eventos.dato(TipoEvento.VISITA_NO_ANUNCIADA, "urlAprobar");
        String sinQuery = url.substring(0, url.indexOf('?'));
        return sinQuery.substring(sinQuery.lastIndexOf('/') + 1);
    }

    private long ingresosDelApartamento() {
        return ingresos.findAll().stream()
                .filter(i -> i.getResidente().identificador().equals(apartamento))
                .count();
    }

    // --------------------------------------------------------------- alta

    @Test
    void registrarUnaVisitaNoCreaNingunIngresoTodavia() throws Exception {
        JsonNode solicitud = crear("Pedro Nadie");

        assertThat(solicitud.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(solicitud.get("apartamento").asText()).isEqualTo(apartamento);
        assertThat(solicitud.get("ingresoId")).isNull();

        // Lo que hace que la regla se cumpla por construccion: sin aprobacion
        // no hay nada que contar en "quien esta dentro"
        assertThat(ingresosDelApartamento()).isZero();
        mockMvc.perform(get("/api/porteria/dentro").header("X-Rol", "VIGILANTE"))
                .andExpect(jsonPath("$[?(@.apartamento == '" + apartamento + "')]").isEmpty());
    }

    @Test
    void elEventoLlevaLosDosEnlacesYElPlazo() throws Exception {
        crear("Pedro Nadie");

        assertThat(eventos.de(TipoEvento.VISITA_NO_ANUNCIADA)).hasSize(1);
        assertThat(eventos.dato(TipoEvento.VISITA_NO_ANUNCIADA, "urlAprobar"))
                .contains("/api/aprobacion/").contains("accion=aprobar");
        assertThat(eventos.dato(TipoEvento.VISITA_NO_ANUNCIADA, "urlRechazar"))
                .contains("accion=rechazar");
        assertThat(eventos.dato(TipoEvento.VISITA_NO_ANUNCIADA, "venceEn")).isNotBlank();
    }

    @Test
    void unApartamentoQueNoExisteEs404() throws Exception {
        mockMvc.perform(post("/api/porteria/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(new SolicitudNoAnunciadaRequest(
                                "ZZ-999", "Pedro Nadie", "0001", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unResidenteNoPuedeRegistrarVisitasNoAnunciadas() throws Exception {
        mockMvc.perform(post("/api/porteria/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento)
                        .content(json.writeValueAsString(new SolicitudNoAnunciadaRequest(
                                apartamento, "Pedro Nadie", "0001", null))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------- el GET no debe mutar

    @Test
    void elGetDevuelveLaPaginaDeConfirmacionSinConsumirElToken() throws Exception {
        crear("Pedro Nadie");
        String token = tokenDelEvento();

        // Tres visitas al enlace, como haria el prefetch de un cliente de correo
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/aprobacion/{token}", token).param("accion", "APROBAR"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Pedro Nadie")));
        }

        // Y el token sigue sirviendo: el prefetch no decidio nada
        mockMvc.perform(post("/api/aprobacion/{token}", token).param("accion", "APROBAR"))
                .andExpect(status().isOk());
    }

    @Test
    void laPaginaEscapaElNombreQueTecleoElVigilante() throws Exception {
        crear("<script>alert(1)</script>");
        String token = tokenDelEvento();

        mockMvc.perform(get("/api/aprobacion/{token}", token).param("accion", "APROBAR"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>alert"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    }

    // ----------------------------------------------------------- aprobacion

    @Test
    void aprobarCreaElIngresoYLoDejaDentro() throws Exception {
        crear("Pedro Nadie");
        String token = tokenDelEvento();

        mockMvc.perform(post("/api/aprobacion/{token}", token).param("accion", "APROBAR"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aprobada")));

        assertThat(ingresosDelApartamento()).isEqualTo(1);
        mockMvc.perform(get("/api/porteria/solicitudes")
                        .param("estado", "APROBADA")
                        .header("X-Rol", "VIGILANTE"))
                .andExpect(jsonPath("$[0].ingresoId").exists());

        // Y ahora si aparece en quien esta dentro, con origen NO_ANUNCIADA
        mockMvc.perform(get("/api/porteria/dentro").header("X-Rol", "VIGILANTE"))
                .andExpect(jsonPath("$[?(@.apartamento == '" + apartamento + "')].origen")
                        .value(org.hamcrest.Matchers.hasItem("NO_ANUNCIADA")));
    }

    @Test
    void elMismoEnlaceNoSirveDosVeces() throws Exception {
        crear("Pedro Nadie");
        String token = tokenDelEvento();

        mockMvc.perform(post("/api/aprobacion/{token}", token).param("accion", "APROBAR"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/aprobacion/{token}", token).param("accion", "APROBAR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TOKEN_YA_USADO"));

        // Y no se creo un segundo ingreso
        assertThat(ingresosDelApartamento()).isEqualTo(1);
    }

    @Test
    void unTokenInventadoEs404() throws Exception {
        mockMvc.perform(post("/api/aprobacion/{token}", "token-que-nadie-emitio")
                        .param("accion", "APROBAR"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- rechazo

    @Test
    void rechazarNoCreaNingunIngreso() throws Exception {
        crear("Pedro Nadie");
        String token = tokenDelEvento();

        mockMvc.perform(post("/api/aprobacion/{token}", token).param("accion", "RECHAZAR"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rechazada")));

        assertThat(ingresosDelApartamento()).isZero();
    }

    @Test
    void trasUnRechazoElVigilanteNoPuedeDarLaVuelta() throws Exception {
        JsonNode solicitud = crear("Pedro Nadie");
        String token = tokenDelEvento();
        mockMvc.perform(post("/api/aprobacion/{token}", token).param("accion", "RECHAZAR"))
                .andExpect(status().isOk());

        // RECHAZADA es final: solo la administracion podria anular (§6)
        mockMvc.perform(patch("/api/porteria/solicitudes/{id}/decision", solicitud.get("id").asLong())
                        .param("accion", "APROBAR")
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        assertThat(ingresosDelApartamento()).isZero();
    }

    // ------------------------------------------------- decision del vigilante

    @Test
    void elVigilanteNoPuedeDecidirMientrasSigaPendiente() throws Exception {
        JsonNode solicitud = crear("Pedro Nadie");

        // Solo cabe desde SIN_RESPUESTA, y a eso solo llega el scheduler
        mockMvc.perform(patch("/api/porteria/solicitudes/{id}/decision", solicitud.get("id").asLong())
                        .param("accion", "APROBAR")
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isConflict());
    }

    @Test
    void decidirSobreUnaSolicitudQueNoExisteEs404() throws Exception {
        mockMvc.perform(patch("/api/porteria/solicitudes/{id}/decision", 999_999)
                        .param("accion", "APROBAR")
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elListadoPorDefectoTraeLasPendientes() throws Exception {
        crear("Pedro Nadie");

        mockMvc.perform(get("/api/porteria/solicitudes").header("X-Rol", "VIGILANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.apartamento == '" + apartamento + "')].estado")
                        .value(org.hamcrest.Matchers.hasItem("PENDIENTE")));
    }
}
