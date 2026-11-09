package co.porteriaya.paquete.repository;

import java.util.List;

import co.porteriaya.paquete.entity.EstadoPaquete;
import co.porteriaya.paquete.entity.Paquete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaqueteRepository extends JpaRepository<Paquete, Long> {

    @Query("""
            select p from Paquete p
             join fetch p.residente r
             where (:estado is null or p.estado = :estado)
               and (:torre is null or r.torre = :torre)
               and (:apartamento is null or r.apartamento = :apartamento)
             order by p.recibidoEn asc
            """)
    List<Paquete> buscar(EstadoPaquete estado, String torre, String apartamento);

    long countByEstado(EstadoPaquete estado);

    /**
     * Paquetes que siguen sin retirar, para los avisos de §9.
     *
     * Las banderas se filtran en el service y no aqui: la pasada tiene que
     * mirar las tres (24 h, 72 h y escalamiento) sobre el mismo conjunto, y
     * tres consultas distintas solo darian pie a que se desincronicen.
     */
    @Query("""
            select p from Paquete p
             join fetch p.residente
             where p.estado = co.porteriaya.paquete.entity.EstadoPaquete.RECIBIDO
             order by p.recibidoEn asc
            """)
    List<Paquete> sinRetirar();
}
