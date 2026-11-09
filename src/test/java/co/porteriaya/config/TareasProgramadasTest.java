package co.porteriaya.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.porteriaya.autorizacion.service.AutorizacionService;
import co.porteriaya.paquete.service.PaqueteService;
import co.porteriaya.porteria.service.IngresoService;
import co.porteriaya.solicitud.service.SolicitudService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El disparador de §9. No tiene reglas de negocio, asi que lo unico que hay
 * que comprobar es que no se le olvide ninguna tarea.
 *
 * Suena trivial y es justo el tipo de cosa que se rompe en silencio: si alguien
 * añade una tarea al dominio y no la engancha aqui, nada falla, simplemente no
 * se ejecuta nunca.
 */
@ExtendWith(MockitoExtension.class)
class TareasProgramadasTest {

    @Mock
    private AutorizacionService autorizaciones;
    @Mock
    private SolicitudService solicitudes;
    @Mock
    private IngresoService ingresos;
    @Mock
    private PaqueteService paquetes;

    @InjectMocks
    private TareasProgramadas tareas;

    @Test
    void unaPasadaEjecutaLasCincoTareas() {
        when(autorizaciones.vencerLasQueTerminaron()).thenReturn(1);
        when(solicitudes.vencerPendientes()).thenReturn(2);
        when(ingresos.avisarPermanenciasExcedidas()).thenReturn(3);
        when(ingresos.cerrarLosQueNoRegistraronSalida()).thenReturn(4);
        when(paquetes.procesarAvisos()).thenReturn(5);

        TareasProgramadas.ResumenPasada resumen = tareas.ejecutarPasada();

        assertThat(resumen.autorizacionesVencidas()).isEqualTo(1);
        assertThat(resumen.solicitudesSinRespuesta()).isEqualTo(2);
        assertThat(resumen.permanenciasAvisadas()).isEqualTo(3);
        assertThat(resumen.ingresosCerrados()).isEqualTo(4);
        assertThat(resumen.avisosDePaquetes()).isEqualTo(5);
        assertThat(resumen.total()).isEqualTo(15);
    }

    @Test
    void elAvisoDePermanenciaVaAntesDelCierreAutomatico() {
        TareasProgramadas.ResumenPasada ignorado = tareas.ejecutarPasada();

        // Importa el orden: si se cerrara primero, el ingreso dejaria de estar
        // DENTRO y nunca se avisaria de la permanencia excedida de esa visita.
        InOrder orden = inOrder(ingresos);
        orden.verify(ingresos).avisarPermanenciasExcedidas();
        orden.verify(ingresos).cerrarLosQueNoRegistraronSalida();

        assertThat(ignorado.total()).isZero();
    }

    @Test
    void elCronDispararaLaMismaPasada() {
        tareas.cadaMinuto();

        verify(autorizaciones).vencerLasQueTerminaron();
        verify(solicitudes).vencerPendientes();
        verify(ingresos).avisarPermanenciasExcedidas();
        verify(ingresos).cerrarLosQueNoRegistraronSalida();
        verify(paquetes).procesarAvisos();
    }

    @Test
    void unaPasadaSinTrabajoDevuelveTodoACero() {
        TareasProgramadas.ResumenPasada resumen = tareas.ejecutarPasada();

        assertThat(resumen.total()).isZero();
    }
}
