package co.porteriaya.bitacora.repository;

import java.time.Instant;
import java.util.List;

import co.porteriaya.bitacora.entity.Bitacora;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Append-only: solo alta y consulta. No se expone ningun metodo de borrado ni
 * actualizacion, y la entidad no tiene setters (§6).
 */
public interface BitacoraRepository extends JpaRepository<Bitacora, Long> {

    /**
     * Consulta con los tres filtros de §8, los tres opcionales.
     *
     * Lleva Pageable porque la bitacora solo crece: devolverla entera seria una
     * bomba de relojeria en una entrega con datos de varios meses.
     */
    @Query("""
            select b from Bitacora b
             where (:desde is null or b.fecha >= :desde)
               and (:hasta is null or b.fecha <= :hasta)
               and (:actor is null or b.actor = :actor)
               and (:entidad is null or b.entidad = :entidad)
             order by b.fecha desc
            """)
    List<Bitacora> buscar(Instant desde, Instant hasta, String actor, String entidad,
                          Pageable pagina);
}
