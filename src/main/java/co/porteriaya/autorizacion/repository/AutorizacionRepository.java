package co.porteriaya.autorizacion.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import co.porteriaya.autorizacion.entity.Autorizacion;
import co.porteriaya.autorizacion.entity.EstadoAutorizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutorizacionRepository extends JpaRepository<Autorizacion, Long> {

    Optional<Autorizacion> findByCodigo(String codigo);

    boolean existsByCodigoAndEstado(String codigo, EstadoAutorizacion estado);

    List<Autorizacion> findByResidenteIdOrderByCreadoEnDesc(Long residenteId);

    List<Autorizacion> findByEstadoAndValidaHastaBefore(EstadoAutorizacion estado, Instant momento);

    /**
     * Paso 3 de la validacion (§6): descuenta un uso con una sola sentencia
     * atomica. Devuelve las filas afectadas; 0 significa usos agotados y el
     * service lo traduce a 409 USOS_AGOTADOS.
     *
     * El `usos_max IS NULL` es obligatorio: representa los usos ilimitados de
     * un servicio recurrente o una mudanza. Sin el, la comparacion con NULL
     * nunca es verdadera y todo ingreso recurrente daria 409.
     *
     * Nada de leer-modificar-guardar: esta sentencia es la que hace que dos
     * vigilantes validando el mismo codigo a la vez no puedan pasar los dos.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE autorizacion SET usos_actuales = usos_actuales + 1
             WHERE id = :id AND (usos_max IS NULL OR usos_actuales < usos_max)
            """, nativeQuery = true)
    int consumirUso(@Param("id") Long id);
}
