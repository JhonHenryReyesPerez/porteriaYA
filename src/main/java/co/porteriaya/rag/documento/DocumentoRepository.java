package co.porteriaya.rag.documento;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /** §11: si ya existe con el mismo hash, no se reindexa. */
    Optional<Documento> findByHashContenido(String hashContenido);

    Optional<Documento> findByTituloIgnoreCase(String titulo);

    List<Documento> findAllByOrderBySubidoEnDesc();
}
