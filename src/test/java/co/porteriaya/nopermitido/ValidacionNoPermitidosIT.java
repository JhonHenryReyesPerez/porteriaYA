package co.porteriaya.nopermitido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import co.porteriaya.IntegracionBase;
import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.TipoAutorizacion;
import co.porteriaya.autorizacion.repository.AutorizacionRepository;
import co.porteriaya.bitacora.repository.BitacoraRepository;
import co.porteriaya.nopermitido.entity.NoPermitido;
import co.porteriaya.nopermitido.repository.NoPermitidoRepository;
import co.porteriaya.porteria.dto.SolicitudValidacion;
import co.porteriaya.residente.entity.Residente;
import co.porteriaya.residente.repository.ResidenteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El paso 4 de la cadena, por HTTP y contra la base real. Las unitarias ya
 * cubren el criterio de coincidencia; esto comprueba el cableado: que el motivo
 * llegue al vigilante y que la alerta de seguridad quede de verdad en bitacora.
 */
@AutoConfigureMockMvc
class ValidacionNoPermitidosIT extends IntegracionBase {

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

    @BeforeEach
    void prepararAutorizacionActiva() {
        int n = SECUENCIA.incrementAndGet();
        Residente residente = residentes.save(new Residente(
                "NP", "N" + n, "Residente de prueba " + n, null, null, clock.instant()));
        Instant ahora = clock.instant();
        codigo = "8%05d".formatted(n);
        autorizaciones.save(new Autorizacion(residente, TipoAutorizacion.VISITA, codigo,
                "Quien sea", "0000", ahora.minus(Duration.ofHours(1)),
                ahora.plus(Duration.ofHours(8)), null, 5, 120, ahora));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion(
            String nombre, String doc4) throws Exception {
        return post("/api/porteria/validar")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Rol", "VIGILANTE")
                .header("X-Actor", "vigilante1")
                .content(json.writeValueAsString(new SolicitudValidacion(codigo, nombre, doc4)));
    }

    @Test
    void unaCoincidenciaExactaNiegaElIngresoYQuedaEnBitacora() throws Exception {
        noPermitidos.save(new NoPermitido("Ivan Castano", "9931",
                "Agresion al personal de vigilancia", "admin1", clock.instant()));
        long entradasAntes = bitacora.count();

        mockMvc.perform(peticion("Ivan Castano", "9931"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(false))
                .andExpect(jsonPath("$.motivo").value("PERSONA_NO_PERMITIDA"))
                .andExpect(jsonPath("$.detalle").value("Agresion al personal de vigilancia"))
                .andExpect(jsonPath("$.autorizacion").doesNotExist());

        // La alerta se registra aunque el vigilante no intente nada mas
        assertThat(bitacora.count()).isEqualTo(entradasAntes + 1);
    }

    @Test
    void unaCoincidenciaParcialAvisaPeroDejaPasar() throws Exception {
        noPermitidos.save(new NoPermitido("Ramiro Delgado", "1111",
                "Hurto en zonas comunes", "admin1", clock.instant()));
        long entradasAntes = bitacora.count();

        // Mismo nombre, otro documento: el vigilante decide
        mockMvc.perform(peticion("Ramiro Delgado", "2222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(true))
                .andExpect(jsonPath("$.motivo").doesNotExist())
                .andExpect(jsonPath("$.advertencias[0]").value("POSIBLE_NO_PERMITIDO"))
                .andExpect(jsonPath("$.detalle").value("Hurto en zonas comunes"));

        // Un aviso no es una alerta: no ensucia la bitacora
        assertThat(bitacora.count()).isEqualTo(entradasAntes);
    }

    @Test
    void laCoincidenciaIgnoraAcentosYMayusculas() throws Exception {
        noPermitidos.save(new NoPermitido("José Castaño", "7777",
                "Suplantacion", "admin1", clock.instant()));

        mockMvc.perform(peticion("JOSE CASTANO", "7777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(false))
                .andExpect(jsonPath("$.motivo").value("PERSONA_NO_PERMITIDA"));
    }

    @Test
    void unRegistroDesactivadoYaNoBloquea() throws Exception {
        NoPermitido registro = noPermitidos.save(new NoPermitido("Olga Prieto", "3333",
                "Resuelto con administracion", "admin1", clock.instant()));
        registro.desactivar();
        noPermitidos.save(registro);

        mockMvc.perform(peticion("Olga Prieto", "3333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(true))
                .andExpect(jsonPath("$.advertencias.length()").value(0));
    }

    @Test
    void quienNoEstaEnLaListaPasaSinAvisos() throws Exception {
        noPermitidos.save(new NoPermitido("Alguien Vetado", "4444",
                "Motivo cualquiera", "admin1", clock.instant()));

        mockMvc.perform(peticion("Persona Corriente", "5555"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(true))
                .andExpect(jsonPath("$.advertencias.length()").value(0))
                .andExpect(jsonPath("$.detalle").doesNotExist());
    }
}
