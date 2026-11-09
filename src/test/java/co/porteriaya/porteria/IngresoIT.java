package co.porteriaya.porteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import co.porteriaya.IntegracionBase;
import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.bitacora.repository.BitacoraRepository;
import co.porteriaya.nopermitido.entity.NoPermitido;
import co.porteriaya.nopermitido.repository.NoPermitidoRepository;
import co.porteriaya.porteria.dto.SolicitudIngreso;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ingresos y salidas por HTTP, con el paso 5 de la cadena y las advertencias. */
@AutoConfigureMockMvc
class IngresoIT extends IntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ResidenteRepository residentes;
    @Autowired
    private AutorizacionRepository autorizaciones;
    @Autowired
    private NoPermitidoRepository noPermitidos;
    @Autowired
    private BitacoraRepository bitacora;
    @Autowired
    private Clock clock;

    private String codigo;
    private String apartamento;

    @BeforeEach
    void prepararAutorizacion() {
        int n = SECUENCIA.incrementAndGet();
        Residente residente = residentes.save(new Residente(
                "IN", "I" + n, "Residente de prueba " + n, null, null, clock.instant()));
        apartamento = residente.identificador();
        codigo = "7%05d".formatted(n);
        Instant ahora = clock.instant();
        autorizaciones.save(new Autorizacion(residente, TipoAutorizacion.VISITA, codigo,
                "Camilo Rojas", "4821", ahora.minus(Duration.ofHours(1)),
                ahora.plus(Duration.ofHours(8)), null, 2, 120, ahora));
    }

    // ------------------------------------------------------------ utilidades

    private MockHttpServletRequestBuilder registrar(String nombre, String doc4, boolean confirma)
            throws Exception {
        return post("/api/porteria/ingresos")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Rol", "VIGILANTE")
                .header("X-Actor", "vigilante1")
                .content(json.writeValueAsString(
                        new SolicitudIngreso(codigo, nombre, doc4, confirma)));
    }

    private JsonNode registrado() throws Exception {
        String cuerpo = mockMvc.perform(registrar("Camilo Rojas", "4821", false))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    // --------------------------------------------------------------- ingreso

    @Test
    void registrarUnIngresoConsumeElUsoYDejaALaPersonaDentro() throws Exception {
        JsonNode ingreso = registrado();

        assertThat(ingreso.get("estado").asText()).isEqualTo("DENTRO");
        assertThat(ingreso.get("origen").asText()).isEqualTo("AUTORIZACION");
        assertThat(ingreso.get("apartamento").asText()).isEqualTo(apartamento);
        assertThat(ingreso.get("duracionEsperadaMin").asInt()).isEqualTo(120);
        assertThat(ingreso.get("salidaEn")).isNull();

        Autorizacion despues = autorizaciones.findByCodigo(codigo).orElseThrow();
        assertThat(despues.getUsosActuales()).isEqualTo(1);
        assertThat(despues.getEstado()).isEqualTo(EstadoAutorizacion.ACTIVA);
    }

    @Test
    void gastarElUltimoUsoDejaLaAutorizacionAgotada() throws Exception {
        registrado();
        // Segundo uso: hay que confirmar la advertencia YA_DENTRO
        mockMvc.perform(registrar("Camilo Rojas", "4821", true)).andExpect(status().isCreated());

        Autorizacion despues = autorizaciones.findByCodigo(codigo).orElseThrow();
        assertThat(despues.getUsosActuales()).isEqualTo(2);
        assertThat(despues.getEstado()).isEqualTo(EstadoAutorizacion.AGOTADA);

        // Un tercer intento ya no pasa el paso 3
        mockMvc.perform(registrar("Camilo Rojas", "4821", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USOS_AGOTADOS"));
    }

    // ------------------------------------------- paso 5 de la cadena: YA_DENTRO

    @Test
    void validarAvisaDeQueLaPersonaYaFiguraDentro() throws Exception {
        registrado();

        mockMvc.perform(post("/api/porteria/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new SolicitudValidacion(codigo, "Camilo Rojas", "4821"))))
                .andExpect(status().isOk())
                // Es un aviso, no un rechazo: §6 dice que el vigilante decide
                .andExpect(jsonPath("$.permitido").value(true))
                .andExpect(jsonPath("$.advertencias[0]").value("YA_DENTRO"));
    }

    @Test
    void sinConfirmarLaAdvertenciaElIngresoDuplicadoEs409() throws Exception {
        registrado();

        mockMvc.perform(registrar("Camilo Rojas", "4821", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ADVERTENCIAS_SIN_CONFIRMAR"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("YA_DENTRO")));

        // Y no se gasto el segundo uso en el intento fallido
        assertThat(autorizaciones.findByCodigo(codigo).orElseThrow().getUsosActuales()).isEqualTo(1);
    }

    @Test
    void confirmandoLaAdvertenciaElVigilantePuedeDuplicar() throws Exception {
        registrado();

        mockMvc.perform(registrar("Camilo Rojas", "4821", true))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/porteria/dentro").header("X-Rol", "VIGILANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.apartamento == '" + apartamento + "')].length()").exists());
    }

    // --------------------------------------------- rechazo con efectos que quedan

    @Test
    void unaPersonaNoPermitidaNoEntraPeroElIntentoQuedaRegistrado() throws Exception {
        noPermitidos.save(new NoPermitido("Ivan Vetado", "9931",
                "Agresion al personal", "admin1", clock.instant()));
        long entradasAntes = bitacora.count();

        mockMvc.perform(registrar("Ivan Vetado", "9931", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_PERMITIDA"));

        // Lo que importa: el 409 no se llevo por delante la alerta. Si el
        // rechazo se hubiera señalado lanzando dentro de la transaccion, esta
        // entrada no existiria.
        assertThat(bitacora.count()).isEqualTo(entradasAntes + 1);
        // Y no se consumio ningun uso
        assertThat(autorizaciones.findByCodigo(codigo).orElseThrow().getUsosActuales()).isZero();
    }

    @Test
    void unCodigoInexistenteEs409YNoCreaNingunIngreso() throws Exception {
        mockMvc.perform(post("/api/porteria/ingresos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new SolicitudIngreso("000000", "Nadie", "0000", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CODIGO_NO_EXISTE"));
    }

    // ---------------------------------------------------------------- salida

    @Test
    void registrarLaSalidaCierraElIngresoYCerrarDosVecesEs409() throws Exception {
        long id = registrado().get("id").asLong();

        mockMvc.perform(patch("/api/porteria/ingresos/{id}/salida", id)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SALIO"))
                .andExpect(jsonPath("$.salidaEn").exists());

        mockMvc.perform(patch("/api/porteria/ingresos/{id}/salida", id)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void trasLaSalidaLaPersonaYaNoFiguraDentro() throws Exception {
        long id = registrado().get("id").asLong();
        mockMvc.perform(patch("/api/porteria/ingresos/{id}/salida", id)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isOk());

        // Y el paso 5 deja de avisar
        mockMvc.perform(post("/api/porteria/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1")
                        .content(json.writeValueAsString(
                                new SolicitudValidacion(codigo, "Camilo Rojas", "4821"))))
                .andExpect(jsonPath("$.permitido").value(true))
                .andExpect(jsonPath("$.advertencias.length()").value(0));
    }

    @Test
    void laSalidaDeUnIngresoQueNoExisteEs404() throws Exception {
        mockMvc.perform(patch("/api/porteria/ingresos/{id}/salida", 999_999)
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------- roles

    @Test
    void unResidenteNoPuedeRegistrarIngresos() throws Exception {
        mockMvc.perform(post("/api/porteria/ingresos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", apartamento)
                        .content(json.writeValueAsString(
                                new SolicitudIngreso(codigo, "Camilo Rojas", "4821", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorSiPuedeConsultarQuienEstaDentro() throws Exception {
        mockMvc.perform(get("/api/porteria/dentro").header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/porteria/dentro").header("X-Rol", "RESIDENTE"))
                .andExpect(status().isForbidden());
    }
}
