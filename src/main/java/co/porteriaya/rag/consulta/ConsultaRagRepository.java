package co.porteriaya.rag.consulta;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultaRagRepository extends JpaRepository<ConsultaRag, Long> {

    /** Alimenta el panel de «preguntas sin respuesta» de §13. */
    List<ConsultaRag> findBySinRespuestaOrderByCreadoEnDesc(boolean sinRespuesta, Pageable pagina);
}
