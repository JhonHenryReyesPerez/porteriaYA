package co.porteriaya.nopermitido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import co.porteriaya.common.Normalizador;
import co.porteriaya.nopermitido.entity.NoPermitido;
import co.porteriaya.nopermitido.repository.NoPermitidoRepository;
import co.porteriaya.nopermitido.service.Coincidencia;
import co.porteriaya.nopermitido.service.NoPermitidoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El criterio de dos niveles de §6: exacta niega, parcial avisa. Es la regla
 * mas delicada del paso 4, porque un falso positivo bloquea a un inocente y un
 * falso negativo deja entrar a quien no debe.
 */
@ExtendWith(MockitoExtension.class)
class NoPermitidoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T12:00:00Z");

    @Mock
    private NoPermitidoRepository repositorio;

    @InjectMocks
    private NoPermitidoService servicio;

    private NoPermitido registro(String nombre, String doc4) {
        return new NoPermitido(nombre, doc4, "Agresion al personal", "admin1", AHORA);
    }

    @Test
    void nombreYDocumentoIgualesEsCoincidenciaExacta() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of(registro("Ivan Castano", "9931")));

        Coincidencia resultado = servicio.evaluar("Ivan Castano", "9931");

        assertThat(resultado.nivel()).isEqualTo(Coincidencia.Nivel.EXACTA);
        assertThat(resultado.niega()).isTrue();
        assertThat(resultado.motivo()).isEqualTo("Agresion al personal");
    }

    @Test
    void mismoNombreConOtroDocumentoSoloAvisa() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of(registro("Ivan Castano", "9931")));

        Coincidencia resultado = servicio.evaluar("Ivan Castano", "1111");

        assertThat(resultado.nivel()).isEqualTo(Coincidencia.Nivel.PARCIAL);
        assertThat(resultado.niega()).isFalse();
        assertThat(resultado.avisa()).isTrue();
    }

    @Test
    void siElRegistroNoTieneDocumentoSoloPuedeAvisar() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of(registro("Ivan Castano", null)));

        Coincidencia resultado = servicio.evaluar("Ivan Castano", "9931");

        assertThat(resultado.nivel()).isEqualTo(Coincidencia.Nivel.PARCIAL);
    }

    @Test
    void siElVisitanteNoDaDocumentoSoloPuedeAvisar() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of(registro("Ivan Castano", "9931")));

        Coincidencia resultado = servicio.evaluar("Ivan Castano", null);

        assertThat(resultado.nivel()).isEqualTo(Coincidencia.Nivel.PARCIAL);
    }

    @Test
    void laComparacionIgnoraAcentosMayusculasYEspaciosDeMas() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of(registro("José  Castaño", "9931")));

        Coincidencia resultado = servicio.evaluar("  jose castano ", "9931");

        assertThat(resultado.nivel()).isEqualTo(Coincidencia.Nivel.EXACTA);
    }

    @Test
    void unNombreDistintoNoCoincide() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of(registro("Ivan Castano", "9931")));

        assertThat(servicio.evaluar("Maria Lopez", "9931").nivel())
                .isEqualTo(Coincidencia.Nivel.NINGUNA);
    }

    @Test
    void siHayVariosHomonimosGanaLaCoincidenciaMasGrave() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of(
                registro("Juan Perez", "0001"),
                registro("Juan Perez", "0002"),
                registro("Juan Perez", null)));

        Coincidencia resultado = servicio.evaluar("Juan Perez", "0002");

        assertThat(resultado.nivel()).isEqualTo(Coincidencia.Nivel.EXACTA);
        assertThat(resultado.registro().getDoc4()).isEqualTo("0002");
    }

    @Test
    void sinNombreNoSeConsultaNada() {
        assertThat(servicio.evaluar(null, "9931").nivel()).isEqualTo(Coincidencia.Nivel.NINGUNA);
        assertThat(servicio.evaluar("   ", "9931").nivel()).isEqualTo(Coincidencia.Nivel.NINGUNA);
    }

    @Test
    void laListaVaciaNuncaCoincide() {
        when(repositorio.findByActivoTrue()).thenReturn(List.of());

        assertThat(servicio.evaluar("Ivan Castano", "9931").nivel())
                .isEqualTo(Coincidencia.Nivel.NINGUNA);
    }

    @Test
    void elNormalizadorDejaLosNombresComparables() {
        assertThat(Normalizador.nombre("José  Pérez")).isEqualTo("jose perez");
        assertThat(Normalizador.nombre("  MARIA   CAMILA  ")).isEqualTo("maria camila");
        assertThat(Normalizador.nombre("Ñuño Muñoz")).isEqualTo("nuno munoz");
        assertThat(Normalizador.nombre(null)).isNull();
    }
}
