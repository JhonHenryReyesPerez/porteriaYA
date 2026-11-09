package co.porteriaya.paquete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import co.porteriaya.IntegracionBase;
import co.porteriaya.paquete.dto.AutorizadoRetiroRequest;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Autorizados a retirar paquetes por HTTP (§8).
 *
 * Lo que de verdad importa aqui es el aislamiento entre apartamentos: la ruta
 * lleva el id del residente y el rol viaja en una cabecera autodeclarada, asi
 * que sin comprobacion en servidor cualquiera podria tocar la lista del vecino.
 */
@AutoConfigureMockMvc
class AutorizadoRetiroIT extends IntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ResidenteRepository residentes;
    @Autowired
    private Clock clock;

    private Residente mio;
    private Residente delVecino;

    @BeforeEach
    void prepararDosApartamentos() {
        mio = nuevoApartamento();
        delVecino = nuevoApartamento();
    }

    private Residente nuevoApartamento() {
        int n = SECUENCIA.incrementAndGet();
        return residentes.save(new Residente(
                "AR", "A" + n, "Residente " + n, "ar" + n + "@example.test", null,
                clock.instant()));
    }

    private ResultActions autorizar(
            Residente dueno, String actor, String nombre, String doc4) throws Exception {
        return mockMvc.perform(post("/api/residentes/{id}/autorizados-retiro", dueno.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Rol", "RESIDENTE")
                .header("X-Actor", actor)
                .content(json.writeValueAsString(new AutorizadoRetiroRequest(nombre, doc4))));
    }

    // ------------------------------------------------------------------ alta

    @Test
    void elResidenteAutorizaAUnaPersonaYLaVeEnSuLista() throws Exception {
        String cuerpo = autorizar(mio, mio.identificador(), "María Fernanda Ruiz", "8842")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode creado = json.readTree(cuerpo);
        assertThat(creado.get("apartamento").asText())
                .isEqualTo(mio.identificador());

        mockMvc.perform(get("/api/residentes/{id}/autorizados-retiro", mio.getId())
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", mio.identificador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("María Fernanda Ruiz"))
                .andExpect(jsonPath("$[0].doc4").value("8842"));
    }

    @Test
    void elDoc4EsOpcionalPeroTieneQueSerDeCuatroDigitos() throws Exception {
        autorizar(mio, mio.identificador(), "Sin documento", null)
                .andExpect(status().isCreated());

        autorizar(mio, mio.identificador(), "Documento largo", "123456")
                .andExpect(status().isBadRequest());
    }

    @Test
    void autorizarDosVecesALaMismaPersonaEs409() throws Exception {
        autorizar(mio, mio.identificador(), "José Pérez", "1234")
                .andExpect(status().isCreated());

        // Normaliza igual (acentos, mayusculas, espacios), asi que es la misma
        // persona escrita de otra forma: la lista no debe quedar ambigua.
        autorizar(mio, mio.identificador(), "  jose   perez ", "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AUTORIZADO_REPETIDO"));
    }

    // -------------------------------------------------- aislamiento por apto

    @Test
    void unResidenteNoPuedeAutorizarEnElApartamentoDelVecino() throws Exception {
        autorizar(delVecino, mio.identificador(), "Intruso", "0000")
                .andExpect(status().isForbidden());
    }

    @Test
    void unResidenteNoPuedeVerLaListaDelVecino() throws Exception {
        mockMvc.perform(get("/api/residentes/{id}/autorizados-retiro", delVecino.getId())
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", mio.identificador()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elVigilanteSiPuedeConsultarCualquierApartamento() throws Exception {
        autorizar(mio, mio.identificador(), "Empleada del hogar", "5566")
                .andExpect(status().isCreated());

        // Lo necesita en la puerta para acreditar un retiro sin codigo.
        mockMvc.perform(get("/api/residentes/{id}/autorizados-retiro", mio.getId())
                        .header("X-Rol", "VIGILANTE")
                        .header("X-Actor", "vigilante1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ------------------------------------------------------------------ baja

    @Test
    void quitarLaAutorizacionLaDesactivaYRepetirloEs409() throws Exception {
        String cuerpo = autorizar(mio, mio.identificador(), "Vecino de confianza", "7788")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long autorizadoId = json.readTree(cuerpo).get("id").asLong();

        mockMvc.perform(delete("/api/residentes/{id}/autorizados-retiro/{autorizadoId}",
                        mio.getId(), autorizadoId)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", mio.identificador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // Ya no sale en la lista, pero la fila sigue ahi (§12: sin borrados)
        mockMvc.perform(get("/api/residentes/{id}/autorizados-retiro", mio.getId())
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", mio.identificador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/residentes/{id}/autorizados-retiro/{autorizadoId}",
                        mio.getId(), autorizadoId)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", mio.identificador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_DESACTIVADO"));
    }

    @Test
    void quitarUnAutorizadoDeOtroApartamentoEs404() throws Exception {
        String cuerpo = autorizar(delVecino, delVecino.identificador(), "Del vecino", "9900")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ajeno = json.readTree(cuerpo).get("id").asLong();

        // 404 y no 403: desde fuera no debe poder distinguirse si ese id existe.
        mockMvc.perform(delete("/api/residentes/{id}/autorizados-retiro/{autorizadoId}",
                        mio.getId(), ajeno)
                        .header("X-Rol", "RESIDENTE")
                        .header("X-Actor", mio.identificador()))
                .andExpect(status().isNotFound());
    }
}
