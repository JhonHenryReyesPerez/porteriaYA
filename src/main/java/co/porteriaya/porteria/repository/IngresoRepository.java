package co.porteriaya.porteria.repository;

import java.util.List;

import co.porteriaya.porteria.entity.EstadoIngreso;
import co.porteriaya.porteria.entity.Ingreso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IngresoRepository extends JpaRepository<Ingreso, Long> {

    /** Paso 5 de la validacion (§6): ya figura como Dentro con la misma autorizacion. */
    boolean existsByAutorizacionIdAndEstado(Long autorizacionId, EstadoIngreso estado);

    @Query("""
            select i from Ingreso i
             join fetch i.residente
             where i.estado = :estado
             order by i.ingresoEn asc
            """)
    List<Ingreso> buscarPorEstado(EstadoIngreso estado);

    /**
     * Candidatos a PERMANENCIA_EXCEDIDA y a cierre automatico (§9).
     *
     * Devuelve a quien sigue dentro sin que se le haya avisado, y el service
     * hace la cuenta del limite con el Clock y la duracion de cada fila. No se
     * intenta en SQL a proposito: el limite es ingresoEn + duracion + gracia,
     * aritmetica por fila que en JPQL queda ilegible, y el conjunto esta
     * acotado por "quien esta dentro ahora mismo", que son decenas.
     *
     * El filtro de la bandera es lo que hace idempotente la pasada: una vez
     * avisado, el ingreso ya no vuelve a salir aqui.
     */
    @Query("""
            select i from Ingreso i
             join fetch i.residente
             where i.estado = co.porteriaya.porteria.entity.EstadoIngreso.DENTRO
               and i.permanenciaAvisada = false
            """)
    List<Ingreso> dentroSinAvisarDePermanencia();

    long countByEstado(EstadoIngreso estado);

    /** Para el grafico de visitas por hora del panel (§13). */
    List<Ingreso> findByIngresoEnGreaterThanEqual(java.time.Instant desde);

    /** Todos los que siguen dentro, para el cierre de medianoche (§9). */
    @Query("""
            select i from Ingreso i
             join fetch i.residente
             where i.estado = co.porteriaya.porteria.entity.EstadoIngreso.DENTRO
            """)
    List<Ingreso> dentro();
}
