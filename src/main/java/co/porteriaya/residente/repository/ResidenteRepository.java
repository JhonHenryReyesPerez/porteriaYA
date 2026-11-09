package co.porteriaya.residente.repository;

import java.util.List;
import java.util.Optional;

import co.porteriaya.residente.entity.Residente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResidenteRepository extends JpaRepository<Residente, Long> {

    Optional<Residente> findByTorreAndApartamento(String torre, String apartamento);

    List<Residente> findByActivoTrueOrderByTorreAscApartamentoAsc();
}
