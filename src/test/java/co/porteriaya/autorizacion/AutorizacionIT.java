package co.porteriaya.autorizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import co.porteriaya.IntegracionBase;
import co.porteriaya.autorizacion.dto.SolicitudAutorizacion;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.porteria.dto.SolicitudValidacion;
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

/**
 * El corte vertical completo por HTTP: alta, listado, cancelacion y validacion,
 * con las comprobaciones de rol y de PIN de por medio.
 */
@AutoConfigureMockMvc
class AutorizacionIT extends IntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ResidenteRepository residentes;
    @Autowired
    private Clock clock;

    private String apartamento;

    @BeforeEach
    void crearApartamento() {
        int n = SECUENCIA.incrementAndGet();
        Residente residente = residentes.save(new Residente(
                "IT", "A" + n, "Residente de prueba " + n, null, null, clock.instant()));
        apartamento = residente.identificador();
    }

    // ------------------------------------------------------------ utilidades

    private SolicitudAutorizacion visita(Integer usosMax) {
        Instant ahora = clock.instant();
        return new SolicitudAutorizacion(TipoAutorizacion.VISITA, "Camilo Rojas", "4821",
                ahora.minus(Duration.ofHours(1)), ahora.plus(Duration.ofHours(8)),
                null, null, null, usosMax, null);
    }

    private JsonNode crear(SolicitudAutorizacion solicitud) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/autorizaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento)
                        .content(json.writeValueAsString(solicitud)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private void esperarEstado(SolicitudAutorizacion solicitud, int esperado) throws Exception {
        mockMvc.perform(post("/api/autorizaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento)
                        .content(json.writeValueAsString(solicitud)))
                .andExpect(status().is(esperado));
    }

    // ------------------------------------------------------------------ alta

    @Test
    void crearUnaVisitaDevuelveUnCodigoDeSeisDigitos() throws Exception {
        JsonNode creada = crear(visita(1));

        assertThat(creada.get("codigo").asText()).matches("[0-9]{6}");
        assertThat(creada.get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(creada.get("apartamento").asText()).isEqualTo(apartamento);
        assertThat(creada.get("usosMax").asInt()).isEqualTo(1);
        assertThat(creada.get("usosRestantes").asInt()).isEqualTo(1);
        // Default por tipo: una visita son 180 minutos (§6)
        assertThat(creada.get("duracionEsperadaMin").asInt()).isEqualTo(180);
    }

    @Test
    void unDomiciliarioTomaSuDuracionPorDefectoYEsDeUnSoloUso() throws Exception {
        Instant ahora = clock.instant();
        JsonNode creada = crear(new SolicitudAutorizacion(TipoAutorizacion.DOMICILIARIO,
                "Mensajeria Rapida", "0094", ahora, ahora.plus(Duration.ofHours(3)),
                null, null, null, null, null));

        assertThat(creada.get("usosMax").asInt()).isEqualTo(1);
        assertThat(creada.get("duracionEsperadaMin").asInt()).isEqualTo(30);
    }

    @Test
    void unServicioRecurrenteGuardaDiasYFranjaYQuedaIlimitado() throws Exception {
        Instant ahora = clock.instant();
        JsonNode creada = crear(new SolicitudAutorizacion(TipoAutorizacion.SERVICIO_RECURRENTE,
                "Marta Quintero", "5567", ahora.minus(Duration.ofDays(1)),
                ahora.plus(Duration.ofDays(60)), Set.of(1, 3, 5),
                LocalTime.of(7, 0), LocalTime.of(12, 0), null, null));

        assertThat(creada.get("diasSemana").toString()).isEqualTo("[1,3,5]");
        assertThat(creada.get("horaDesde").asText()).startsWith("07:00");
        // Ilimitado: usosMax y usosRestantes van nulos
        assertThat(creada.get("usosMax")).isNull();
        assertThat(creada.get("usosRestantes")).isNull();
    }

    @Test
    void elResidenteNoPuedeSobrescribirLaDuracionConUnValorInvalido() throws Exception {
        Instant ahora = clock.instant();
        esperarEstado(new SolicitudAutorizacion(TipoAutorizacion.VISITA, "Camilo Rojas", "4821",
                ahora, ahora.plus(Duration.ofHours(2)), null, null, null, 1, -5), 400);
    }

    @Test
    void elResidenteSiPuedeSobrescribirLaDuracion() throws Exception {
        Instant ahora = clock.instant();
        JsonNode creada = crear(new SolicitudAutorizacion(TipoAutorizacion.VISITA, "Camilo Rojas",
                "4821", ahora, ahora.plus(Duration.ofHours(2)), null, null, null, 1, 45));

        assertThat(creada.get("duracionEsperadaMin").asInt()).isEqualTo(45);
    }

    // ------------------------------------------------- coherencia de la solicitud

    @Test
    void unServicioRecurrenteSinFranjaHorariaEs400() throws Exception {
        Instant ahora = clock.instant();
        esperarEstado(new SolicitudAutorizacion(TipoAutorizacion.SERVICIO_RECURRENTE,
                "Marta Quintero", "5567", ahora, ahora.plus(Duration.ofDays(30)),
                Set.of(1, 3), null, null, null, null), 400);
    }

    @Test
    void unaVisitaConDiasDeLaSemanaEs400() throws Exception {
        Instant ahora = clock.instant();
        esperarEstado(new SolicitudAutorizacion(TipoAutorizacion.VISITA, "Camilo Rojas", "4821",
                ahora, ahora.plus(Duration.ofHours(4)), Set.of(1, 2),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 1, null), 400);
    }

    @Test
    void unaMudanzaConUsosMaxEs400() throws Exception {
        Instant ahora = clock.instant();
        esperarEstado(new SolicitudAutorizacion(TipoAutorizacion.MUDANZA, "Trasteos El Dorado",
                "1122", ahora, ahora.plus(Duration.ofDays(2)), Set.of(1, 2, 3),
                LocalTime.of(8, 0), LocalTime.of(17, 0), 5, null), 400);
    }

    @Test
    void unaVentanaInvertidaEs400() throws Exception {
        Instant ahora = clock.instant();
        esperarEstado(new SolicitudAutorizacion(TipoAutorizacion.VISITA, "Camilo Rojas", "4821",
                ahora, ahora.minus(Duration.ofHours(1)), null, null, null, 1, null), 400);
    }

    @Test
    void mandarElDocumentoCompletoEs400() throws Exception {
        Instant ahora = clock.instant();
        // La API solo acepta 4 digitos: el documento completo no entra ni en la peticion
        esperarEstado(new SolicitudAutorizacion(TipoAutorizacion.VISITA, "Camilo Rojas",
                "1098765432", ahora, ahora.plus(Duration.ofHours(2)),
                null, null, null, 1, null), 400);
    }

    // ------------------------------------------------------------------ roles

    @Test
    void unVigilanteNoPuedeCrearAutorizaciones() throws Exception {
        mockMvc.perform(post("/api/autorizaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(visita(1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void elAdministradorNecesitaElPinParaCancelar() throws Exception {
        Long id = crear(visita(1)).get("id").asLong();

        mockMvc.perform(delete("/api/autorizaciones/{id}", id)
                        .header("X-Rol", "ADMINISTRADOR")
                        .header("X-Actor", "admin1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/autorizaciones/{id}", id)
                        .header("X-Rol", "ADMINISTRADOR")
                        .header("X-Actor", "admin1")
                        .header("X-Admin-Pin", "el-que-no-es"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/autorizaciones/{id}", id)
                        .header("X-Rol", "ADMINISTRADOR")
                        .header("X-Actor", "admin1")
                        .header("X-Admin-Pin", PIN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    // ------------------------------------------------------------ cancelacion

    @Test
    void cancelarEsLogicoYCancelarDosVecesEs409() throws Exception {
        Long id = crear(visita(1)).get("id").asLong();

        mockMvc.perform(delete("/api/autorizaciones/{id}", id)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        // La fila sigue existiendo: el segundo intento es un salto de estado invalido
        mockMvc.perform(delete("/api/autorizaciones/{id}", id)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void unResidenteNoVeNiCancelaLaAutorizacionDeOtroApartamento() throws Exception {
        Long id = crear(visita(1)).get("id").asLong();

        mockMvc.perform(delete("/api/autorizaciones/{id}", id)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", "IT-OTRO"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelarAlgoQueNoExisteEs404() throws Exception {
        mockMvc.perform(delete("/api/autorizaciones/{id}", 999_999)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    // --------------------------------------------------------------- listado

    @Test
    void elListadoSoloTraeLasDelApartamentoQueConsulta() throws Exception {
        crear(visita(1));
        crear(visita(2));

        mockMvc.perform(get("/api/autorizaciones")
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].apartamento").value(apartamento));
    }

    // ------------------------------------------------------------- validacion

    @Test
    void elVigilantePuedeValidarUnCodigoRecienCreado() throws Exception {
        String codigo = crear(visita(1)).get("codigo").asText();

        mockMvc.perform(post("/api/porteria/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new SolicitudValidacion(codigo, "Camilo Rojas", "4821"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(true))
                .andExpect(jsonPath("$.motivo").doesNotExist())
                .andExpect(jsonPath("$.advertencias.length()").value(0))
                .andExpect(jsonPath("$.autorizacion.apartamento").value(apartamento));
    }

    @Test
    void validarUnaAutorizacionCanceladaDevuelveSuMotivo() throws Exception {
        JsonNode creada = crear(visita(1));
        mockMvc.perform(delete("/api/autorizaciones/{id}", creada.get("id").asLong())
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/porteria/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(new SolicitudValidacion(
                                creada.get("codigo").asText(), "Camilo Rojas", "4821"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(false))
                .andExpect(jsonPath("$.motivo").value("AUTORIZACION_CANCELADA"));
    }

    @Test
    void unCodigoInexistenteDevuelveCodigoNoExiste() throws Exception {
        mockMvc.perform(post("/api/porteria/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new SolicitudValidacion("000000", "Nadie", "0000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(false))
                .andExpect(jsonPath("$.motivo").value("CODIGO_NO_EXISTE"));
    }

    @Test
    void unCodigoDeCincoDigitosEs400() throws Exception {
        mockMvc.perform(post("/api/porteria/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new SolicitudValidacion("12345", "Alguien", "0000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void unResidenteNoPuedeValidarCodigos() throws Exception {
        mockMvc.perform(post("/api/porteria/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento)
                        .content(json.writeValueAsString(
                                new SolicitudValidacion("000000", "Nadie", "0000"))))
                .andExpect(status().isForbidden());
    }
}
