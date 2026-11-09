package co.porteriaya.paquete.repository;

import java.util.List;

import co.porteriaya.paquete.entity.AutorizadoRetiro;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutorizadoRetiroRepository extends JpaRepository<AutorizadoRetiro, Long> {

    /**
     * La comparacion de nombres necesita quitar acentos, igual que en la lista
     * de no permitidos, asi que se trae la lista del apartamento y se compara
     * en memoria. Son dos o tres personas por apartamento.
     */
    List<AutorizadoRetiro> findByResidenteIdAndActivoTrue(Long residenteId);
}
