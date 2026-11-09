package co.porteriaya.nopermitido.repository;

import java.util.List;

import co.porteriaya.nopermitido.entity.NoPermitido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoPermitidoRepository extends JpaRepository<NoPermitido, Long> {

    /**
     * La comparacion de nombres necesita quitar acentos y colapsar espacios, y
     * eso no se puede hacer en SQL sin la extension unaccent, asi que se trae
     * la lista activa y se compara en memoria. Es una lista de decenas de
     * registros en un conjunto residencial; si creciera, lo correcto seria una
     * columna nombre_normalizado indexada y comparar en la consulta.
     */
    List<NoPermitido> findByActivoTrue();
}
